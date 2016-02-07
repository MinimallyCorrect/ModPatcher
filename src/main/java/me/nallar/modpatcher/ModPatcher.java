package me.nallar.modpatcher;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import me.nallar.javapatcher.patcher.Patcher;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * ModPatcher API
 *
 * This class is the public facing API of ModPatcher
 *
 * It automatically downloads ModPatcher if the file is missing from the libs folder, or a coremod depends on
 * a newer version of modpatcher than the installed version
 *
 * This behaviour can be disabled by creating the file "libs/modpatcher/NEVER_UPDATE.txt"
 */
public class ModPatcher {
	private static final int API_VERSION = 0;
	private static final Logger log = LogManager.getLogger("ModPatcher");
	private static final String mcVersion = "@MC_VERSION@";
	private static final Path neverUpdatePath = Paths.get("./libs/ModPatcher/NEVER_UPDATE.txt").toAbsolutePath();
	private static final Path modPatcherPath = Paths.get("./libs/ModPatcher/ModPatcher-lib.jar").toAbsolutePath();
	private static final Future<Boolean> defaultUpdateRequired = CompletableFuture.completedFuture(Files.exists(modPatcherPath));
	private static String modPatcherRelease;
	private static Future<Boolean> updateRequired = defaultUpdateRequired;
	private static Version requiredVersion;
	private static Version lastVersion;

	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass() {
		return getSetupClass(null);
	}

	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @param versionString Minimum version of ModPatcher required. Special value "latest" always uses latest version
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass(String versionString) {
		return getSetupClass(versionString, null);
	}

	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @param versionString Minimum version of ModPatcher required. Special value "latest" always uses latest version
	 * @param release       Release stream to use
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass(String versionString, String release) {
		if (versionString != null || release != null) {
			if (updateRequired == null) {
				throw new Error("Modpatcher has already been loaded, it is too late to call getSetupClass");
			}
			if (release != null) {
				if (modPatcherRelease == null) {
					modPatcherRelease = release;
					startVersionCheck();
				} else {
					log.warn("Conflicting ModPatcher release requests. Set to " + modPatcherRelease + ", requested: " + release);
				}
			}
			if (versionString != null) {
				Version requested = Version.of(versionString);
				if (requested.compareTo(requiredVersion) > 0) {
					requiredVersion = requested;
					startVersionCheck();
				}
			}
		}

		return "me.nallar.modpatcher.ModPatcherSetup";
	}

	/**
	 * Load JavaPatcher patches
	 *
	 * @param inputStream stream to load patches from
	 */
	public static void loadPatches(InputStream inputStream) {
		getPatcher().loadPatches(inputStream);
	}

	/**
	 * Load JavaPatcher patches
	 *
	 * @param patches String to load patches from
	 */
	public static void loadPatches(String patches) {
		getPatcher().loadPatches(patches);
	}

	/**
	 * Gets the JavaPatcher Patcher instance
	 *
	 * @deprecated Use specific methods such as loadPatches(InputStream)
	 * @return the Patcher
	 */
	@Deprecated
	public static Patcher getPatcher() {
		checkClassLoading();
		return ModPatcherTransformer.getPatcher();
	}

	/**
	 * Loads mixins from the given package.
	 * The package must have a package-info.java with @Mixin annotation
	 *
	 * @param mixinPackage Package to load mixins from
	 */
	public static void loadMixins(String mixinPackage) {
		checkClassLoading();
		ModPatcherTransformer.getMixinApplicator().addSource(mixinPackage);
	}

	/**
	 * Loads all mixins from the given path, regardless of package
	 *
	 * @param path Path to load mixins from
	 */
	public static void loadMixins(Path path) {
		checkClassLoading();
		ModPatcherTransformer.getMixinApplicator().addSource(path);
	}

	/**
	 * Loads all mixins from the given path, if they match the given package
	 *
	 * @param path Path to load mixins from
	 */
	public static void loadMixins(Path path, String packageName) {
		checkClassLoading();
		ModPatcherTransformer.getMixinApplicator().addSource(path, packageName);
	}

	/**
	 * Gets the default patches directory. Any patches in this directory are loaded by ModPatcher on startup.
	 *
	 * @return default patches directory
	 */
	public static String getDefaultPatchesDirectory() {
		checkClassLoading();
		return ModPatcherTransformer.getDefaultPatchesDirectory();
	}


	private static void loadModPatcher() {
		download();

		updateRequired = null;

		addToCurrentClassLoader();

		checkClassLoading();
	}

	private static String getModPatcherRelease() {
		return mcVersion + '-' + System.getProperty("modpatcher.release", modPatcherRelease == null ? "stable" : modPatcherRelease);
	}

	@SuppressWarnings("unchecked")
	private static void addToCurrentClassLoader() {
		ClassLoader cl = ModPatcher.class.getClassLoader();

		if (cl instanceof LaunchClassLoader) {
			if (System.getProperty("modpatcher.allowLoadingUnderLCL").equals("true")) {
				LaunchClassLoader lcl = (LaunchClassLoader) cl;
				cl = ReflectionHelper.<ClassLoader, LaunchClassLoader>getPrivateValue(LaunchClassLoader.class, lcl, "parent");
				lcl.addClassLoaderExclusion("me.nallar.modpatcher");
			} else {
				throw new Error("Can't load ModPatcher under LaunchClassLoader");
			}
		}

		try {
			Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
			method.setAccessible(true);
			method.invoke(cl, modPatcherPath.toUri().toURL());
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	static boolean neverUpdate() {
		return "true".equals(System.getProperty("modPatcher.neverUpdate")) || Files.exists(neverUpdatePath);
	}

	private static boolean isDownloadNeeded() {
		if (neverUpdate())
			return false;

		try {
			return updateRequired.get();
		} catch (InterruptedException | ExecutionException e) {
			log.warn("Failed to check if updates are required", e);
		}
		return false;
	}

	private static void download() {
		if (!isDownloadNeeded())
			return;

		try (InputStream in = new URL(System.getProperty("modpatcher.downloadUrl", "https://modpatcher.nallar.me/" + getModPatcherRelease() + "/ModPatcher-lib.jar")).openConnection().getInputStream()) {
			Files.deleteIfExists(modPatcherPath);
			Files.copy(in, modPatcherPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void checkClassLoading() {
		checkClassLoading(true);
	}

	private static void checkClassLoading(boolean load) {
		try {
			ModPatcherLoadHook.loadHook(requiredVersion, getModPatcherRelease(), API_VERSION);
		} catch (NoClassDefFoundError e) {
			if (!load)
				throw e;

			loadModPatcher();
		}
	}

	private static void startVersionCheck() {
		if (neverUpdate())
			return;

		updateRequired.cancel(true);

		try {
			if (!updateRequired.isDone() || updateRequired.isCancelled() || !updateRequired.get()) {
				updateRequired = new FutureTask<>(() -> {
					Version current = getLastVersion();
					if (requiredVersion.newerThan(current)) {
						try {
							Version online = new Version(Resources.toString(new URL(System.getProperty("modpatcher.versionUrl", "https://modpatcher.nallar.me/" + getModPatcherRelease() + "latest-version.txt")), Charsets.UTF_8).trim());
							return online.compareTo(current) > 0;
						} catch (InterruptedIOException ignored) {
						} catch (Throwable t) {
							log.warn("Failed to check for update", t);
						}
					}
					return false;
				});
			}
		} catch (InterruptedException | ExecutionException e) {
			log.warn("Interrupted when checking done/not cancelled future", e);
		}
	}

	static Version getLastVersion() {
		if (lastVersion != null)
			return lastVersion;

		if (!Files.exists(modPatcherPath))
			return Version.NONE;

		try (FileSystem fs = FileSystems.newFileSystem(modPatcherPath, null)) {
			return lastVersion = new Version(Files.readAllLines(fs.getPath("modpatcher.version"), Charsets.UTF_8).get(0));
		} catch (IOException e) {
			throw new IOError(e);
		}
	}

	static class Version implements Comparable<Version> {
		public static final Version LATEST = new Version(String.valueOf(Integer.MAX_VALUE));
		public static final Version NONE = new Version("0");
		private String version;

		private Version(String version) {
			if (version == null)
				throw new IllegalArgumentException("Version can not be null");
			if (!version.matches("[0-9]+(\\.[0-9]+)*"))
				throw new IllegalArgumentException("Invalid version format");
			this.version = version;
		}

		static Version of(String s) {
			if (s.equalsIgnoreCase("latest")) {
				return LATEST;
			}
			return new Version(s);
		}

		public final String get() {
			return this.version;
		}

		@Override
		public int compareTo(Version that) {
			if (that == null)
				return 1;

			if (this == that || version.equals(that.version))
				return 0;

			String[] thisParts = this.get().split("\\.");
			String[] thatParts = that.get().split("\\.");
			int length = Math.max(thisParts.length, thatParts.length);
			for (int i = 0; i < length; i++) {
				int thisPart = i < thisParts.length ?
					Integer.parseInt(thisParts[i]) : 0;
				int thatPart = i < thatParts.length ?
					Integer.parseInt(thatParts[i]) : 0;
				if (thisPart < thatPart)
					return -1;
				if (thisPart > thatPart)
					return 1;
			}

			return 0;
		}

		@Override
		public int hashCode() {
			return version.hashCode();
		}

		@Override
		public boolean equals(Object that) {
			return this == that || that != null && this.getClass() == that.getClass() && this.compareTo((Version) that) == 0;
		}

		public boolean newerThan(Version other) {
			return compareTo(other) > 0;
		}
	}
}
