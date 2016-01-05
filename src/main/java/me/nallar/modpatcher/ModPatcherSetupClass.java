package me.nallar.modpatcher;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLCallHook;

import java.util.*;

/**
 * Return "me.nallar.modpatcher.ModPatcherSetupClass" in your IFMLLoadingPlugin's getSetupClass
 * if you are using ModPatcher in your own core mod
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
		LaunchClassLoaderUtil.addTransformer(new ModPatcher());
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
