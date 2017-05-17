package org.minimallycorrect.modpatcher.api;

import org.junit.Test;

import java.util.*;

public class LaunchClassLoaderUtilTest {
	@Test
	public void TestRemoveRedundantExclusions() {
		HashSet<String> test = new HashSet<>();
		test.add("me.nallar.test");
		test.add("me.");
		test.add("me.nallar");
		test.add("org.example.banana");
		test.add("org.example.apple");
		test.add("org.example.pear");
		test.add("org.example.apple.pear");

		LaunchClassLoaderUtil.removeRedundantExclusions(test);
		assert test.contains("me.");
		assert test.contains("org.example.banana");
		assert test.contains("org.example.apple");
		assert test.contains("org.example.pear");
		assert !test.contains("me.nallar");
		assert !test.contains("me.nallar.test");
		assert !test.contains("org.example.apple.pear");
	}
}
