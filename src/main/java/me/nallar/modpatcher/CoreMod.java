package me.nallar.modpatcher;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.*;

@IFMLLoadingPlugin.Name("ModPatcher")
@IFMLLoadingPlugin.SortingIndex(1001) // Magic value, after deobf transformer.
public class CoreMod implements IFMLLoadingPlugin {
	@Override
	public String[] getASMTransformerClass() {
		return new String[0];
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return ModPatcher.getSetupClass();
	}

	@Override
	public void injectData(Map<String, Object> data) {
		ModPatcher.modPatcherAsCoreModStartup();
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
