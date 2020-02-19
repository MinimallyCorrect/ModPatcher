package org.minimallycorrect.modpatcher.api.tweaker;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.minimallycorrect.modpatcher.api.LaunchClassLoaderUtil;
import org.minimallycorrect.modpatcher.api.ModPatcherTransformer;
import org.minimallycorrect.modpatcher.api.PatcherLog;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class ModPatcherTweaker2 implements ITweaker {
	@SuppressWarnings("unchecked")
	public static void add() {
		((List<String>) Launch.blackboard.get("TweakClasses")).add(ModPatcherTweaker2.class.getName());
	}

	@SuppressWarnings("unchecked")
	private static void inject() {
		LaunchClassLoaderUtil.addTransformer(ModPatcherTransformer.getInstance());
		
		// Mixin 0.7
		try {
			Class<?> mixinEnvironmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, ModPatcherTweaker.class.getClassLoader());
			
			// org.spongepowered.asm.mixin.MixinEnvironment.excludeTransformers
			Field excludeTransformersField = mixinEnvironmentClass.getDeclaredField("excludeTransformers");
			excludeTransformersField.setAccessible(true);
			
			Set<String> values = (Set<String>) excludeTransformersField.get(null);
			values.add(ModPatcherTransformer.class.getName());
		} catch (ClassNotFoundException ex) {
			// TODO Silence this once confirmed working?
			PatcherLog.trace("Failed to find mixin environment, normal for non-spongepowered", ex);
			return;
		} catch (NoSuchFieldException ex) {
			// no-op
		} catch (Exception ex) {
			PatcherLog.warn("Failed to add mixin transformer exclusion for our transformer", ex);
			return;
		}
		
		// Mixin 0.8
		try {
			Class<?> mixinServiceLaunchWrapperClass = Class.forName("org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper", false, ModPatcherTweaker.class.getClassLoader());
			
			// org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper.excludeTransformers
			Field excludeTransformersField = mixinServiceLaunchWrapperClass.getDeclaredField("excludeTransformers");
			excludeTransformersField.setAccessible(true);
			
			Set<String> values = (Set<String>) excludeTransformersField.get(null);
			values.add(ModPatcherTransformer.class.getName());
		} catch (ClassNotFoundException ex) {
			// TODO Silence this once confirmed working?
			PatcherLog.trace("Failed to find mixin environment, normal for non-spongepowered", ex);
		} catch (Exception ex) {
			PatcherLog.warn("Failed to add mixin transformer exclusion for our transformer", ex);
		}
	}

	@Override
	public void acceptOptions(List<String> list, File file, File file1, String s) {
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader classLoader) {
	}

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
