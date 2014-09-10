package me.nallar.modpatcher;

import me.nallar.javapatcher.Log;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import me.nallar.modpatcher.mappings.MCPMappings;

import java.lang.reflect.*;

public enum PatchHook {;
	private static final Patcher preSrgPatcher;
	private static final Patcher postSrgPatcher;

	static {
		Log.info("PatchHook running under classloader " + PatchHook.class.getClassLoader().getClass().getName());
		try {
			Class<?> clazz = Class.forName("cpw.mods.fml.relauncher.ServerLaunchWrapper");
			try {
				Field field = clazz.getDeclaredField("startupArgs");
				field.set(null, PatchLauncher.startupArgs);
			} catch (NoSuchFieldException ignored) {
			}
		} catch (Throwable t) {
			Log.severe("Failed to set up Cauldron startup args. This is only a problem if you are using Cauldron", t);
		}
		Patcher preSrgPatcher_ = null;
		Patcher postSrgPatcher_ = null;
		try {
			preSrgPatcher_ = new Patcher(PatchLauncher.class.getResourceAsStream("/patches.xml"), Patches.class, new ClassLoaderPool(false), new MCPMappings(false));
			postSrgPatcher_ = new Patcher(PatchLauncher.class.getResourceAsStream("/patches.xml"), Patches.class, new ClassLoaderPool(true), new MCPMappings(true));
		} catch (Throwable t) {
			Log.severe("Failed to create Patcher", t);
			System.exit(1);
		}
		preSrgPatcher = preSrgPatcher_;
		postSrgPatcher = postSrgPatcher_;
	}

	public static byte[] preSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return preSrgPatcher.transform(name, originalBytes);
		} catch (Throwable t) {
			Log.severe("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	public static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return postSrgPatcher.transform(transformedName, originalBytes);
		} catch (Throwable t) {
			Log.severe("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}
}
