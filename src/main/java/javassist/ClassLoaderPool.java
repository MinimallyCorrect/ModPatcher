package javassist;

import me.nallar.modpatcher.LaunchClassLoaderUtil;
import me.nallar.modpatcher.PatcherLog;

import java.io.*;
import java.net.*;

/**
 * This is in the javassist package (which isn't sealed) to access package-local javassist internals needed to load
 * from the classloader at runtime for patching purposes.
 */
public class ClassLoaderPool extends ClassPool {
	public ClassLoaderPool() {
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

	private byte[] getClassBytesRuntime(String className, boolean allowReTransform) {
		try {
			return LaunchClassLoaderUtil.getSrgBytes(className, allowReTransform);
		} catch (RuntimeException e) {
			if (e.getMessage().contains("No SRG transformer")) {
				throw new RuntimeException("Classloader used to load LaunchClassLoader: " + LaunchClassLoaderUtil.getInstance().getClass().getClassLoader(), e);
			}
			throw e;
		}
	}

	@Override
	public URL find(String className) {
		if (!className.contains(".")) {
			return null;
		}
		byte[] bytes = getClassBytesRuntime(className, true);
		if (bytes != null) {
			try {
				return new URL(null, "runtimeclass:" + className.replace(".", "/"), new Handler(bytes));
			} catch (MalformedURLException e) {
				PatcherLog.error("Failed to make fake URL for " + className, e);
			}
		}
		return source.find(className);
	}

	@Override
	InputStream openClassfile(String className) throws NotFoundException {
		byte[] bytes = getClassBytesRuntime(className, true);
		if (bytes != null) {
			return new ByteArrayInputStream(bytes);
		}
		return source.openClassfile(className);
	}

	@Override
	void writeClassfile(String className, OutputStream out) throws NotFoundException, IOException, CannotCompileException {
		byte[] bytes = getClassBytesRuntime(className, false);
		if (bytes != null) {
			out.write(bytes);
		} else {
			source.writeClassfile(className, out);
		}
	}

	public void dropCache(String name, boolean allowFailure) {
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
}
