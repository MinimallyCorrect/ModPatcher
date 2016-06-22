package me.nallar.modpatcher;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
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
		return null;
	}

	@Override
	public String[] getLaunchArguments() {
		inject();
		return new String[0];
	}
}
