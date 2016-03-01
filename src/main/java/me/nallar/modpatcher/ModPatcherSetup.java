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
	@Override
	public void injectData(Map<String, Object> data) {
		ModPatcher.initialiseClassLoader((LaunchClassLoader) data.get("classLoader"));
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
