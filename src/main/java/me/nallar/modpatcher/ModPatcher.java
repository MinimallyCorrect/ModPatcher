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
import java.util.*;
import java.util.concurrent.*;

/**
 * ModPatcher API
 *
 * This class is the public facing API of ModPatcher
 *
 * It automatically downloads ModPatcher if the file is missing from the libs folder, or a coremod depends on
 * a newer version of modpatcher than the installed version
 *
 * This behaviour can be disabled by creating the file "mods/ModPatcher_NEVER_UPDATE.txt"
 */
public class ModPatcher {
	private static final int API_VERSION = 1;
	private static final Logger log = LogManager.getLogger("ModPatcher");
	private static final String MC_VERSION = "@MC_VERSION@";
	private static final String MODPATCHER_NEVER_UPDATE_NAME = "ModPatcher_NEVER_UPDATE.txt";
	private static final String MODPATCHER_LIB_NAME = "ModPatcher.jlib";
	private static final Path neverUpdatePath = realPath("mods/" + MODPATCHER_NEVER_UPDATE_NAME);
	private static final Path modPatcherPath = realPath("mods/" + MODPATCHER_LIB_NAME);
	private static final boolean modPatcherPresent = Files.exists(modPatcherPath);
	private static final Future<Boolean> defaultUpdateRequired = CompletableFuture.completedFuture(!modPatcherPresent);
	private static final String DOWNLOAD_URL_PROPERTY = "modpatcher.downloadUrl";
	private static final String DOWNLOAD_URL_DEFAULT = "https://modpatcher.nallar.me/%1/ModPatcher-lib.jar";
	private static final String REQUIRED_VERSION_PROPERTY = "modpatcher.requiredVersion";
	private static final String RELEASE_PROPERTY = "modpatcher.release";
	private static final String VERSION_URL_DEFAULT = "https://modpatcher.nallar.me/%s/version.txt";
	private static final String VERSION_URL_PROPERTY = "modpatcher.versionUrl";
	private static final String NEVER_UPDATE_PROPERTY = "modpatcher.neverUpdate";
	private static final String DEFAULT_RELEASE = "stable";
	private static final String MODPATCHER_PACKAGE = "me.nallar.modpatcher";
	private static String modPatcherRelease;
	private static Future<Boolean> updateRequired = defaultUpdateRequired;
	private static Version requiredVersion;
	private static Version lastVersion;
	private static boolean checked = false;

	static {
		requireVersionInternal(null, null);
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
	 * Requests the given ModPatcher version
	 *
	 * @param versionString Minimum version of ModPatcher required. Special value "latest" always uses latest version
	 */
	public static void requireVersion(String versionString) {
		requireVersion(versionString, null);
	}

	/**
	 * Requests the given ModPatcher version
	 *
	 * @param versionString Minimum version of ModPatcher required. Special value "latest" always uses latest version
	 * @param release       Release stream to use. null by default
	 */
	public static void requireVersion(String versionString, String release) {
		requireVersionInternal(versionString, release);
	}

	/**
	 * Load JavaPatcher patches
	 *
	 * @param inputStream stream to load patches from
	 */
	public static void loadPatches(InputStream inputStream) {
		checkClassLoading();
		ModPatcherTransformer.getPatcher().loadPatches(inputStream);
	}

	/**
	 * Load JavaPatcher patches
	 *
	 * @param patches String to load patches from
	 */
	public static void loadPatches(String patches) {
		checkClassLoading();
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

	static void initialiseClassLoader(LaunchClassLoader classLoader) {
		checkClassLoading();
		ModPatcherTransformer.initialiseClassLoader(classLoader);
	}

	private static Path realPath(String s) {
		Path absolute = Paths.get(s).toAbsolutePath();
		try {
			return absolute.toRealPath();
		} catch (IOException e) {
			return absolute;
		}
	}

	private static void requireVersionInternal(String versionString, String release) {
		if (updateRequired == null)
			throw new Error("Modpatcher has already been loaded, it is too late to call getSetupClass");

		versionString = System.getProperty(REQUIRED_VERSION_PROPERTY, versionString);
		release = System.getProperty(RELEASE_PROPERTY, release);

		if (release != null && versionString == null)
			throw new IllegalArgumentException("versionString must be non-null if release is non-null");

		boolean startCheck = false;

		if (release != null) {
			if (modPatcherRelease == null) {
				modPatcherRelease = release;
				startCheck = true;
			} else {
				log.warn("Conflicting ModPatcher release requests. Set to " + modPatcherRelease + ", requested: " + release);
			}
		}

		if (versionString != null) {
			Version requested = Version.of(versionString);
			if (requested.compareTo(requiredVersion) > 0) {
				requiredVersion = requested;
				startCheck = true;
			}
		}

		if (startCheck)
			startVersionCheck();
	}

	private static void loadModPatcher() {
		if (neverUpdate() && !modPatcherPresent)
			throw new Error(
				"ModPatcher is set to never update as " + MODPATCHER_NEVER_UPDATE_NAME + " exists, but the ModPatcher " +
					"library (" + MODPATCHER_LIB_NAME + ") is not in the mods folder.\n" +
					"As automatic updating is disabled, we can not retrieve a compatible version of ModPatcher.\n" +
					"Please place a compatible version of ModPatcher-lib in the mods folder and rename it to " +
					MODPATCHER_LIB_NAME
			);

		download();

		updateRequired = null;

		addToCurrentClassLoader();

		checkClassLoading(false);
	}

	private static String getModPatcherRelease() {
		return MC_VERSION + '-' + (modPatcherRelease == null ? DEFAULT_RELEASE : modPatcherRelease);
	}

	@SuppressWarnings("unchecked")
	private static void addToCurrentClassLoader() {
		ClassLoader cl = ModPatcher.class.getClassLoader();

		try {
			final URL url = modPatcherPath.toUri().toURL();
			log.trace("Adding " + url + " to classloader");
			if (cl instanceof LaunchClassLoader) {
				LaunchClassLoader lcl = (LaunchClassLoader) cl;
				lcl.addTransformerExclusion(MODPATCHER_PACKAGE);
				lcl.addURL(url);

				boolean cclInvalidNegativeCleared = false;
				try {
					// No, IDEA, it's not always true
					//noinspection ConstantConditions
					lcl.clearFailures(ModPatcher::removeModPatcherEntries);
					cclInvalidNegativeCleared = true;
				} catch (NoClassDefFoundError | NoSuchMethodError ignored) {
				}

				//and that's not always true either
				//noinspection ConstantConditions
				if (!cclInvalidNegativeCleared) {
					Set<String> invalidClasses = ReflectionHelper.getPrivateValue(LaunchClassLoader.class, lcl, "invalidClasses");
					Set<String> negativeResources = ReflectionHelper.getPrivateValue(LaunchClassLoader.class, lcl, "negativeResourceCache");
					invalidClasses.removeIf(ModPatcher::removeModPatcherEntries);
					negativeResources.removeIf(ModPatcher::removeModPatcherEntries);
				}

				log.trace("Loaded class: " + Class.forName(MODPATCHER_PACKAGE + ".ModPatcherLoadHook"));
			} else {
				Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
				method.setAccessible(true);
				method.invoke(cl, url);
			}
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private static boolean removeModPatcherEntries(String entry) {
		return entry.replace('/', '.').startsWith(MODPATCHER_PACKAGE);
	}

	static boolean neverUpdate() {
		return "true".equals(System.getProperty(NEVER_UPDATE_PROPERTY)) || Files.exists(neverUpdatePath);
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

		String url = String.format(System.getProperty(DOWNLOAD_URL_PROPERTY, DOWNLOAD_URL_DEFAULT), getModPatcherRelease());
		try (InputStream in = new URL(url).openConnection().getInputStream()) {
			Files.deleteIfExists(modPatcherPath);
			Files.createDirectories(modPatcherPath.getParent());
			Files.copy(in, modPatcherPath);
		} catch (IOException e) {
			log.error("Failed to download ModPatcher", e);
		}
	}

	private static void checkClassLoading() {
		checkClassLoading(true);
	}

	private static void checkClassLoading(boolean load) {
		if (checked)
			return;

		try {
			Class.forName(MODPATCHER_PACKAGE + ".ModPatcherLoadHook");
			ModPatcherLoadHook.loadHook(requiredVersion, getModPatcherRelease(), API_VERSION);
			checked = true;
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			if (!load)
				throw new Error(e);

			loadModPatcher();
		}
	}

	private static void startVersionCheck() {
		if (neverUpdate() || requiredVersion == null)
			return;

		updateRequired.cancel(true);

		try {
			if (!updateRequired.isDone() || updateRequired.isCancelled() || !updateRequired.get()) {
				FutureTask<Boolean> task;
				updateRequired = task = new FutureTask<>(() -> {
					Version current;
					try {
						current = getLastVersion();
					} catch (Exception e) {
						current = Version.NONE;
						log.warn("Failed to determine current ModPatcher version, assuming it is outdated", e);
					}
					if (requiredVersion.newerThan(current)) {
						try {
							String url = String.format(System.getProperty(VERSION_URL_PROPERTY, VERSION_URL_DEFAULT), getModPatcherRelease());
							Version online = new Version(Resources.toString(new URL(url), Charsets.UTF_8).trim());
							return online.compareTo(current) > 0;
						} catch (InterruptedIOException ignored) {
						} catch (Throwable t) {
							log.warn("Failed to check for update", t);
						}
					}
					return false;
				});
				task.run();
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
		static final Version LATEST = new Version(String.valueOf(Integer.MAX_VALUE));
		static final Version NONE = new Version("0");
		private String version;
		private boolean snapshot;

		private Version(String version) {
			if (version == null)
				throw new IllegalArgumentException("Version can not be null");
			version = version.trim();
			if (version.endsWith("-SNAPSHOT")) {
				version = version.replace("-SNAPSHOT", "");
				snapshot = true;
			}
			if (!version.matches("[0-9]+(\\.[0-9]+)*"))
				throw new IllegalArgumentException("Invalid version format. Should consist entirely of digits and dots. Got '" + version + "'");
			this.version = version;
		}

		static Version of(String s) {
			if (s.equalsIgnoreCase("latest")) {
				return LATEST;
			}
			return new Version(s);
		}

		@Override
		public int compareTo(@SuppressWarnings("NullableProblems") Version that) {
			if (that == null)
				return 1;

			if (this == that || version.equals(that.version))
				return 0;

			String[] thisParts = version.split("\\.");
			String[] thatParts = that.version.split("\\.");
			int length = Math.max(thisParts.length, thatParts.length);
			for (int i = 0; i < length; i++) {
				int thisPart = i < thisParts.length ?
					Integer.parseInt(thisParts[i]) : 0;
				int thatPart = i < thatParts.length ?
					Integer.parseInt(thatParts[i]) : 0;

				if (this.snapshot && i >= thisParts.length)
					thisPart = Integer.MAX_VALUE;

				if (that.snapshot && i >= thatParts.length)
					thatPart = Integer.MAX_VALUE;

				if (thisPart < thatPart)
					return -1;
				if (thisPart > thatPart)
					return 1;
			}

			return this.snapshot == that.snapshot ? 0 : (this.snapshot ? 1 : -1);
		}

		@Override
		public String toString() {
			return version + (snapshot ? "-SNAPSHOT" : "");
		}

		@Override
		public int hashCode() {
			return toString().hashCode();
		}

		@Override
		public boolean equals(Object that) {
			return this == that || that != null && this.getClass() == that.getClass() && this.compareTo((Version) that) == 0;
		}

		boolean newerThan(Version other) {
			return compareTo(other) > 0;
		}
	}
}
