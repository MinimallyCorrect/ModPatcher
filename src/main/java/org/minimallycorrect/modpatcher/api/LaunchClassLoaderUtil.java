package org.minimallycorrect.modpatcher.api;

import LZMA.LzmaInputStream;
import lombok.SneakyThrows;
import lombok.val;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;

public enum LaunchClassLoaderUtil {
	;
	private static final boolean DUMP_JAVASSIST_LOADED_CLASSES = Boolean.parseBoolean(System.getProperty("nallar.LaunchClassLoaderUtil.dumpJavassistLoadedClasses", "false"));
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.alreadyLoaded";
	private static final String DUMP_TRANSFORMERS_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.dumpTransformers";
	private static final String WARN_INCONSISTENT_TRANSFORMATION_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.warnForInconsistentTransformation";
	private static final HashMap<String, byte[]> cachedSrgClasses = new HashMap<>();
	static LaunchClassLoader instance;

	private static List<IClassTransformer> transformers;
	private static IClassNameTransformer renameTransformer;
	private static Set<String> classLoaderExceptions;
	private static Set<String> transformerExceptions;
	private static boolean warnedForInconsistentTransformation;
	private static FileSystem stubs;

	static {
		boolean alreadyLoaded = System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null;
		if (alreadyLoaded) {
			PatcherLog.error("Detected multiple classloads of LaunchClassLoaderUtil - classloading issue?", new Throwable());
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
	}

	public static void addTransformer(IClassTransformer transformer) {
		List<IClassTransformer> transformers = getTransformers();
		transformers.remove(transformer);
		transformers.add(transformer);
	}

	public static void dumpTransformersIfEnabled() {
		if (!"false".equalsIgnoreCase(System.getProperty(DUMP_TRANSFORMERS_PROPERTY_NAME)))
			PatcherLog.info("Transformers: " + transformers.toString());
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

	public static boolean allowedForSrg(String name) {
		return !(name.startsWith("java.") || name.startsWith("javax."));
	}

	private static byte[] transformUpToSrg(final String name, final String transformedName, byte[] basicClass) {
		if (basicClass == null)
			return null;

		val renameTransformer = getRenameTransformer();
		if (renameTransformer == null)
			throw new RuntimeException("Tried to call transformUpToSrg too early - haven't built SRG transformer list yet");

		return ((IClassTransformer) renameTransformer).transform(name, transformedName, basicClass);
	}

	private static String classNameToResourceName(String name) {
		return name.replace('.', '/') + ".class";
	}

	@SneakyThrows
	private static byte[] getStubSrgBytes(String name) {
		if (stubs == null) {
			Path extracted = Paths.get("./libraries/minecraft_stubs.jar");
			if (!Files.exists(extracted)) {
				if (!Files.isDirectory(extracted.getParent())) {
					Files.createDirectory(extracted.getParent());
				}
				Files.copy(new LzmaInputStream(LaunchClassLoaderUtil.class.getResourceAsStream("/minecraft_stubs.jar.lzma")), extracted);
			}
			stubs = FileSystems.newFileSystem(extracted, null);
		}

		try {
			return Files.readAllBytes(stubs.getPath(classNameToResourceName(name)));
		} catch (NoSuchFileException ignored) {
			return null;
		}
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
		val stubBytes = getStubSrgBytes(transformedName);
		if (stubBytes != null) {
			return stubBytes;
		}
		try {
			byte[] bytes = getClassBytes(name);

			if (name.equals(transformedName))
				return bytes;

			return transformUpToSrg(name, transformedName, bytes);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public static void cacheSrgBytes(String transformedName, byte[] bytes) {
		if (!allowedForSrg(transformedName)) {
			return;
		}

		byte[] old = cachedSrgClasses.put(transformedName, bytes);
		if (old != null && !Arrays.equals(bytes, old)) {
			ModPatcherTransformer.pool.dropCache(transformedName, false);
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
		ModPatcherTransformer.pool.dropCache(transformedName, true);
	}

	public static void removeClassLoaderExclusions(String name) {
		classLoaderExceptions.removeIf(it -> it.startsWith(name));
	}
}
