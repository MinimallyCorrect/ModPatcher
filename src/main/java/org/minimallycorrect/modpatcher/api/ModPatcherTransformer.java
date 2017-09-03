package org.minimallycorrect.modpatcher.api;

import lombok.val;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.minimallycorrect.mixin.internal.ApplicationType;
import org.minimallycorrect.mixin.internal.MixinApplicator;
import org.minimallycorrect.modpatcher.api.tweaker.ModPatcherTweaker;

import java.io.*;
import java.nio.file.*;

public class ModPatcherTransformer {
	private static final String MOD_PATCHES_DIRECTORY = "./ModPatches/";
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.ModPatcher.alreadyLoaded";
	private static final String DUMP_PROPERTY_NAME = "nallar.ModPatcher.dump";
	private static final boolean DUMP = !System.getProperty(DUMP_PROPERTY_NAME, "").isEmpty();
	private static boolean classLoaderInitialised = false;
	private static boolean haveTransformedClasses;
	private static Patcher patcher;
	private static MixinApplicator mixinApplicator;

	static {
		PatcherLog.info("ModPatcher running under classloader " + ModPatcherTransformer.class.getClassLoader().getClass().getName());

		checkForMultipleClassLoads();

		try {
			// TODO - issue #2. Determine layout/config file structure
			recursivelyAddXmlFiles(new File(MOD_PATCHES_DIRECTORY));
		} catch (Throwable t) {
			throw logError("Failed to load patches from " + MOD_PATCHES_DIRECTORY, t);
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

	public static Patcher getPatcher() {
		if (patcher == null) {
			if (haveTransformedClasses)
				throw new IllegalStateException("Too late to initialise patcher");
			patcher = new Patcher(new ClassLoaderPool(), Patches.class, new MCPMappings());
		}

		return patcher;
	}

	@SuppressWarnings("deprecation")
	private static void recursivelyAddXmlFiles(File directory) {
		File[] files = directory.listFiles();
		if (files == null)
			return;

		try {
			for (File f : files) {
				if (f.isDirectory()) {
					recursivelyAddXmlFiles(f);
				} else if (f.getName().endsWith(".xml")) {
					getPatcher().readPatchesFromXmlInputStream(new FileInputStream(f));
				} else if (f.getName().endsWith(".json")) {
					getPatcher().readPatchesFromJsonInputStream(new FileInputStream(f));
				}
			}
		} catch (IOException e) {
			PatcherLog.warn("Failed to load patch", e);
		}
	}

	public static IClassTransformer getInstance() {
		return ClassTransformer.INSTANCE;
	}

	static void initialiseClassLoader(LaunchClassLoader classLoader) {
		if (classLoaderInitialised)
			return;
		classLoaderInitialised = true;

		LaunchClassLoaderUtil.instance = classLoader;
		ModPatcherTweaker.add();
		classLoader.addTransformerExclusion("org.minimallycorrect.modpatcher");
		classLoader.addTransformerExclusion("org.minimallycorrect.javatransformer");
		classLoader.addTransformerExclusion("org.minimallycorrect.mixin");
		classLoader.addTransformerExclusion("me.nallar.javapatcher");
		classLoader.addTransformerExclusion("javassist");
		classLoader.addTransformerExclusion("com.github.javaparser");
		LaunchClassLoaderUtil.addTransformer(ModPatcherTransformer.getInstance());
	}

	static String getDefaultPatchesDirectory() {
		return MOD_PATCHES_DIRECTORY;
	}

	static MixinApplicator getMixinApplicator() {
		MixinApplicator mixinApplicator = ModPatcherTransformer.mixinApplicator;

		if (mixinApplicator == null) {
			if (haveTransformedClasses)
				throw new IllegalStateException("Too late to initialise mixin applicator");
			ModPatcherTransformer.mixinApplicator = mixinApplicator = new MixinApplicator();
			mixinApplicator.setApplicationType(ApplicationType.FINAL_PATCH);
			mixinApplicator.setNoMixinIsError(true);
			mixinApplicator.setLog(PatcherLog::info);
		}

		return mixinApplicator;
	}

	private static class ClassTransformer implements IClassTransformer {
		static IClassTransformer INSTANCE = new ClassTransformer();

		private static void dumpIfEnabled(String name, byte[] data) {
			if (!DUMP || !name.contains("net.minecraft"))
				return;

			Path path = Paths.get("./DUMP/" + name + ".class");
			try {
				Files.createDirectories(path.getParent());
				Files.write(path, data);
			} catch (IOException e) {
				PatcherLog.error("Failed to dump class " + name, e);
			}
		}

		@Override
		public byte[] transform(String name, String transformedName, byte[] bytes) {
			haveTransformedClasses = true;

			dumpIfEnabled(transformedName + "_unpatched", bytes);

			final byte[] originalBytes = bytes;
			val mixinApplicator = ModPatcherTransformer.mixinApplicator;
			if (mixinApplicator != null)
				bytes = mixinApplicator.getMixinTransformer().transformClass(() -> originalBytes, transformedName).get();

			val patcher = ModPatcherTransformer.patcher;
			if (patcher != null) {
				LaunchClassLoaderUtil.cacheSrgBytes(transformedName, bytes);
				try {
					bytes = patcher.patch(transformedName, bytes);
				} catch (Throwable t) {
					PatcherLog.error("Failed to patch " + transformedName, t);
				} finally {
					LaunchClassLoaderUtil.releaseSrgBytes(transformedName);
				}
			}

			if (originalBytes != bytes)
				dumpIfEnabled(transformedName, bytes);

			return bytes;
		}
	}
}
