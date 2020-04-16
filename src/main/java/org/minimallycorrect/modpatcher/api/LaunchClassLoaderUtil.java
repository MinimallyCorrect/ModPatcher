package org.minimallycorrect.modpatcher.api;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

public enum LaunchClassLoaderUtil {
	;
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.alreadyLoaded";
	private static final String DUMP_TRANSFORMERS_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.dumpTransformers";
	static LaunchClassLoader instance;

	private static List<IClassTransformer> transformers;
	private static Set<String> classLoaderExceptions;
	private static Set<String> transformerExceptions;

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

	public static void removeRedundantExclusions() {
		removeRedundantExclusions(getTransformerExceptions());
		removeRedundantExclusions(getClassLoaderExceptions());
		getClassLoaderExceptions().removeIf(it -> it.startsWith("org.minimallycorrect.modpatcher"));
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
}
