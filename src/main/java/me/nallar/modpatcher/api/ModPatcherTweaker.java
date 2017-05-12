package me.nallar.modpatcher.api;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Tries to ensure that our transformer is last
 */
class ModPatcherTweaker implements ITweaker {
	@SuppressWarnings("unchecked")
	public static void add() {
		((List<String>) Launch.blackboard.get("TweakClasses")).add(ModPatcherTweaker.class.getName());
	}

	private static void inject() {
		LaunchClassLoaderUtil.addTransformer(ModPatcherTransformer.getInstance());
		try {
			Class<?> mixinEnvironmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, ModPatcherTweaker.class.getClassLoader());
			Field f = mixinEnvironmentClass.getDeclaredField("excludeTransformers");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			Set<String> vals = (Set<String>) f.get(null);
			vals.add(ModPatcherTransformer.class.getName());
		} catch (ClassNotFoundException ignored) {
			// TODO Silence this once confirmed working?
			PatcherLog.trace("Failed to find mixin environment, normal for non-spongepowered", ignored);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			PatcherLog.warn("Failed to add mixin transformer exclusion for our transformer", e);
		}
	}

	@Override
	public void acceptOptions(List<String> list, File file, File file1, String s) {
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		inject();
	}

	@Override
	public String getLaunchTarget() {
		throw new UnsupportedOperationException("ModPatcherTweaker is not a primary tweaker.");
	}

	@Override
	public String[] getLaunchArguments() {
		inject();
		LaunchClassLoaderUtil.dumpTransformersIfEnabled();
		return new String[0];
	}
}
