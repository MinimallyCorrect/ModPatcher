package me.nallar.modpatcher;

import me.nallar.javatransformer.api.JavaTransformer;

class ModPatcherLoadHook {
	private static final int API_VERSION = 1; //Keep in sync with version in ModPatcher.java
	private static final String VERSION = "@MOD_VERSION@";

	static void loadHook(ModPatcher.Version requiredVersion, String modPatcherRelease, int apiVersion) {
		PatcherLog.info("Loaded ModPatcher. Version: @MOD_VERSION@ API version: " + API_VERSION);

		if (API_VERSION != apiVersion) {
			PatcherLog.warn("API version mismatch. Expected " + API_VERSION + ", got " + apiVersion);
			PatcherLog.warn("API was loaded from: " + JavaTransformer.pathFromClass(ModPatcher.class));
		}

		ModPatcher.Version current = ModPatcher.Version.of(VERSION);

		if (isOutdated(current, requiredVersion)) {
			String autoUpdate = "\nWill auto-update on next start.";

			if (ModPatcher.neverUpdate())
				autoUpdate = "";
			else
				JavaTransformer.pathFromClass(ModPatcherTransformer.class).toFile().deleteOnExit();

			throw new RuntimeException("ModPatcher outdated. Have version: " + VERSION + ", requested version: " + requiredVersion + autoUpdate);
		}
	}

	static boolean isOutdated(ModPatcher.Version current, ModPatcher.Version required) {
		return required != ModPatcher.Version.LATEST && current.compareTo(required) < 0;
	}
}
