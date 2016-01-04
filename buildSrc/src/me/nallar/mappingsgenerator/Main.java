package me.nallar.mappingsgenerator;

import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.jar.*;

public class Main {
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

	public static void generateMappings(File jar) throws Exception {
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

	public static void extractGeneratedSources(File jar) throws Exception {
		System.out.println("Source dir " + jar);
		File generatedDirectory = new File("./generated/");
		generatedDirectory = generatedDirectory.getCanonicalFile();
		final File generatedSrcDirectory = new File(generatedDirectory, "src");

		if (generatedSrcDirectory.exists()) {
			deleteDirectory(generatedSrcDirectory.toPath());
		}
		generatedSrcDirectory.mkdirs();

		final File mainSrcDirectory = new File("./src/main/java/");
		jar = jar.getCanonicalFile();

		JarInputStream istream = new JarInputStream(new FileInputStream(jar));
		JarEntry entry;
		while ((entry = istream.getNextJarEntry()) != null) {
			String part = entry.getName();
			byte[] sourceBytes = ByteStreams.toByteArray(istream);
			if (part.endsWith(".java")) {
				// Source file
				if (new File(mainSrcDirectory, part).exists()) {
					continue;
				}
				File dest = new File(generatedSrcDirectory, part);
				dest.getParentFile().mkdirs();
				Files.write(dest.toPath(), sourceBytes);
			}

			istream.closeEntry();
		}
		istream.close();
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
