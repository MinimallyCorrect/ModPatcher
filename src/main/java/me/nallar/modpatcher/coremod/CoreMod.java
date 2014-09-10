package me.nallar.modpatcher.coremod;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.*;

@IFMLLoadingPlugin.Name("ModPatcher")
public class CoreMod implements IFMLLoadingPlugin {
	@Override
	public String[] getASMTransformerClass() {
		return new String[]{"me.nallar.modpatcher.ModPatcher"};
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return "me.nallar.modpatcher.ModPatcherSetupClass";
	}

	@Override
	public void injectData(Map<String, Object> data) {

	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
