package me.nallar.modpatcher;

import LZMA.LzmaInputStream;
import lombok.SneakyThrows;
import lombok.val;
import me.nallar.modpatcher.ModPatcher.Version;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.*;

public class ModPatcherTest {
	@Test
	public void testGetSetupClass() {
		ModPatcher.getSetupClass();

		Assert.assertTrue(ModPatcher.getLastVersion() != null);
	}

	@Test
	public void testVersions() {
		Assert.assertTrue(Version.of("1.8.9-SNAPSHOT").newerThan(Version.of("1.8.9.81")));
		Assert.assertFalse(Version.of("1.8.9.81").newerThan(Version.of("1.8.9-SNAPSHOT")));

		Assert.assertFalse(ModPatcherLoadHook.isOutdated(Version.of("1.8.9-SNAPSHOT"), Version.of("1.8.9.81")));
	}

	@SneakyThrows
	@Test
	public void testMappings() {
		MCPMappings.loadExtends(new LzmaInputStream(new FileInputStream("./generated/extendsMap.obj.lzma")), new HashMap<>());
		MCPMappings.loadCsv(new LzmaInputStream(new FileInputStream("./generated/fields.csv.lzma")), new HashMap<>());
		MCPMappings.loadCsv(new LzmaInputStream(new FileInputStream("./generated/methods.csv.lzma")), new HashMap<>());
	}
}
