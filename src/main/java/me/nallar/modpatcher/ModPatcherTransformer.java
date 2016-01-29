package me.nallar.modpatcher;

import javassist.ClassLoaderPool;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
import java.nio.file.*;

class ModPatcherTransformer implements IClassTransformer {
	private static final String MOD_PATCHES_DIRECTORY = "./ModPatches/";
	private static final Patcher patcher;
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.ModPatcher.alreadyLoaded";
	private static final String DUMP_PROPERTY_NAME = "nallar.ModPatcher.dump";
	private static final boolean DUMP = !System.getProperty(DUMP_PROPERTY_NAME, "").isEmpty();
	private static boolean classLoaderInitialised = false;

	static {
		PatcherLog.info("ModPatcher running under classloader " + ModPatcherTransformer.class.getClassLoader().getClass().getName());
		boolean alreadyLoaded = System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null;
		if (alreadyLoaded) {
			PatcherLog.error("Detected multiple classloads of ModPatcher - classloading issue?", new Throwable());
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
		Patcher postSrgPatcher_;
		try {
			postSrgPatcher_ = new Patcher(new ClassLoaderPool(), Patches.class, new MCPMappings());
		} catch (Exception t) {
			PatcherLog.error("Failed to create Patcher", t);
			throw new RuntimeException(t);
		}
		patcher = postSrgPatcher_;
		// TODO - issue #2. Determine layout/config file structure
		recursivelyAddXmlFiles(new File(MOD_PATCHES_DIRECTORY), patcher);
	}

	private boolean init;

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

	static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		LaunchClassLoaderUtil.cacheSrgBytes(transformedName, originalBytes);
		try {
			return patcher.patch(transformedName, originalBytes);
		} catch (Throwable t) {
			PatcherLog.error("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	static IClassTransformer getInstance() {
		return LazyModPatcherHolder.INSTANCE;
	}

	public static void initialiseClassLoader(LaunchClassLoader classLoader) {
		if (classLoaderInitialised)
			return;

		classLoaderInitialised = true;

		classLoader.addClassLoaderExclusion("me.nallar.modpatcher");
		classLoader.addClassLoaderExclusion("javassist");
		LaunchClassLoaderUtil.instance = classLoader;
		LaunchClassLoaderUtil.addTransformer(ModPatcherTransformer.getInstance());
	}

	static String getDefaultPatchesDirectory() {
		return MOD_PATCHES_DIRECTORY;
	}

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
		return postSrgTransformationHook(name, transformedName, bytes);
	}

	private static class LazyModPatcherHolder {
		private static final ModPatcherTransformer INSTANCE = new ModPatcherTransformer();
	}
}
