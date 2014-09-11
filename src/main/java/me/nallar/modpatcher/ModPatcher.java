package me.nallar.modpatcher;

import javassist.ClassLoaderPool;
import me.nallar.javapatcher.patcher.Patcher;
import me.nallar.javapatcher.patcher.Patches;
import me.nallar.modpatcher.mappings.MCPMappings;
import net.minecraft.launchwrapper.IClassTransformer;

import java.io.*;

public class ModPatcher implements IClassTransformer {
	public static void addPatchesFromInputStream(InputStream is) {
		getPatcher().readPatchesFromInputStream(is);
	}

	public static void addPatchesFromFile(File f) {
		getPatcher().readPatchesFromFile(f);
	}

	public static Patcher getPatcher() {
		return postSrgPatcher;
	}

	public static String getSetupClass() {
		return "me.nallar.modpatcher.ModPatcherSetupClass";
	}

	private static final Patcher preSrgPatcher;
	private static final Patcher postSrgPatcher;
	private static final String ALREADY_LOADED_PROPERTY_NAME = "nallar.ModPatcher.alreadyLoaded";

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
		recursivelyAddXmlFiles(new File("./ModPatchesSrg/"), preSrgPatcher);
		recursivelyAddXmlFiles(new File("./ModPatches/"), postSrgPatcher);
	}

	private static void recursivelyAddXmlFiles(File directory, Patcher patcher) {
		if (!directory.isDirectory()) {
			return;
		}
		for (File f : directory.listFiles()) {
			if (f.isDirectory()) {
				recursivelyAddXmlFiles(f, patcher);
			} else if (f.getName().endsWith(".xml")) {
				patcher.readPatchesFromFile(f);
			}
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

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		return postSrgTransformationHook(name, transformedName, bytes);
	}
}
