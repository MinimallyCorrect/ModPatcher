package me.nallar.modpatcher.coremod;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import me.nallar.modpatcher.ModPatcher;

import java.util.*;

@IFMLLoadingPlugin.Name("ModPatcher")
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

	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
