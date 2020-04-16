package org.minimallycorrect.modpatcher.api.tweaker;

import java.io.*;
import java.util.*;

import org.minimallycorrect.modpatcher.api.LaunchClassLoaderUtil;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class ModPatcherTweaker implements ITweaker {
	@SuppressWarnings("unchecked")
	public static void add() {
		((List<String>) Launch.blackboard.get("TweakClasses")).add(ModPatcherTweaker.class.getName());
	}

	@Override
	public void acceptOptions(List<String> list, File file, File file1, String s) {
		LaunchClassLoaderUtil.removeRedundantExclusions();
		ModPatcherTweaker2.add();
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {}

	@Override
	public String getLaunchTarget() {
		throw new UnsupportedOperationException("ModPatcherTweaker is not a primary tweaker.");
	}

	@Override
	public String[] getLaunchArguments() {
		return new String[0];
	}
}
