package me.nallar.modpatcher.api;

import me.nallar.modpatcher.api.ModPatcher.Version;
import org.junit.Assert;
import org.junit.Test;

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
	}
}
