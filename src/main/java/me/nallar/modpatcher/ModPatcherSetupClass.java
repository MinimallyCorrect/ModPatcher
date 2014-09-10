package me.nallar.modpatcher;

import cpw.mods.fml.relauncher.IFMLCallHook;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.util.*;

/**
 * Return "me.nallar.modpatcher.ModPatcherSetupClass" in your IFMLLoadingPlugin's getSetupClass
 * if you are using CoreMod as a library
 */
public class ModPatcherSetupClass implements IFMLCallHook {
	private static boolean modPatcherInitialised = false;

	@Override
	public void injectData(Map<String, Object> data) {
		initialised((LaunchClassLoader) data.get("classLoader"));
	}

	private void initialised(LaunchClassLoader classLoader) {
		if (modPatcherInitialised) {
			return;
		}
		modPatcherInitialised = true;
		LaunchClassLoaderUtil.instance = classLoader;
		System.out.println(classLoader);
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
