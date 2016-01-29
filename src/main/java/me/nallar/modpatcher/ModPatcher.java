package me.nallar.modpatcher;

import me.nallar.javapatcher.patcher.Patcher;

/**
 *
 */
public class ModPatcher {
	static {
		if (ModPatcherTransformer.class.getClassLoader().getClass().getName().contains("LaunchClassLoader")) {
			throw new Error("ModPatcher must be loaded in the system classloader, not: " + ModPatcherTransformer.class.getClassLoader());
		}
	}

	/**
	 * Gets the JavaPatcher Patcher instance
	 *
	 * @return the Patcher
	 */
	public static Patcher getPatcher() {
		return ModPatcherTransformer.getPatcher();
	}

	public static void loadMixin(Class<?> mixinClass) {

	}

	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass() {
		return "me.nallar.modpatcher.ModPatcherSetup";
	}

	/**
	 * Gets the directory
	 *
	 * @return
	 */
	public static String getDefaultPatchesDirectory() {
		return ModPatcherTransformer.getDefaultPatchesDirectory();
	}
}
