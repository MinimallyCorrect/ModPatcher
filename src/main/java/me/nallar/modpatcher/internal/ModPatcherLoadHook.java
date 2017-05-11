package me.nallar.modpatcher.internal;

import me.nallar.javatransformer.api.JavaTransformer;
import me.nallar.modpatcher.api.ModPatcher;

public class ModPatcherLoadHook {
	private static final int API_VERSION = 2; //Keep in sync with version in ModPatcher.java

	public static void loadHook(int apiVersion) {
		PatcherLog.info("Loaded ModPatcher. Version: @MOD_VERSION@ API version: " + API_VERSION);

		if (API_VERSION != apiVersion) {
			PatcherLog.warn("API version mismatch. Expected " + API_VERSION + ", got " + apiVersion);
			PatcherLog.warn("API was loaded from: " + JavaTransformer.pathFromClass(ModPatcher.class));
			PatcherLog.warn("Updating the mod which contains the outdated ModPatcher API should fix this warning");
		}

		ModPatcher.Version current = ModPatcher.Version.of("@MOD_VERSION@");
	}
}
