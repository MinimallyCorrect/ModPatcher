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
	private static final List<String> DEOBF_TRANSFORMER_NAMES = Arrays.asList(
		DEOBF_TRANSFORMER_NAME,
		SPONGEPOWERED_MIXIN_TRANSFORMER_NAME
	);
	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("legacy.debugClassLoading", "false"));
	private static final boolean DEBUG_FINER = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingFiner", "false"));
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.alreadyLoaded";
	private static final String DUMP_TRANSFORMERS_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.dumpTransformers";
	private static final String WARN_INCONSISTENT_TRANSFORMATION_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.warnForInconsistentTransformation";
	private static final HashMap<String, byte[]> cachedSrgClasses = new HashMap<String, byte[]>();
	static LaunchClassLoader instance;

	private static List<IClassTransformer> transformers;
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

	private static byte[] transformUpToSrg(final String name, final String transformedName, byte[] basicClass) {
		Iterable<IClassTransformer> transformers = getTransformers();
		for (final IClassTransformer transformer : transformers) {
			if (transformer == ModPatcherTransformer.getInstance()) {
				cacheSrgBytes(transformedName, basicClass);
				return basicClass;
			}
			basicClass = runTransformer(name, transformedName, basicClass, transformer);
			if (Objects.equals(transformer.getClass().getName(), DEOBF_TRANSFORMER_NAME)) {
				cacheSrgBytes(transformedName, basicClass);
				return basicClass;
			}
		}
		throw new RuntimeException("No SRG transformer!" + Joiner.on(",\n").join(transformers));
	}

	public static byte[] getSrgBytes(String name) {
		final String transformedName = transformName(name);
		name = untransformName(name);
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null) {
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

	private static void removeRedundantExclusions(Set<String> transformerExceptions) {
		Iterator<String> parts = transformerExceptions.iterator();
		while (parts.hasNext()) {
			String part = parts.next();

			for (String part2 : new HashSet<>(transformerExceptions)) {
				if (!part.equals(part2) && part.startsWith(part2)) {
					parts.remove();
				}
			}
		}
	}
}
