package org.minimallycorrect.modpatcher.api;

import org.junit.Assert;
import org.junit.Test;

public class ModPatcherTest {
	@Test
	public void testGetSetupClass() {
		// This might seem like a pointless test, but it actually was broken once after a refactoring...
		Assert.assertEquals(ModPatcherSetup.class.getName(), ModPatcher.getSetupClass());
	}
}
