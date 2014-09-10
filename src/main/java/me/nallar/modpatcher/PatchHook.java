package me.nallar.modpatcher;

import javassist.ClassLoaderPool;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import me.nallar.modpatcher.mappings.MCPMappings;

import java.io.*;
import java.lang.reflect.*;

public enum PatchHook {
	;
	private static final Patcher preSrgPatcher;
	private static final Patcher postSrgPatcher;
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.PatchHook.alreadyLoaded";

	static {
		Log.info("PatchHook running under classloader " + PatchHook.class.getClassLoader().getClass().getName());
		boolean alreadyLoaded = System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null;
		if (alreadyLoaded) {
			Log.error("Detected multiple classloads of PatchHook - classloading issue?", new Throwable());
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
		try {
			Class<?> clazz = Class.forName("cpw.mods.fml.relauncher.ServerLaunchWrapper");
			try {
				Field field = clazz.getDeclaredField("startupArgs");
				field.setAccessible(true);
				field.set(null, PatchLauncher.startupArgs);
			} catch (NoSuchFieldException ignored) {
			}
		} catch (Throwable t) {
			Log.error("Failed to set up Cauldron startup args. This is only a problem if you are using Cauldron", t);
		}
		Patcher preSrgPatcher_ = null;
		Patcher postSrgPatcher_ = null;
		try {
			preSrgPatcher_ = new Patcher(Patches.class, new ClassLoaderPool(false), new MCPMappings(false));
			postSrgPatcher_ = new Patcher(Patches.class, new ClassLoaderPool(true), new MCPMappings(true));
		} catch (Throwable t) {
			Log.error("Failed to create Patcher", t);
			System.exit(1);
		}
		preSrgPatcher = preSrgPatcher_;
		postSrgPatcher = postSrgPatcher_;
		// TODO - issue #2. Determine layout/config file structure
		recursivelyAddXmlFiles(new File("./ModPatchesSrg/"), preSrgPatcher);
		recursivelyAddXmlFiles(new File("./ModPatches/"), postSrgPatcher);
	}

	private static void recursivelyAddXmlFiles(File directory, Patcher patcher) {
		if (!directory.isDirectory()) {
			return;
		}
		for (File f : directory.listFiles()) {
			if (f.isDirectory()) {
				recursivelyAddXmlFiles(f, patcher);
			} else if (f.getName().endsWith(".xml")) {
				patcher.readPatchesFromFile(f);
			}
		}
	}

	public static byte[] preSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return preSrgPatcher.transform(name, originalBytes);
		} catch (Throwable t) {
			Log.error("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	public static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return postSrgPatcher.transform(transformedName, originalBytes);
		} catch (Throwable t) {
			Log.error("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	public static boolean requiresSrgHook(String transformedName) {
		return postSrgPatcher.willTransform(transformedName);
	}
}
