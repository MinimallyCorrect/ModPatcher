package org.minimallycorrect.modpatcher.api;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLCallHook;

import java.util.*;

/**
 * Set as the setup class for your CoreMod to set up ModPatcher
 * <p>
 * <pre><code>@Override public String getSetupClass() { return ModPatcher.getSetupClass(); }</code></pre>
 */
public class ModPatcherSetup implements IFMLCallHook {
	@Override
	public void injectData(Map<String, Object> data) {
		ModPatcherTransformer.initialiseClassLoader((LaunchClassLoader) ModPatcherSetup.class.getClassLoader());
	}

	@Override
	public Void call() throws Exception {
		return null;
	}
}
