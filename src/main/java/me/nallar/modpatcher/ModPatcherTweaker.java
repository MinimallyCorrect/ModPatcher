package me.nallar.modpatcher;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Tries to ensure that our transformer is last
 */
public class ModPatcherTweaker implements ITweaker {
	@SuppressWarnings("unchecked")
	public static void add() {
		List<String> newTweaks = (List<String>) Launch.blackboard.get("TweakClasses");
		newTweaks.add(ModPatcherTweaker.class.getName());
	}

	private static void inject() {
		LaunchClassLoaderUtil.addTransformer(ModPatcherTransformer.getInstance());
		try {
			Class<?> mixinEnvironmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, ModPatcherTweaker.class.getClassLoader());
			Field f = mixinEnvironmentClass.getDeclaredField("excludeTransformers");
			f.setAccessible(true);
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
		inject();
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		inject();
	}

	@Override
	public String getLaunchTarget() {
		inject();
		LaunchClassLoaderUtil.dumpTransformersIfEnabled();
		return null;
	}

	@Override
	public String[] getLaunchArguments() {
		inject();
		return new String[0];
	}
}
