package me.nallar.modpatcher;

import org.junit.Assert;
import org.junit.Test;

public class ModPatcherTest {
	@Test
	public void testGetSetupClass() {
		ModPatcher.getSetupClass();

		Assert.assertTrue(ModPatcher.getLastVersion() != null);
	}
}
