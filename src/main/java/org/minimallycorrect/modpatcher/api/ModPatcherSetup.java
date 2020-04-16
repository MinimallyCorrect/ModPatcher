package org.minimallycorrect.modpatcher.api;

import org.minimallycorrect.modpatcher.api.tweaker.ModPatcherTweaker;

public class ModPatcherSetup {
	public static void initialise() {
		if (tryLaunchTweakerInit()) {
			return;
		}

		// TODO: more hooks

		System.err.println("Can't initialise ModPatcher, dumping state.");
		System.getenv().forEach((k, v) -> {
			System.err.println(k + "\t=\t" + v);
		});
		System.getProperties().forEach((k, v) -> {
			System.err.println(k + "\t=\t" + v);
		});
		throw new Error("Couldn't initialise ModPatcher");
	}

	private static boolean tryLaunchTweakerInit() {
		try {
			ModPatcherTweaker.add();
		} catch (Throwable t) {
			System.err.println("Failed to add tweaker");
			t.printStackTrace();
			return false;
		}

		System.out.println("Adding tweaker seems successful, init will be delayed until tweaker runs.");
		return true;
	}
}
