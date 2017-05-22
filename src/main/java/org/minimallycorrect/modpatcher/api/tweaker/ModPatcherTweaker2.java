package org.minimallycorrect.modpatcher.api.tweaker;

import net.minecraft.launchwrapper.Launch;
import org.minimallycorrect.modpatcher.api.LaunchClassLoaderUtil;

import java.io.*;
import java.util.*;

public class ModPatcherTweaker2 extends ModPatcherTweaker {
	@SuppressWarnings("unchecked")
	public static void add() {
		((List<String>) Launch.blackboard.get("TweakClasses")).add(ModPatcherTweaker2.class.getName());
	}

	@Override
	public void acceptOptions(List<String> list, File file, File file1, String s) {
		LaunchClassLoaderUtil.removeClassLoaderExclusions("org.minimallycorrect.modpatcher");
	}

	@Override
	public String[] getLaunchArguments() {
		LaunchClassLoaderUtil.dumpTransformersIfEnabled();
		return super.getLaunchArguments();
	}
}
