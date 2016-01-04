package me.nallar.modpatcher;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.appender.FileAppender;

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
		ModPatcher.modPatcherAsCoreModStartup();
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
