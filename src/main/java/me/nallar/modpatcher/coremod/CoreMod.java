package me.nallar.modpatcher.coremod;

import me.nallar.modpatcher.ModPatcher;
import me.nallar.modpatcher.PatcherLog;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.appender.FileAppender;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@IFMLLoadingPlugin.Name("ModPatcher")
@IFMLLoadingPlugin.SortingIndex(1001) // Magic value, after deobf transformer.
public class CoreMod implements IFMLLoadingPlugin {
	static {
		logToFile();
	}

	private static void logToFile() {
		FileAppender fa = FileAppender.createAppender("./logs/ModPatcher.log", "false", "false", "PatcherAppender", "true", "true", "true", null, null, "false", null, null);
		fa.start();
		((org.apache.logging.log4j.core.Logger) LogManager.getLogger("ModPatcher")).addAppender(fa);
		((org.apache.logging.log4j.core.Logger) LogManager.getLogger("JavaPatcher")).addAppender(fa);
	}

	private static void extractFile(String name, File to) throws IOException {
		Files.copy(CoreMod.class.getResourceAsStream(name), new File(to, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public String[] getASMTransformerClass() {
		return new String[0];
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return ModPatcher.getSetupClass();
	}

	@Override
	public void injectData(Map<String, Object> data) {
		File modPatchesDirectory = new File(ModPatcher.getDefaultPatchesDirectory());
		if (!modPatchesDirectory.exists() && !modPatchesDirectory.mkdirs()) {
			throw new IOError(new IOException("Failed to make directory " + modPatchesDirectory));
		}

		try {
			extractFile("/modpatcher.json.example", modPatchesDirectory);
			extractFile("/modpatcher.xml.example", modPatchesDirectory);
		} catch (IOException e) {
			PatcherLog.warn("Failed to extract example patcher files", e);
		}
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
