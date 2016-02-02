package me.nallar.modpatcher;

import me.nallar.javatransformer.api.JavaTransformer;

class ModPatcherLoadHook {
	private static final String VERSION = "@VERSION@".replace("-SNAPSHOT", "");

	static void loadHook(ModPatcher.Version requiredVersion, String modPatcherRelease) {
		if (ModPatcherLoadHook.class.getClassLoader().getClass().getName().contains("LaunchClassLoader")) {
			throw new Error("ModPatcher should not be loaded under LaunchClassLoader");
		}

		ModPatcher.Version current = ModPatcher.Version.of(VERSION);

		if (current.compareTo(requiredVersion) < 0) {
			JavaTransformer.pathFromClass(ModPatcherTransformer.class).toFile().deleteOnExit();
			throw new RuntimeException("ModPatcher outdated. Have version: " + VERSION + ", requested version: " + requiredVersion + "\nWill auto-update on next start.");
		}
	}
}
