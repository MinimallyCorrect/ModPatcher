package me.nallar.modpatcher;

import com.google.common.base.Joiner;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public enum LaunchClassLoaderUtil {
	;
	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("legacy.debugClassLoading", "false"));
	private static final boolean DEBUG_FINER = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingFiner", "false"));
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.LaunchClassLoaderUtil.alreadyLoaded";
	public static final String AFTER_TRANSFORMER_NAME = "cpw.mods.fml.common.asm.transformers.DeobfuscationTransformer";

	static {
		boolean alreadyLoaded = System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null;
		if (alreadyLoaded) {
			PatcherLog.error("Detected multiple classloads of LaunchClassLoaderUtil - classloading issue?", new Throwable());
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
	}

	static LaunchClassLoader instance;

	private static List<IClassTransformer> transformers;

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

	private static IClassNameTransformer renameTransformer;

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

	private static Set<String> classLoaderExceptions;

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

	public static boolean excluded(String name) {
		for (final String exception : getClassLoaderExceptions()) {
			if (name.startsWith(exception)) {
				return true;
			}
		}
		return false;
	}

	private static final HashMap<String, byte[]> cachedSrgClasses = new HashMap<String, byte[]>();

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
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null) {
			return cached;
		}
		Iterable<IClassTransformer> transformers = getTransformers();
		for (final IClassTransformer transformer : transformers) {
			basicClass = runTransformer(name, transformedName, basicClass, transformer);
			if (transformer.getClass().getName().equals(AFTER_TRANSFORMER_NAME)) {
				cachedSrgClasses.put(transformedName, basicClass);
				return basicClass;
			}
		}
		throw new RuntimeException("No SRG transformer!" + Joiner.on(",\n").join(transformers));
	}

	public static byte[] getPreSrgBytes(String name) {
		name = untransformName(name);
		try {
			return getClassBytes(name);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
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
}
