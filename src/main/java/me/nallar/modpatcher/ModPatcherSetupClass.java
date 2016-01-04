package me.nallar.modpatcher;

import cpw.mods.fml.relauncher.IFMLCallHook;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

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
		List<IClassTransformer> transformers = LaunchClassLoaderUtil.getTransformers();
		boolean foundDeobfuscationTransformer = false;
		int i = 0;
		while (i < transformers.size()) {
			if (transformers.get(i).getClass().getName().equals(LaunchClassLoaderUtil.AFTER_TRANSFORMER_NAME)) {
				foundDeobfuscationTransformer = true;
				break;
			}
			i++;
		}
		i++;
		if (!foundDeobfuscationTransformer) {
			PatcherLog.warn("Didn't find deobfuscation transformer " + LaunchClassLoaderUtil.AFTER_TRANSFORMER_NAME + " in transformers list.\n" +
				"Did you forget to set the SortingIndex for your coremod >= 1001? This message is expected in a deobf environment.");
			modPatcherInitialised = false;
			return;
		}
		transformers.add(i > transformers.size() ? transformers.size() : i, new ModPatcher());
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
