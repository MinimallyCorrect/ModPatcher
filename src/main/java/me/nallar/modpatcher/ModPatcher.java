package me.nallar.modpatcher;

import me.nallar.javapatcher.patcher.Patcher;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;

/**
 * ModPatcher API
 *
 * This class is the public facing API of ModPatcher
 */
public class ModPatcher {
	private static final String modPatcherDownloadUrl = "https://modpatcher.nallar.me/ModPatcher-lib.jar";
	private static Path modPatcherPath = Paths.get("./libs/me/nallar/modpatcher/ModPatcher-lib.jar").toAbsolutePath();

	static {
		try {
			checkClassLoading();
		} catch (NoClassDefFoundError e) {
			loadModPatcher();
		}
	}

	private static void loadModPatcher() {
		downloadIfNeeded();

		addToCurrentClassLoader();

		checkClassLoading();
	}

	@SuppressWarnings("unchecked")
	private static void addToCurrentClassLoader() {
		ClassLoader cl = ModPatcher.class.getClassLoader();

		LaunchClassLoader lcl = null;
		if (cl instanceof LaunchClassLoader) {
			lcl = (LaunchClassLoader) cl;
			cl = ReflectionHelper.<ClassLoader, LaunchClassLoader>getPrivateValue(LaunchClassLoader.class, lcl, "parent");
			lcl.addClassLoaderExclusion("me.nallar.modpatcher");
		}

		try {
			Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
			method.setAccessible(true);
			method.invoke(cl, modPatcherPath.toUri().toURL());
		} catch (Exception e) {
			throw new Error(e);
		}

		ModPatcherLoadHook.loadedAfterDownload(lcl);
	}

	private static void downloadIfNeeded() {
		if (!Files.exists(modPatcherPath))
			download();
	}

	private static void download() {
		try (InputStream in = new URL(modPatcherDownloadUrl).openConnection().getInputStream()) {
			Files.copy(in, modPatcherPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void checkClassLoading() {
		if (ModPatcherLoadHook.class.getClassLoader().getClass().getName().contains("LaunchClassLoader")) {
			throw new Error("ModPatcher not be loaded under LaunchClassLoader");
		}
	}

	/**
	 * Ensures that ModPatcher is at least the given version. Triggers auto-updating if not.
	 *
	 * @param version minimum required version
	 */
	public static void ensureVersion(String version) {
		ModPatcherLoadHook.ensureVersion(version);
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
