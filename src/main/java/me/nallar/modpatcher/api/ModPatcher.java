package me.nallar.modpatcher.api;

import lombok.SneakyThrows;
import lombok.val;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.modpatcher.internal.ModPatcherLoadHook;
import me.nallar.modpatcher.internal.ModPatcherTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/**
 * ModPatcher API
 *
 * This class is the public facing API of ModPatcher
 */
public class ModPatcher {
	private static final int API_VERSION = 2;
	private static final Logger log = LogManager.getLogger("ModPatcher");
	private static final String MODPATCHER_LIB_NAME = "ModPatcher.jlib";
	private static final Path modPatcherPath = realPath("mods/" + MODPATCHER_LIB_NAME);
	private static final String MODPATCHER_API_PACKAGE = "me.nallar.modpatcher.api";
	private static final String MODPATCHER_INTERNAL_PACKAGE = "me.nallar.modpatcher.internal";
	private static final Map<URL, Future<Version>> requestedVersions = new ConcurrentHashMap<>();
	private static boolean checked = false;

	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass() {
		return "me.nallar.modpatcher.api.ModPatcherSetup";
	}

	/**
	 * Requests the given ModPatcher version
	 *
	 * @param modpatcher URL to bundled modpatcher
	 */
	public static void requireVersion(URL modpatcher) {
		requestedVersions.put(modpatcher, CompletableFuture.supplyAsync(() -> getVersion(modpatcher)));
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

	private static Path realPath(String s) {
		Path absolute = Paths.get(s).toAbsolutePath();
		try {
			return absolute.toRealPath();
		} catch (IOException e) {
			return absolute;
		}
	}

	private static void loadModPatcher() {
		update();

		LaunchClassLoader classLoader = addToCurrentClassLoader();

		checkClassLoading(false);

		ModPatcherTransformer.initialiseClassLoader(classLoader);
	}

	@SuppressWarnings("unchecked")
	private static LaunchClassLoader addToCurrentClassLoader() {
		ClassLoader cl = ModPatcher.class.getClassLoader();

		try {
			final URL url = modPatcherPath.toUri().toURL();
			log.trace("Adding " + url + " to classloader");
			if (!(cl instanceof LaunchClassLoader)) {
				throw new UnsupportedOperationException("Should be running under LaunchClassLoader");
			}

			LaunchClassLoader lcl = (LaunchClassLoader) cl;
			lcl.addClassLoaderExclusion(MODPATCHER_INTERNAL_PACKAGE);
			lcl.addClassLoaderExclusion("javassist.");
			lcl.addClassLoaderExclusion("com.github.javaparser.");
			lcl.addClassLoaderExclusion("me.nallar.whocalled.");
			lcl.addClassLoaderExclusion("me.nallar.javatransformer.");
			lcl.addClassLoaderExclusion("me.nallar.javapatcher.");
			lcl.addClassLoaderExclusion("me.nallar.mixin.");

			lcl.addTransformerExclusion(MODPATCHER_API_PACKAGE);

			Field parent = LaunchClassLoader.class.getDeclaredField("parent");
			parent.setAccessible(true);
			parent.set(lcl, new JLibClassLoader((ClassLoader) parent.get(lcl), url));

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

			log.trace("Loaded class: " + Class.forName(MODPATCHER_INTERNAL_PACKAGE + ".ModPatcherLoadHook"));
			return lcl;
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private static boolean removeModPatcherEntries(String entry) {
		return entry.replace('/', '.').startsWith(MODPATCHER_INTERNAL_PACKAGE);
	}

	@SneakyThrows
	private static void update() {
		Version version = getLastVersion();
		val current = version;
		URL url = null;

		for (val entry : requestedVersions.entrySet()) {
			val entryUrl = entry.getKey();
			val entryVersion = entry.getValue().get();

			if (entryVersion.compareTo(version) > 0) {
				version = entryVersion;
				url = entryUrl;
			}
		}

		if (url == null)
			return;

		log.info("Current modpatcher version: " + current);
		log.info("Extracting new version " + version + " from " + url);

		try (InputStream in = url.openConnection().getInputStream()) {
			Files.deleteIfExists(modPatcherPath);
			Files.createDirectories(modPatcherPath.getParent());
			Files.copy(in, modPatcherPath);
		} catch (IOException e) {
			log.error("Failed to download ModPatcher", e);
		}
	}

	static void checkClassLoading() {
		checkClassLoading(true);
	}

	private static void checkClassLoading(boolean load) {
		if (checked)
			return;

		try {
			Class.forName(MODPATCHER_INTERNAL_PACKAGE + ".ModPatcherLoadHook");
			ModPatcherLoadHook.loadHook(API_VERSION);
			checked = true;
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			if (!load)
				throw new Error(e);

			loadModPatcher();
		}
	}

	static Version getLastVersion() {
		if (!Files.exists(modPatcherPath))
			return Version.NONE;

		try {
			return getVersion(modPatcherPath.toUri().toURL());
		} catch (MalformedURLException e) {
			throw new IOError(e);
		}
	}

	private static Version getVersion(URL url) {
		try (ZipInputStream zis = new ZipInputStream(url.openStream())) {
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null) {
				if ("modpatcher.version".equals(e.getName())) {
					Scanner s = new Scanner(zis).useDelimiter("\\A");
					return new Version(s.hasNext() ? s.next() : "");
				}
			}
			throw new IOError(new FileNotFoundException("modpatcher.version in " + url));
		} catch (IOException e) {
			throw new IOError(e);
		}
	}

	public static class Version implements Comparable<Version> {
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

		public static Version of(String s) {
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

	static class JLibClassLoader extends URLClassLoader {
		static {
			ClassLoader.registerAsParallelCapable();
		}

		private final ClassLoader parent;

		JLibClassLoader(ClassLoader parent, URL... urls) {
			super(urls, null);
			this.parent = parent;
		}

		@Override
		public Class<?> findClass(String name) throws ClassNotFoundException {
			Class<?> loaded = super.findLoadedClass(name);
			if (loaded != null)
				return loaded;
			try {
				return super.findClass(name);
			} catch (ClassNotFoundException e) {
				return parent.loadClass(name);
			}
		}
	}
}
