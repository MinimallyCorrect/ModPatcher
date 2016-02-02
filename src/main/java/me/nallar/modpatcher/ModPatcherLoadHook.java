package me.nallar.modpatcher;

import me.nallar.javatransformer.api.JavaTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

class ModPatcherLoadHook {
	private static final String VERSION = "@VERSION@".replace("-SNAPSHOT", "");

	static void loadedAfterDownload(LaunchClassLoader launchClassLoader) {

	}

	public static void ensureVersion(String version) {
		Version current = new Version(VERSION);
		Version requested = new Version(version);

		if (current.compareTo(requested) > 0) {
			JavaTransformer.pathFromClass(ModPatcherTransformer.class).toFile().deleteOnExit();
			throw new RuntimeException("ModPatcher outdated. Have version: " + VERSION + ", requested version: " + version + "\nWill auto-update on next start.");
		}
	}

	private static class Version implements Comparable<Version> {
		private String version;

		public Version(String version) {
			if (version == null)
				throw new IllegalArgumentException("Version can not be null");
			if (!version.matches("[0-9]+(\\.[0-9]+)*"))
				throw new IllegalArgumentException("Invalid version format");
			this.version = version;
		}

		public final String get() {
			return this.version;
		}

		@Override
		public int compareTo(Version that) {
			if (that == null)
				return 1;

			if (this.version.equals(that.version))
				return 0;

			String[] thisParts = this.get().split("\\.");
			String[] thatParts = that.get().split("\\.");
			int length = Math.max(thisParts.length, thatParts.length);
			for (int i = 0; i < length; i++) {
				int thisPart = i < thisParts.length ?
					Integer.parseInt(thisParts[i]) : 0;
				int thatPart = i < thatParts.length ?
					Integer.parseInt(thatParts[i]) : 0;
				if (thisPart < thatPart)
					return -1;
				if (thisPart > thatPart)
					return 1;
			}
			return 0;
		}

		@Override
		public int hashCode() {
			return version.hashCode();
		}

		@Override
		public boolean equals(Object that) {
			return this == that || that != null && this.getClass() == that.getClass() && this.compareTo((Version) that) == 0;
		}
	}
}
