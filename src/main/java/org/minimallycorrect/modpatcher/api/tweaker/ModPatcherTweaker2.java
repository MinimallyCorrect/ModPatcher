package org.minimallycorrect.modpatcher.api.tweaker;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.minimallycorrect.modpatcher.api.LaunchClassLoaderUtil;
import org.minimallycorrect.modpatcher.api.ModPatcherTransformer;
import org.minimallycorrect.modpatcher.api.PatcherLog;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class ModPatcherTweaker2 implements ITweaker {
	@SuppressWarnings("unchecked")
	public static void add() {
		((List<String>) Launch.blackboard.get("TweakClasses")).add(ModPatcherTweaker2.class.getName());
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
	public void acceptOptions(List<String> list, File file, File file1, String s) {}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader classLoader) {}

	@Override
	public String getLaunchTarget() {
		throw new UnsupportedOperationException("ModPatcherTweaker is not a primary tweaker.");
	}

	@Override
	public String[] getLaunchArguments() {
		inject();
		LaunchClassLoaderUtil.dumpTransformersIfEnabled();
		LaunchClassLoaderUtil.removeRedundantExclusions();
		return new String[0];
	}
}
