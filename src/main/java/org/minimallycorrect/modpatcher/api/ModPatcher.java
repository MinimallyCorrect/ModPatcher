package org.minimallycorrect.modpatcher.api;

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
		return "org.minimallycorrect.modpatcher.api.ModPatcherSetup";
	}

	/**
	 * Loads mixins from the given package. The package must have a package-info.java with @Mixin annotation
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
}
