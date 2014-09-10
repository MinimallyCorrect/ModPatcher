package net.minecraft.launchwrapper;

import com.google.common.base.Joiner;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import me.nallar.javapatcher.Log;
import me.nallar.modpatcher.PatchHook;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.jar.Attributes.*;

public class LaunchClassLoader extends URLClassLoader {
	// MP start
	private IClassTransformer deobfuscationTransformer;
	public static LaunchClassLoader instance;

	private boolean initedMpPatcher = false;
	public static final long launchTime = System.currentTimeMillis();

	private static final Method findLoaded = getFindLoaded();

	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private static final byte[] CACHE_MISS = new byte[0];

	public static void testForModPatcher() {
		// Do nothing, just make this method exist.
	}

	private void mpPatchInit() {
		if (!initedMpPatcher) {
			initedMpPatcher = true;
			try {
				FMLRelaunchLog.fine("Dummy log message to make sure that FMLRelaunchLog has been set up.");
			} catch (Throwable t) {
				System.err.println("Failure in FMLRelaunchLog");
				t.printStackTrace(System.err);
			}
			try {
				Class.forName("me.nallar.modpatcher.PatchHook");
			} catch (ClassNotFoundException e) {
				FMLRelaunchLog.log(Level.ERROR, e, "Failed to init TT PatchHook");
				System.exit(1);
			}
		}
	}

	private static Method getFindLoaded() {
		try {
			Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
			m.setAccessible(true);
			return m;
		} catch (NoSuchMethodException e) {
			LogWrapper.log(Level.ERROR, e, "Failed to get findLoadedClass method");
			return null;
		}
	}

	@SuppressWarnings("ConstantConditions")
	private static byte[] runTransformer(final String name, final String transformedName, byte[] basicClass, final IClassTransformer transformer) {
		try {
			return transformer.transform(name, transformedName, basicClass);
		} catch (Throwable t) {
			String message = t.getMessage();
			if (message != null && message.contains("for invalid side")) {
				if (t instanceof RuntimeException) {
					throw (RuntimeException) t;
				} else if (t instanceof Error) {
					throw (Error) t;
				} else {
					throw new RuntimeException(t);
				}
			} else if (basicClass != null || DEBUG_FINER) {
				FMLRelaunchLog.log((DEBUG_FINER && basicClass != null) ? Level.WARN : Level.TRACE, t, "Failed to transform " + name);
			}
			return basicClass;
		}
	}

	private final HashMap<String, byte[]> cachedSrgClasses = new HashMap<String, byte[]>();

	private byte[] transformUpToSrg(final String name, final String transformedName, byte[] basicClass) {
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null) {
			return cached;
		}
		for (final IClassTransformer transformer : transformers) {
			basicClass = runTransformer(name, transformedName, basicClass, transformer);
			if (transformer == deobfuscationTransformer) {
				cachedSrgClasses.put(transformedName, basicClass);
				return basicClass;
			}
		}
		throw new RuntimeException("No SRG transformer!" + Joiner.on(",\n").join(transformers) + " -> " + deobfuscationTransformer);
	}

	private byte[] transformAfterSrg(final String name, final String transformedName, byte[] basicClass) {
		boolean pastSrg = false;
		for (final IClassTransformer transformer : transformers) {
			if (pastSrg) {
				basicClass = runTransformer(name, transformedName, basicClass, transformer);
			} else if (transformer == deobfuscationTransformer) {
				pastSrg = true;
			}
		}
		if (!pastSrg) {
			throw new RuntimeException("No SRG transformer!" + Joiner.on(",\n").join(transformers) + " -> " + deobfuscationTransformer);
		}
		return basicClass;
	}

	public byte[] getPreSrgBytes(String name) {
		name = untransformName(name);
		try {
			return getClassBytes(name);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public byte[] getSrgBytes(String name) {
		final String transformedName = transformName(name);
		name = untransformName(name);
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null) {
			return cached;
		}
		try {
			byte[] bytes = getClassBytes(name);
			return transformUpToSrg(name, transformedName, bytes);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public boolean excluded(String name) {
		for (final String exception : classLoaderExceptions) {
			if (name.startsWith(exception)) {
				return true;
			}
		}
		return false;
	}
	// MP end
	public static final int BUFFER_SIZE = 1 << 12;
	private List<URL> sources;
	private ClassLoader parent = getClass().getClassLoader();

	private List<IClassTransformer> transformers = new ArrayList<IClassTransformer>(2);
	private Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<String, Class<?>>();
	private Set<String> invalidClasses = new HashSet<String>(1000);

	private Set<String> classLoaderExceptions = new HashSet<String>();
	private Set<String> transformerExceptions = new HashSet<String>();
	private Map<String,byte[]> resourceCache = new ConcurrentHashMap<String,byte[]>(1000);
	private Set<String> negativeResourceCache = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	private IClassNameTransformer renameTransformer;

	private static final Manifest EMPTY = new Manifest();

	private final ThreadLocal<byte[]> loadBuffer = new ThreadLocal<byte[]>();

	private static final String[] RESERVED_NAMES = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("legacy.debugClassLoading", "false"));
	private static final boolean DEBUG_FINER = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingFiner", "false"));
	private static final boolean DEBUG_SAVE = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingSave", "false"));
	private static File tempFolder = null;

	public LaunchClassLoader(URL[] sources) {
		super(sources, null);
		// MP start
		if (instance == null) {
			instance = this;
			Thread.currentThread().setContextClassLoader(this);
		} else {
			LogWrapper.log(Level.ERROR, new Throwable(), "Initing multiple LaunchClassLoaders - what?!");
		}
		// MP end
		this.sources = new ArrayList<URL>(Arrays.asList(sources));

		// MP start
		// classloader exclusions
		addClassLoaderExclusion("java.");
		addClassLoaderExclusion("javassist.");
		addClassLoaderExclusion("sun.");
		addClassLoaderExclusion("org.lwjgl.");
		addClassLoaderExclusion("org.apache.logging.");
		addClassLoaderExclusion("net.minecraft.launchwrapper.");
		addClassLoaderExclusion("argo.");
		addClassLoaderExclusion("org.objectweb.asm.");

		// transformer exclusions
		addTransformerExclusion("javax.");
		addTransformerExclusion("com.google.common.");
		addTransformerExclusion("org.bouncycastle.");
		// MP end

		if (DEBUG_SAVE) {
			int x = 1;
			tempFolder = new File(Launch.minecraftHome, "CLASSLOADER_TEMP");
			while (tempFolder.exists() && x <= 10) {
				tempFolder = new File(Launch.minecraftHome, "CLASSLOADER_TEMP" + x++);
			}

			if (tempFolder.exists()) {
				LogWrapper.info("DEBUG_SAVE enabled, but 10 temp directories already exist, clean them and try again.");
				tempFolder = null;
			} else {
				LogWrapper.info("DEBUG_SAVE Enabled, saving all classes to \"%s\"", tempFolder.getAbsolutePath().replace('\\', '/'));
				tempFolder.mkdirs();
			}
		}
	}

	public void registerTransformer(String transformerClassName) {
		try {
			IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName).newInstance();
			// MP start
			if (transformer instanceof IClassNameTransformer && renameTransformer == null) {
				renameTransformer = (IClassNameTransformer) transformer;
			}
			if (transformerClassName.equals("cpw.mods.fml.common.asm.transformers.DeobfuscationTransformer")) {
				deobfuscationTransformer = transformer;
				ArrayList<IClassTransformer> oldTransformersList = new ArrayList<IClassTransformer>(transformers);
				transformers.clear();
				IClassTransformer eventTransformer = null;
				for (IClassTransformer transformer_ : oldTransformersList) {
					if (transformer_.getClass().getName().equals("net.minecraftforge.transformers.EventTransformer")) {
						eventTransformer = transformer_;
					} else {
						transformers.add(transformer_);
					}
				}
				transformers.add(transformer);
				if (eventTransformer == null) {
					FMLRelaunchLog.severe("Failed to find event transformer.");
				} else {
					transformers.add(eventTransformer);
				}
			} else {
				transformers.add(transformer);
			}
			// MP end
		} catch (Exception e) {
			LogWrapper.log(Level.ERROR, e, "A critical problem occurred registering the ASM transformer class %s", transformerClassName);
		}
	}

	@Override
	public Class<?> findClass(final String name) throws ClassNotFoundException {
		if (invalidClasses.contains(name)) {
			throw new ClassNotFoundException(name);
		}

		// MP start
		if (excluded(name)) {
			return parent.loadClass(name);
		}

		Class alreadyLoaded = null;
		try {
			alreadyLoaded = (Class) findLoaded.invoke(parent, name);
		} catch (Throwable t) {
			LogWrapper.log(Level.ERROR, t, "");
		}

		if (alreadyLoaded != null) {
			if (name.startsWith("nallar.") && !name.startsWith("nallar.tickthreading.util")) {
				if (!name.startsWith("nallar.log.")) {
					LogWrapper.log(Level.ERROR, new Error(), "Already classloaded earlier: " + name);
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ignored) {
					}
					throw new InternalError("Classloading failure");
				}
				return alreadyLoaded;
			}
		}

		Class<?> cached = cachedClasses.get(name);
		if (cached != null) {
			return cached;
		}

		for (final String exception : transformerExceptions) {
			if (name.startsWith(exception)) {
				try {
					final Class<?> clazz = super.findClass(name);
					if (clazz == null) {
						throw new ClassNotFoundException("null from super.findClass");
					}
					cachedClasses.put(name, clazz);
					return clazz;
				} catch (ClassNotFoundException e) {
					invalidClasses.add(name);
					throw e;
				}
			}
		}

		try {
			final String transformedName = transformName(name);
			if (!transformedName.equals(name)) {
				FMLRelaunchLog.severe("Asked for " + name + ", giving " + transformedName);
				cached = cachedClasses.get(transformedName);
				if (cached != null) {
					return cached;
				}
			}

			final String untransformedName = untransformName(name);

			final int lastDot = untransformedName.lastIndexOf('.');
			final String packageName = lastDot == -1 ? "" : untransformedName.substring(0, lastDot);
			final String fileName = untransformedName.replace('.', '/') + ".class";
			URLConnection urlConnection = findCodeSourceConnectionFor(fileName);

			CodeSigner[] signers = null;

			byte[] classBytes = null;
			if (lastDot > -1 && !untransformedName.startsWith("net.minecraft.")) {
				if (urlConnection instanceof JarURLConnection) {
					final JarURLConnection jarURLConnection = (JarURLConnection) urlConnection;
					final JarFile jarFile = jarURLConnection.getJarFile();

					if (jarFile != null && jarFile.getManifest() != null) {
						final Manifest manifest = jarFile.getManifest();
						final JarEntry entry = jarFile.getJarEntry(fileName);

						Package pkg = getPackage(packageName);
						classBytes = getClassBytes(untransformedName);
						signers = entry.getCodeSigners();
						if (pkg == null) {
							definePackage(packageName, manifest, jarURLConnection.getJarFileURL());
						} else {
							if (pkg.isSealed() && !pkg.isSealed(jarURLConnection.getJarFileURL())) {
								LogWrapper.severe("The jar file %s is trying to seal already secured path %s", jarFile.getName(), packageName);
							} else if (isSealed(packageName, manifest)) {
								LogWrapper.severe("The jar file %s has a security seal for path %s, but that path is defined and not secure", jarFile.getName(), packageName);
							}
						}
					}
				} else {
					Package pkg = getPackage(packageName);
					if (pkg == null) {
						definePackage(packageName, null, null, null, null, null, null, null);
					} else if (pkg.isSealed()) {
						LogWrapper.severe("The URL %s is defining elements for sealed path %s", urlConnection.getURL(), packageName);
					}
				}
			}

			if (classBytes == null) {
				classBytes = getClassBytes(untransformedName);
			}

			final byte[] transformedClass = runTransformers(untransformedName, transformedName, classBytes);
			// MP end
			if (DEBUG_SAVE) {
				saveTransformedClass(transformedClass, transformedName);
			}

			final CodeSource codeSource = urlConnection == null ? null : new CodeSource(urlConnection.getURL(), signers);
			final Class<?> clazz = defineClass(transformedName, transformedClass, 0, transformedClass.length, codeSource);
			cachedClasses.put(transformedName, clazz);
			return clazz;
		} catch (Throwable e) {
			invalidClasses.add(name);
			if (DEBUG) {
				LogWrapper.log(Level.TRACE, e, "Exception encountered attempting classloading of %s", name);
				LogManager.getLogger("LaunchWrapper").log(Level.ERROR, "Exception encountered attempting classloading of %s", e);
			}
			throw new ClassNotFoundException(name, e);
		}
	}

	private void saveTransformedClass(final byte[] data, final String transformedName) {
		if (tempFolder == null) {
			return;
		}

		final File outFile = new File(tempFolder, transformedName.replace('.', File.separatorChar) + ".class");
		final File outDir = outFile.getParentFile();

		if (!outDir.exists()) {
			outDir.mkdirs();
		}

		if (outFile.exists()) {
			outFile.delete();
		}

		try {
			LogWrapper.fine("Saving transformed class \"%s\" to \"%s\"", transformedName, outFile.getAbsolutePath().replace('\\', '/'));

			final OutputStream output = new FileOutputStream(outFile);
			output.write(data);
			output.close();
		} catch (IOException ex) {
			LogWrapper.log(Level.WARN, ex, "Could not save transformed class \"%s\"", transformedName);
		}
	}

	private String untransformName(final String name) {
		if (renameTransformer != null) {
			return renameTransformer.unmapClassName(name);
		}

		return name;
	}

	private String transformName(final String name) {
		if (renameTransformer != null) {
			return renameTransformer.remapClassName(name);
		}

		return name;
	}

	private boolean isSealed(final String path, final Manifest manifest) {
		Attributes attributes = manifest.getAttributes(path);
		String sealed = null;
		if (attributes != null) {
			sealed = attributes.getValue(Name.SEALED);
		}

		if (sealed == null) {
			attributes = manifest.getMainAttributes();
			if (attributes != null) {
				sealed = attributes.getValue(Name.SEALED);
			}
		}
		return "true".equalsIgnoreCase(sealed);
	}

	private URLConnection findCodeSourceConnectionFor(final String name) {
		final URL resource = findResource(name);
		if (resource != null) {
			try {
				return resource.openConnection();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return null;
	}

	private byte[] runTransformers(final String name, final String transformedName, byte[] basicClass) {
		// MP start
		mpPatchInit();
		basicClass = PatchHook.preSrgTransformationHook(name, transformedName, basicClass);
		if (deobfuscationTransformer == null) {
			if (transformedName.startsWith("net.minecraft.") && !transformedName.contains("ClientBrandRetriever")) {
				Log.severe("Transforming " + name + " before SRG transformer has been added.", new Throwable());
			}
			if (PatchHook.requiresSrgHook(transformedName)) {
				Log.severe("Class " + name + " must be transformed postSrg, but the SRG transformer has not been added to the classloader.", new Throwable());
			}
			for (final IClassTransformer transformer : transformers) {
				basicClass = runTransformer(name, transformedName, basicClass, transformer);
			}
			return basicClass;
		}
		basicClass = transformUpToSrg(name, transformedName, basicClass);
		basicClass = PatchHook.postSrgTransformationHook(name, transformedName, basicClass);
		basicClass = transformAfterSrg(name, transformedName, basicClass);
		return basicClass;
		// MP end
	}

	@Override
	public void addURL(final URL url) {
		super.addURL(url);
		sources.add(url);
	}

	public List<URL> getSources() {
		return sources;
	}

	private byte[] readFully(InputStream stream) {
		try {
			byte[] buffer = getOrCreateBuffer();

			int read;
			int totalLength = 0;
			while ((read = stream.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
				totalLength += read;

				// Extend our buffer
				if (totalLength >= buffer.length - 1) {
					byte[] newBuffer = new byte[buffer.length + BUFFER_SIZE];
					System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
					buffer = newBuffer;
				}
			}

			final byte[] result = new byte[totalLength];
			System.arraycopy(buffer, 0, result, 0, totalLength);
			return result;
		} catch (Throwable t) {
			LogWrapper.log(Level.WARN, t, "Problem loading class");
			return new byte[0];
		}
	}

	private byte[] getOrCreateBuffer() {
		byte[] buffer = loadBuffer.get();
		if (buffer == null) {
			loadBuffer.set(new byte[BUFFER_SIZE]);
			buffer = loadBuffer.get();
		}
		return buffer;
	}

	public List<IClassTransformer> getTransformers() {
		return Collections.unmodifiableList(transformers);
	}

	public void addClassLoaderExclusion(String toExclude) {
		classLoaderExceptions.add(toExclude);
	}

	public void addTransformerExclusion(String toExclude) {
		transformerExceptions.add(toExclude);
	}

	public byte[] getClassBytes(String name) throws IOException {
		// MP start
		if (name.startsWith("java/")) {
			return null;
		}
		name = name.replace('/', '.');
		byte[] cached = resourceCache.get(name);
		if (cached != null) {
			return cached == CACHE_MISS ? null : cached;
		}
		if (name.indexOf('.') == -1) {
			String upperCaseName = name.toUpperCase(Locale.ENGLISH);
			for (final String reservedName : RESERVED_NAMES) {
				if (upperCaseName.startsWith(reservedName)) {
					final byte[] data = getClassBytes('_' + name);
					if (data != null) {
						resourceCache.put(name, data);
						return data;
					}
				}
			}
		}

		InputStream classStream = null;
		try {
			final String resourcePath = name.replace('.', '/') + ".class";
			final URL classResource = findResource(resourcePath);

			if (classResource == null) {
				if (DEBUG) LogWrapper.finest("Failed to find class resource %s", resourcePath);
				resourceCache.put(name, CACHE_MISS);
				return null;
			}
			classStream = classResource.openStream();

			if (DEBUG) LogWrapper.finest("Loading class %s from resource %s", name, classResource.toString());
			final byte[] data = readFully(classStream);
			resourceCache.put(name, data);
			return data;
		} finally {
			closeSilently(classStream);
		}
		// MP end
	}

	private static void closeSilently(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ignored) {
			}
		}
	}

	public void clearNegativeEntries(Set<String> entriesToClear) {
		// MP start
		for (String entry : entriesToClear) {
			entry = entry.replace('/', '.');
			if (resourceCache.get(entry) == CACHE_MISS) {
				resourceCache.remove(entry);
			}
		}
		// MP end
	}
}
