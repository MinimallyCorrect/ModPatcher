package me.nallar.modpatcher;

import javassist.ClassLoaderPool;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import me.nallar.mixin.internal.MixinApplicator;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
import java.nio.file.*;

class ModPatcherTransformer {
	private static final String MOD_PATCHES_DIRECTORY = "./ModPatches/";
	private static final Patcher patcher;
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.ModPatcher.alreadyLoaded";
	private static final String DUMP_PROPERTY_NAME = "nallar.ModPatcher.dump";
	private static final boolean DUMP = !System.getProperty(DUMP_PROPERTY_NAME, "").isEmpty();
	private static boolean classLoaderInitialised = false;
	private static MixinApplicator mixinApplicator;

	static {
		PatcherLog.info("ModPatcher running under classloader " + ModPatcherTransformer.class.getClassLoader().getClass().getName());

		checkForMultipleClassLoads();

		patcher = createPatcher();
	}

	private static Patcher createPatcher() {
		try {
			Patcher patcher = new Patcher(new ClassLoaderPool(), Patches.class, new MCPMappings());

			// TODO - issue #2. Determine layout/config file structure
			recursivelyAddXmlFiles(new File(MOD_PATCHES_DIRECTORY), patcher);

			return patcher;
		} catch (Throwable t) {
			throw logError("Failed to create Patcher", t);
		}
	}

	private static Error logError(String message, Throwable t) {
		PatcherLog.error(message, t);
		return new Error(message, t);
	}

	private static void checkForMultipleClassLoads() {
		if (System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null) {
			Error e = logError("Detected multiple classloads of ModPatcher - classloading issue?", new Throwable());
			if (!System.getProperty(ALREADY_LOADED_PROPERTY_NAME).equals("breakEverything"))
				throw e;
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
	}

	static Patcher getPatcher() {
		return patcher;
	}

	private static void recursivelyAddXmlFiles(File directory, Patcher patcher) {
		File[] files = directory.listFiles();
		if (files == null)
			return;

		try {
			for (File f : files) {
				if (f.isDirectory()) {
					recursivelyAddXmlFiles(f, patcher);
				} else if (f.getName().endsWith(".xml")) {
					patcher.readPatchesFromXmlInputStream(new FileInputStream(f));
				} else if (f.getName().endsWith(".json")) {
					patcher.readPatchesFromJsonInputStream(new FileInputStream(f));
				}
			}
		} catch (IOException e) {
			PatcherLog.warn("Failed to load patch", e);
		}
	}

	static byte[] transformationHook(String name, byte[] originalBytes) {
		LaunchClassLoaderUtil.cacheSrgBytes(name, originalBytes);

		if (mixinApplicator != null) {
			final byte[] finalOriginalBytes = originalBytes;
			originalBytes = getMixinApplicator().getMixinTransformer().transformClass(() -> finalOriginalBytes, name).get();
		}

		try {
			return patcher.patch(name, originalBytes);
		} catch (Throwable t) {
			PatcherLog.error("Failed to patch " + name, t);
		}
		return originalBytes;
	}

	static IClassTransformer getInstance() {
		return ClassTransformer.INSTANCE;
	}

	public static void initialiseClassLoader(LaunchClassLoader classLoader) {
		if (classLoaderInitialised)
			return;

		classLoaderInitialised = true;

		classLoader.addClassLoaderExclusion("me.nallar.");
		classLoader.addClassLoaderExclusion("nallar.");
		classLoader.addClassLoaderExclusion("javassist.");
		classLoader.addClassLoaderExclusion("com.github.javaparser.");

		LaunchClassLoaderUtil.instance = classLoader;
		LaunchClassLoaderUtil.addTransformer(ModPatcherTransformer.getInstance());
		LaunchClassLoaderUtil.removeRedundantExclusions();
	}

	static String getDefaultPatchesDirectory() {
		return MOD_PATCHES_DIRECTORY;
	}

	static MixinApplicator getMixinApplicator() {
		if (mixinApplicator == null)
			mixinApplicator = new MixinApplicator();

		return mixinApplicator;
	}

	private static class ClassTransformer implements IClassTransformer {
		static IClassTransformer INSTANCE = new ClassTransformer();
		private boolean init;

		@Override
		public byte[] transform(String name, String transformedName, byte[] bytes) {
			if (!init) {
				init = true;
				patcher.logDebugInfo();
			}
			if (DUMP) {
				Path path = Paths.get("./DUMP/" + name);
				try {
					Files.createDirectories(path.getParent());
					Files.write(path, bytes);
				} catch (IOException e) {
					PatcherLog.error("Failed to dump class " + name, e);
				}
			}
			return transformationHook(transformedName, bytes);
		}
	}
}
