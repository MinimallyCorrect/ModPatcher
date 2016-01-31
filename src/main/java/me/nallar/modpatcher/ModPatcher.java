package me.nallar.modpatcher;

import me.nallar.javapatcher.patcher.Patcher;

import java.nio.file.*;

/**
 *
 */
public class ModPatcher {
	static {
		if (ModPatcherTransformer.class.getClassLoader().getClass().getName().contains("LaunchClassLoader")) {
			throw new Error("ModPatcher not be loaded under LaunchClassLoader");
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

	/**
	 * Loads mixins from the package which the given class is in
	 *
	 * @param mixinClass Class to load mixins from package of
	 */
	public static void loadMixins(Class<?> mixinClass) {
		ModPatcherTransformer.loadMixins(mixinClass);
	}

	/**
	 * Loads all mixins from the given path, regardless of package
	 *
	 * @param path Path to load mixins from
	 */
	public static void loadMixins(Path path) {
		ModPatcherTransformer.loadMixins(path);
	}

	/**
	 * Loads all mixins from the given path, if they match the given package
	 *
	 * @param path Path to load mixins from
	 */
	public static void loadMixins(Path path, String packageName) {
		ModPatcherTransformer.loadMixins(path, packageName);
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
	 * Gets the default patches directory. Any patches in this directory are loaded by ModPatcher on startup.
	 *
	 * @return default patches directory
	 */
	public static String getDefaultPatchesDirectory() {
		return ModPatcherTransformer.getDefaultPatchesDirectory();
	}
}
