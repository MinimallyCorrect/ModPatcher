package me.nallar.modpatcher;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLCallHook;

import java.util.*;

/**
 * Set as the setup class for your CoreMod to set up ModPatcher
 *
 * <pre><code>@Override public String getSetupClass() { return ModPatcher.getSetupClass(); }</code></pre>
 */
public class ModPatcherSetup implements IFMLCallHook {
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

		classLoader.addClassLoaderExclusion("me.nallar.modpatcher");
		classLoader.addClassLoaderExclusion("javassist");
		LaunchClassLoaderUtil.instance = classLoader;
		LaunchClassLoaderUtil.addTransformer(ModPatcher.getInstance());
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
