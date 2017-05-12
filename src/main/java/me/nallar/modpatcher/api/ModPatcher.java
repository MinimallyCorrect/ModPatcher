package me.nallar.modpatcher.api;

import me.nallar.javapatcher.patcher.Patcher;

import java.io.*;
import java.nio.file.*;

/**
 * ModPatcher API
 * <p>
 * This class is the public facing API of ModPatcher
 */
public class ModPatcher {
	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass() {
		return "me.nallar.modpatcher.api.ModPatcherSetup";
	}

	/**
	 * Load JavaPatcher patches
	 *
	 * @param inputStream stream to load patches from
	 */
	public static void loadPatches(InputStream inputStream) {
		ModPatcherTransformer.getPatcher().loadPatches(inputStream);
	}

	/**
	 * Load JavaPatcher patches
	 *
	 * @param patches String to load patches from
	 */
	public static void loadPatches(String patches) {
		ModPatcherTransformer.getPatcher().loadPatches(patches);
	}

	/**
	 * Gets the JavaPatcher Patcher instance
	 *
	 * @return the Patcher
	 * @deprecated Use specific methods such as loadPatches(InputStream)
	 */
	@Deprecated
	public static Patcher getPatcher() {
		return ModPatcherTransformer.getPatcher();
	}

	/**
	 * Loads mixins from the given package.
	 * The package must have a package-info.java with @Mixin annotation
	 *
	 * @param mixinPackage Package to load mixins from
	 */
	public static void loadMixins(String mixinPackage) {
		ModPatcherTransformer.getMixinApplicator().addSource(mixinPackage);
	}

	/**
	 * Loads all mixins from the given path, regardless of package
	 *
	 * @param path Path to load mixins from
	 */
	public static void loadMixins(Path path) {
		ModPatcherTransformer.getMixinApplicator().addSource(path);
	}

	/**
	 * Loads all mixins from the given path, if they match the given package
	 *
	 * @param path Path to load mixins from
	 */
	public static void loadMixins(Path path, String packageName) {
		ModPatcherTransformer.getMixinApplicator().addSource(path, packageName);
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
