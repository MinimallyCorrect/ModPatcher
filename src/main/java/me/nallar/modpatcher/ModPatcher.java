package me.nallar.modpatcher;

import javassist.ClassLoaderPool;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import me.nallar.modpatcher.mappings.MCPMappings;
import net.minecraft.launchwrapper.IClassTransformer;

import java.io.*;
import java.nio.file.*;

public class ModPatcher implements IClassTransformer {
	public static final String MOD_PATCHES_DIRECTORY = "./ModPatches/";
	public static final String MOD_PATCHES_SRG_DIRECTORY = "./ModPatchesSrg/";
	private static final Patcher preSrgPatcher;
	private static final Patcher postSrgPatcher;
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.ModPatcher.alreadyLoaded";
	private static final String DUMP_PROPERTY_NAME = "nallar.ModPatcher.dump";
	private static final boolean DUMP = !System.getProperty(DUMP_PROPERTY_NAME, "").isEmpty();

	static {
		PatcherLog.info("ModPatcher running under classloader " + ModPatcher.class.getClassLoader().getClass().getName());
		boolean alreadyLoaded = System.getProperty(ALREADY_LOADED_PROPERTY_NAME) != null;
		if (alreadyLoaded) {
			PatcherLog.error("Detected multiple classloads of ModPatcher - classloading issue?", new Throwable());
		} else {
			System.setProperty(ALREADY_LOADED_PROPERTY_NAME, "true");
		}
		Patcher preSrgPatcher_;
		Patcher postSrgPatcher_;
		try {
			preSrgPatcher_ = new Patcher(new ClassLoaderPool(false), Patches.class, new MCPMappings(false));
			postSrgPatcher_ = new Patcher(new ClassLoaderPool(true), Patches.class, new MCPMappings(true));
		} catch (Exception t) {
			PatcherLog.error("Failed to create Patcher", t);
			throw new RuntimeException(t);
		}
		preSrgPatcher = preSrgPatcher_;
		postSrgPatcher = postSrgPatcher_;
		// TODO - issue #2. Determine layout/config file structure
		recursivelyAddXmlFiles(new File(MOD_PATCHES_SRG_DIRECTORY), preSrgPatcher);
		recursivelyAddXmlFiles(new File(MOD_PATCHES_DIRECTORY), postSrgPatcher);
	}

	private boolean init;

	/**
	 * Gets the JavaPatcher Patcher instance
	 *
	 * @return the Patcher
	 */
	public static Patcher getPatcher() {
		return postSrgPatcher;
	}

	/**
	 * Gets the name of the setup class to use in your IFMLLoadingPlugin
	 *
	 * @return Name of the ModPatcher setup class
	 */
	public static String getSetupClass() {
		return "me.nallar.modpatcher.ModPatcherSetupClass";
	}

	private static void recursivelyAddXmlFiles(File directory, Patcher patcher) {
		if (!directory.isDirectory()) {
			return;
		}
		try {
			for (File f : directory.listFiles()) {
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

	// TODO - determine whether to remove non-SRG patching? Not usable just now.
	public static byte[] preSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return preSrgPatcher.patch(name, originalBytes);
		} catch (Throwable t) {
			PatcherLog.error("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	public static boolean requiresSrgHook(String transformedName) {
		return postSrgPatcher.willPatch(transformedName);
	}

	public static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return postSrgPatcher.patch(transformedName, originalBytes);
		} catch (Throwable t) {
			PatcherLog.error("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	static void modPatcherAsCoreModStartup() {
		File modPatchesDirectory = new File(MOD_PATCHES_DIRECTORY);
		if (!modPatchesDirectory.exists()) {
			modPatchesDirectory.mkdir();
			try {
				Files.copy(ModPatcher.class.getResourceAsStream("/modpatcher.json.example"), new File(modPatchesDirectory, "/modpatcher.json.example").toPath(), StandardCopyOption.REPLACE_EXISTING);
				Files.copy(ModPatcher.class.getResourceAsStream("/modpatcher.xml.example"), new File(modPatchesDirectory, "/modpatcher.xml.example").toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				PatcherLog.warn("Failed to extract example patcher files", e);
			}
		}
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (!init) {
			init = true;
			getPatcher().logDebugInfo();
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
}
