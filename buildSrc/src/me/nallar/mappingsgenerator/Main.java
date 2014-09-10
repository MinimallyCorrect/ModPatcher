package me.nallar.mappingsgenerator;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.jar.*;

public class Main {
	/**
	 * @param jar    File
	 * @param source if TRUE source, if FALSE binary
	 */
	public static void generateMappings(File jar, boolean source) throws Exception {
		try {
			if (source) {
				generateMappingsSource(jar);
			} else {
				generateMappingsBinary(jar);
			}
		} catch (Throwable t) {
			t.printStackTrace();
			Throwables.propagate(t);
		}
	}

	private static final HashMap<String, String> classExtends = new HashMap<String, String>();

	public static void addClassToExtendsMap(byte[] inputCode) {
		ClassReader classReader = new ClassReader(inputCode);
		ClassNode classNode = new ClassNode();
		classReader.accept(classNode, 0);
		String superName = classNode.superName.replace("/", ".");
		if (superName != null && !superName.equals("java.lang.Object")) {
			classExtends.put(classNode.name.replace("/", "."), superName);
		}
	}

	public static void generateMappingsBinary(File jar) throws Exception {
		// READING
		JarInputStream istream = new JarInputStream(new FileInputStream(jar));
		JarEntry entry;
		while ((entry = istream.getNextJarEntry()) != null) {
			byte[] classBytes = ByteStreams.toByteArray(istream);
			if (entry.getName().endsWith(".class")) {
				// PARSING
				addClassToExtendsMap(classBytes);
			}

			istream.closeEntry();
		}
		istream.close();

		File generatedDirectory = new File("./generated/");
		generatedDirectory = generatedDirectory.getCanonicalFile();

		if (!generatedDirectory.exists()) {
			generatedDirectory.mkdir();
		}
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(new File(generatedDirectory, "extendsMap.obj")));
		try {
			objectOutputStream.writeObject(classExtends);
		} finally {
			objectOutputStream.close();
		}

	}

	public static void generateMappingsSource(File directory) throws Exception {
		System.out.println("Source dir " + directory);
		File generatedDirectory = new File("./generated/");
		generatedDirectory = generatedDirectory.getCanonicalFile();
		final File generatedSrcDirectory = new File(generatedDirectory, "src");

		if (generatedSrcDirectory.exists()) {
			deleteDirectory(generatedSrcDirectory.toPath());
		}
		generatedSrcDirectory.mkdirs();

		final File mainSrcDirectory = new File("./src/main/java/");
		directory = directory.getCanonicalFile();
		final int cutoff = directory.toString().length();
		System.out.println(directory.toPath());
		java.nio.file.Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
					throws IOException {
				String name = path.getFileName().toString();
				if (!name.endsWith(".java")) {
					return FileVisitResult.CONTINUE;
				}
				String fullPath = path.toFile().getCanonicalFile().toString();
				String part = fullPath.substring(cutoff);
				if (new File(mainSrcDirectory, part).exists()) {
					return FileVisitResult.CONTINUE;
				}
				File dest = new File(generatedSrcDirectory, part);
				dest.getParentFile().mkdirs();
				java.nio.file.Files.copy(path, dest.toPath());
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc == null) {
					return FileVisitResult.CONTINUE;
				} else {
					// directory iteration failed; propagate exception
					throw exc;
				}
			}
		});
	}

	public static void deleteDirectory(Path path) throws IOException {
		java.nio.file.Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				java.nio.file.Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				// try to delete the file anyway, even if its attributes
				// could not be read, since delete-only access is
				// theoretically possible
				java.nio.file.Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc == null) {
					java.nio.file.Files.delete(dir);
					return FileVisitResult.CONTINUE;
				} else {
					// directory iteration failed; propagate exception
					throw exc;
				}
			}
		});
	}
}
