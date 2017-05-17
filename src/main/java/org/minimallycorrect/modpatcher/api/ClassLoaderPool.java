package org.minimallycorrect.modpatcher.api;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import lombok.SneakyThrows;
import lombok.val;

import java.io.*;
import java.net.*;

public class ClassLoaderPool extends ClassPool {
	ClassLoaderPool() {
		this.appendClassPath(new LaunchClassLoaderPath());
		this.appendSystemPath();
		this.importPackage("java.util");
	}

	@Override
	public CtClass getCached(String className) {
		return super.getCached(className);
	}

	@Override
	protected synchronized CtClass get0(String className, boolean useCache) throws NotFoundException {
		return super.get0(className, true);
	}

	void dropCache(String name, boolean allowFailure) {
		if (classes.remove(name) == null && !allowFailure) {
			// TODO: Should be behind a system property? Could be very spammy
			PatcherLog.warn("Failed to drop " + name + " from cache. Currently cached: " + classes.keySet().toString());
		}
	}

	public static class Handler extends URLStreamHandler {
		final byte[] data;

		Handler(byte[] data) {
			this.data = data;
		}

		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			return new MockHttpURLConnection(u, data);
		}

		public static class MockHttpURLConnection extends HttpURLConnection {
			private final byte[] data;

			MockHttpURLConnection(URL url, byte[] data) {
				super(url);
				this.data = data;
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return new ByteArrayInputStream(data);
			}

			@Override
			public void connect() throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void disconnect() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean usingProxy() {
				return false;
			}

		}
	}

	private class LaunchClassLoaderPath implements ClassPath {
		@Override
		public InputStream openClassfile(String className) throws NotFoundException {
			val bytes = LaunchClassLoaderUtil.getSrgBytes(className, true);
			if (bytes == null)
				return null;
			return new ByteArrayInputStream(bytes);
		}

		@SneakyThrows
		@Override
		public URL find(String className) {
			val bytes = LaunchClassLoaderUtil.getSrgBytes(className, true);
			if (bytes == null)
				return null;
			return new URL(null, "runtimeclass:" + className.replace(".", "/"), new Handler(bytes));
		}

		@Override
		public void close() {
		}
	}
}
