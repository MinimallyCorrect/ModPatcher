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
		int i = 0;
		while (i < transformers.size()) {
			if (transformers.get(i).getClass().getName().equals(LaunchClassLoaderUtil.AFTER_TRANSFORMER_NAME)) {
				break;
			}
			i++;
		}
		i++;
		transformers.add(i > transformers.size() ? transformers.size() : i, new ModPatcher());
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
