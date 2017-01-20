package me.nallar.modpatcher;

import com.google.common.base.Joiner;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.FMLRelaunchLog;
import org.apache.logging.log4j.Level;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public enum LaunchClassLoaderUtil {
	;
	private static final String SPONGEPOWERED_MIXIN_TRANSFORMER_NAME = "org.spongepowered.asm.mixin.transformer.MixinTransformer$Proxy";
	private static final String DEOBF_TRANSFORMER_NAME = "net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer";
	private static final boolean TEMPORARY_ALLOW_PATCHING_ALL_CLASSES = Boolean.parseBoolean(System.getProperty("nallar.LaunchClassLoaderUtil.allowPatchingAllClasses", "false"));
	private static final boolean DUMP_JAVASSIST_LOADED_CLASSES = Boolean.parseBoolean(System.getProperty("nallar.LaunchClassLoaderUtil.dumpJavassistLoadedClasses", "false"));
	private static final List<String> DEOBF_TRANSFORMER_NAMES = Arrays.asList(
		DEOBF_TRANSFORMER_NAME,
		SPONGEPOWERED_MIXIN_TRANSFORMER_NAME
	);
	private static final List<String> WHITELISTED_TRANSFORMERS = Arrays.asList(
		"net.minecraftforge.fml.common.asm.transformers.PatchingTransformer",
		"net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer"
	);
	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("legacy.debugClassLoading", "false"));
	private static final boolean DEBUG_FINER = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingFiner", "false"));
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.alreadyLoaded";
	private static final String DUMP_TRANSFORMERS_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.dumpTransformers";
	private static final String WARN_INCONSISTENT_TRANSFORMATION_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.warnForInconsistentTransformation";
	private static final HashMap<String, byte[]> cachedSrgClasses = new HashMap<>();
	static LaunchClassLoader instance;

	private static List<IClassTransformer> transformers;
	private static IClassTransformer[] srgTransformers;
	private static IClassNameTransformer renameTransformer;
	private static Set<String> classLoaderExceptions;
	private static Set<String> transformerExceptions;
	private static boolean warnedForInconsistentTransformation;

	static {
		boolean alreadyLoaded = System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null;
		if (alreadyLoaded) {
			PatcherLog.error("Detected multiple classloads of LaunchClassLoaderUtil - classloading issue?", new Throwable());
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
	}

	public static void addTransformer(IClassTransformer transformer) {
		List<IClassTransformer> transformers = LaunchClassLoaderUtil.getTransformers();

		int target = -1;
		for (int i = 0; i < transformers.size(); i++) {
			IClassTransformer current = transformers.get(i);

			if (current == transformer)
				transformers.remove(i--);

			String className = current.getClass().getName();
			if (DEOBF_TRANSFORMER_NAMES.contains(className))
				target = i;
		}

		if (target == -1) {
			PatcherLog.warn("Didn't find deobfuscation transformers " + DEOBF_TRANSFORMER_NAMES.toString() + " in transformers list.\n" +
				"Did you forget to set the SortingIndex for your coremod >= 1001? This message is expected in a deobf environment.");
			transformers.add(transformer);
		} else {
			transformers.add(target + 1, transformer);
		}

		buildSrgTransformList();
	}

	public static void dumpTransformersIfEnabled() {
		if (System.getProperty(DUMP_TRANSFORMERS_PROPERTY_NAME) != null)
			PatcherLog.info("Transformers: " + transformers.toString(), new Throwable());
	}

	@SuppressWarnings("unchecked")
	public static List<IClassTransformer> getTransformers() {
		if (transformers != null) {
			return transformers;
		}
		try {
			Field f = instance.getClass().getDeclaredField("transformers");
			f.setAccessible(true);
			return transformers = (List<IClassTransformer>) f.get(getInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static LaunchClassLoader getInstance() {
		if (instance != null) {
			return instance;
		}
		throw new Error("Tried to retrieve LaunchClassLoader instance before setting up the transformer");
	}

	private static IClassNameTransformer getRenameTransformer() {
		if (renameTransformer != null) {
			return renameTransformer;
		}
		try {
			Field f = instance.getClass().getDeclaredField("renameTransformer");
			f.setAccessible(true);
			return renameTransformer = (IClassNameTransformer) f.get(getInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<String> getClassLoaderExceptions() {
		if (classLoaderExceptions != null) {
			return classLoaderExceptions;
		}
		try {
			Field f = instance.getClass().getDeclaredField("classLoaderExceptions");
			f.setAccessible(true);
			return classLoaderExceptions = (Set<String>) f.get(getInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<String> getTransformerExceptions() {
		if (transformerExceptions != null) {
			return transformerExceptions;
		}
		try {
			Field f = instance.getClass().getDeclaredField("transformerExceptions");
			f.setAccessible(true);
			return transformerExceptions = (Set<String>) f.get(getInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean excluded(String name) {
		for (final String exception : getClassLoaderExceptions()) {
			if (name.startsWith(exception)) {
				return true;
			}
		}
		return false;
	}

	public static boolean allowedForSrg(String name) {
		if (name.startsWith("javax.") || name.startsWith("java."))
			return false;

		if (TEMPORARY_ALLOW_PATCHING_ALL_CLASSES || name.startsWith("net.minecraft") || name.startsWith("nallar.") || name.startsWith("me.nallar."))
			return true;

		// TODO: Extensibility, need to add to API for patcher use?

		return false;
	}

	@SuppressWarnings("ConstantConditions")
	private static byte[] runTransformer(final String name, final String transformedName, byte[] basicClass, final IClassTransformer transformer) {
		try {
			return transformer.transform(name, transformedName, basicClass);
		} catch (Throwable t) {
			String message = t.getMessage();
			if (message != null && message.contains("for invalid side")) {
				if (t instanceof RuntimeException) {
					throw (RuntimeException) t;
				} else if (t instanceof Error) {
					throw (Error) t;
				} else {
					throw new RuntimeException(t);
				}
			} else if (basicClass != null || DEBUG_FINER) {
				FMLRelaunchLog.log((DEBUG_FINER && basicClass != null) ? Level.WARN : Level.TRACE, t, "Failed to transform " + name);
			}
			return basicClass;
		}
	}

	public static void buildSrgTransformList() {
		List<IClassTransformer> result = new ArrayList<>();

		Iterable<IClassTransformer> transformers = getTransformers();
		for (final IClassTransformer transformer : transformers) {
			if (transformer == ModPatcherTransformer.getInstance()) {
				srgTransformers = result.toArray(new IClassTransformer[0]);
				return;
			}

			if (whitelisted(transformer))
				result.add(transformer);

			if (Objects.equals(transformer.getClass().getName(), DEOBF_TRANSFORMER_NAME)) {
				srgTransformers = result.toArray(new IClassTransformer[0]);
				return;
			}
		}

		throw new RuntimeException("No SRG or ModPatcher transformer found when building SRG transformer list. " + Joiner.on(",\n").join(transformers));
	}

	private static boolean whitelisted(IClassTransformer transformer) {
		for (String whitelistEntry : WHITELISTED_TRANSFORMERS)
			if (transformer.getClass().getName().startsWith(whitelistEntry))
				return true;

		return false;
	}

	private static byte[] transformUpToSrg(final String name, final String transformedName, byte[] basicClass) {
		if (srgTransformers == null)
			throw new RuntimeException("Tried to call transformUpToSrg too early - haven't build SRG transformer list yet");

		for (final IClassTransformer transformer : srgTransformers) {
			basicClass = runTransformer(name, transformedName, basicClass, transformer);
		}
		return basicClass;
	}

	public static byte[] getSrgBytes(String name, boolean allowRetransform) {
		final String transformedName = transformName(name);
		name = untransformName(name);

		if (!allowedForSrg(transformedName)) {
			return null;
		}

		if (DUMP_JAVASSIST_LOADED_CLASSES) {
			PatcherLog.warn("Need to retransform " + transformedName + " to get SRG bytes", new Throwable());
		}
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null || !allowRetransform) {
			return cached;
		}
		try {
			byte[] bytes = getClassBytes(name);
			return transformUpToSrg(name, transformedName, bytes);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public static void cacheSrgBytes(String transformedName, byte[] bytes) {
		// TODO: Cache for this (and the javassist classpool too?) should be wiped out after worlds load?

		if (!allowedForSrg(transformedName)) {
			return;
		}

		byte[] old = cachedSrgClasses.put(transformedName, bytes);
		if (old != null && !Arrays.equals(bytes, old)) {
			ModPatcherTransformer.pool.dropCache(transformedName);
			if (shouldWarnInconsistentTransformation())
				PatcherLog.warn(null, new Error("Inconsistent transformation results. Tried to cache different bytes for class " + transformedName + " to previous result after transformation."));
		}
	}

	private static boolean shouldWarnInconsistentTransformation() {
		if (System.getProperty(WARN_INCONSISTENT_TRANSFORMATION_PROPERTY_NAME) != null)
			return true;

		if (!warnedForInconsistentTransformation) {
			// non-threadsafe but it really doesn't matter if this shows up twice so I don't care
			warnedForInconsistentTransformation = true;

			PatcherLog.warn("One or more classes have inconsistent transformation results. To enable logging of this," +
				" add -D" + WARN_INCONSISTENT_TRANSFORMATION_PROPERTY_NAME + "=true to your JVM parameters.");
		}

		return false;
	}

	public static byte[] getClassBytes(String name) {
		if (name.startsWith("java/")) {
			return null;
		}
		try {
			return getInstance().getClassBytes(name);
		} catch (IOException e) {
			return null;
		}
	}

	public static String transformName(String name) {
		return getRenameTransformer().remapClassName(name);
	}

	public static String untransformName(String name) {
		return getRenameTransformer().unmapClassName(name);
	}

	public static void removeRedundantExclusions() {
		removeRedundantExclusions(getTransformerExceptions());
		removeRedundantExclusions(getClassLoaderExceptions());
	}

	static void removeRedundantExclusions(Set<String> transformerExceptions) {
		HashSet<String> old = new HashSet<>(transformerExceptions);

		for (String exclusion : old) {
			for (String exclusion2 : old) {
				if (exclusion.equals(exclusion2))
					continue;

				if (exclusion.startsWith(exclusion2))
					transformerExceptions.remove(exclusion);
			}
		}
	}

	public static void releaseSrgBytes(String transformedName) {
		cachedSrgClasses.remove(transformedName);
		ModPatcherTransformer.pool.dropCache(transformedName);
	}
}
