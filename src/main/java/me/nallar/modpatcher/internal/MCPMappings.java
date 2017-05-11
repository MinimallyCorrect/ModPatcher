package me.nallar.modpatcher.internal;

import LZMA.LzmaInputStream;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.SneakyThrows;
import lombok.val;
import me.nallar.javapatcher.mappings.ClassDescription;
import me.nallar.javapatcher.mappings.FieldDescription;
import me.nallar.javapatcher.mappings.Mappings;
import me.nallar.javapatcher.mappings.MethodDescription;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import java.io.*;
import java.util.*;
import java.util.regex.*;

class MCPMappings extends Mappings {
	private static final Pattern classObfuscatePattern = Pattern.compile("\\^class:([^\\^]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern methodObfuscatePattern = Pattern.compile("\\^method:([^\\^/]+)/([^\\^/]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern fieldObfuscatePattern = Pattern.compile("\\^field:([^\\^/]+)/([^\\^/]+)\\^", Pattern.DOTALL | Pattern.MULTILINE);
	private final Map<String, String> methodSeargeMappings = new HashMap<>();
	private final Map<String, String> fieldSeargeMappings = new HashMap<>();
	private final BiMap<ClassDescription, ClassDescription> classMappings = HashBiMap.create();
	private final BiMap<MethodDescription, MethodDescription> methodMappings = HashBiMap.create();
	private final BiMap<MethodDescription, MethodDescription> methodSrgMappings = HashBiMap.create();
	private final Map<FieldDescription, FieldDescription> fieldSrgMappings = new HashMap<>();
	private final Map<String, MethodDescription> parameterlessSrgMethodMappings = new HashMap<>();
	private final Map<String, String> shortClassNameToFullName = new HashMap<>();
	private final Map<String, List<String>> extendsMap = new HashMap<>();

	@SneakyThrows
	MCPMappings() {
		try {
			loadExtends(new LzmaInputStream(Mappings.class.getResourceAsStream("/extendsMap.obj.lzma")), extendsMap);
			loadCsv(new LzmaInputStream(Mappings.class.getResourceAsStream("/methods.csv.lzma")), methodSeargeMappings);
			loadCsv(new LzmaInputStream(Mappings.class.getResourceAsStream("/fields.csv.lzma")), fieldSeargeMappings);
			loadSrg(new LzmaInputStream(FMLInjectionData.class.getResourceAsStream("/deobfuscation_data-" + FMLInjectionData.data()[4] + ".lzma")));
		} catch (Exception e) {
			PatcherLog.error("Failed to load MCP mappings", e);
		}
		methodSeargeMappings.clear();
		fieldSeargeMappings.clear();
	}

	static void loadCsv(InputStream mappingsCsv, Map<String, String> seargeMappings) throws IOException {
		try (Scanner in = new Scanner(mappingsCsv)) {
			in.useDelimiter(",");
			while (in.hasNextLine()) {
				String seargeName = in.next();
				String name = in.next();
				String side = in.next();
				in.nextLine();
				if ("2".equals(side) || "0".equals(side)) { // 2 = joined 'side'.
					seargeMappings.put(seargeName, name);
				}
			}
		}
		mappingsCsv.close();
	}

	static void loadExtends(InputStream resourceAsStream, Map<String, List<String>> extendsMap) throws IOException {
		try (val reader = new BufferedReader(new InputStreamReader(resourceAsStream))) {
			while (true) {
				val line = reader.readLine();
				if (line == null)
					break;
				if (line.isEmpty())
					continue;

				val split = line.indexOf('^');
				if (split == -1)
					continue;

				val a = line.substring(0, split);
				val b = line.substring(split + 1);

				extendsMap.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
			}
		}
	}

	@Override
	public MethodDescription map(MethodDescription methodDescription) {
		MethodDescription obfuscated = methodSrgMappings.get(methodDescription);
		if (obfuscated == null) {
			obfuscated = parameterlessSrgMethodMappings.get(methodDescription.getShortName());
			if (methodDescription.isExact() || obfuscated == null) {
				obfuscated = methodDescription;
				obfuscated.obfuscateClasses();
			}
		}
		return obfuscated;
	}

	@Override
	public MethodDescription unmap(MethodDescription methodDescription) {
		return methodSrgMappings.inverse().get(methodDescription);
	}

	private String shortClassNameToFullClassName(String shortName) {
		return shortClassNameToFullName.get(shortName);
	}

	@Override
	public ClassDescription map(ClassDescription classDescription) {
		return classDescription;
	}

	@Override
	public FieldDescription map(FieldDescription fieldDescription) {
		return fieldSrgMappings.get(fieldDescription);
	}

	private String classStringToClassName(String name) {
		String mapped = shortClassNameToFullClassName(name);
		if (mapped != null) {
			name = mapped;
		}
		return map(new ClassDescription(name)).name;
	}

	@Override
	public String obfuscate(String code) {
		StringBuffer result = new StringBuffer();

		{
			Matcher methodMatcher = methodObfuscatePattern.matcher(code);
			while (methodMatcher.find()) {
				String className = shortClassNameToFullClassName(methodMatcher.group(1));
				String methodDescriptionString = methodMatcher.group(2);
				if (className == null) {
					className = methodMatcher.group(1);
					if (!className.contains(".")) {
						PatcherLog.error("Could not find " + methodMatcher.group(1));
						continue;
					}
				}
				MethodDescription methodDescription = MethodDescription.fromString(className, methodDescriptionString);
				MethodDescription mapped = map(methodDescription);
				methodMatcher.appendReplacement(result, mapped.name);
			}
			methodMatcher.appendTail(result);
		}

		{
			Matcher fieldMatcher = fieldObfuscatePattern.matcher(result);
			result = new StringBuffer();
			while (fieldMatcher.find()) {
				String className = shortClassNameToFullClassName(fieldMatcher.group(1));
				String fieldName = fieldMatcher.group(2);
				if (className == null) {
					className = fieldMatcher.group(1);
					if (!className.contains(".")) {
						PatcherLog.error("Could not find " + fieldMatcher.group(1));
						continue;
					}
				}
				FieldDescription fieldDescription = new FieldDescription(className, fieldName);
				FieldDescription mapped = map(fieldDescription);
				if (mapped == null) {
					PatcherLog.error("Could not map " + fieldName);
					fieldMatcher.appendReplacement(result, fieldName);
				} else {
					fieldMatcher.appendReplacement(result, mapped.name);
				}
			}
			fieldMatcher.appendTail(result);
		}

		{
			Matcher classMatcher = classObfuscatePattern.matcher(result);
			result = new StringBuffer();
			while (classMatcher.find()) {
				String className = classStringToClassName(classMatcher.group(1));
				if (className == null) {
					PatcherLog.error("Could not find " + classMatcher.group(1));
					continue;
				}
				classMatcher.appendReplacement(result, className);
			}
			classMatcher.appendTail(result);
		}

		return result.toString();
	}

	private void loadSrg(InputStream mappings) throws IOException {
		Scanner srgScanner = new Scanner(mappings);
		while (srgScanner.hasNextLine()) {
			if (srgScanner.hasNext("CL:")) {
				srgScanner.next();
				String fromClass = srgScanner.next().replace('/', '.');
				String toClass = srgScanner.next().replace('/', '.');
				ClassDescription obfuscatedClass = new ClassDescription(fromClass);
				ClassDescription deobfuscatedClass = new ClassDescription(toClass);
				shortClassNameToFullName.put(deobfuscatedClass.name.substring(deobfuscatedClass.name.lastIndexOf('.') + 1), deobfuscatedClass.name);
				classMappings.put(deobfuscatedClass, obfuscatedClass);
			} else if (srgScanner.hasNext("FD:")) {
				srgScanner.next();
				String obfuscatedMCPName = srgScanner.next();
				String seargeName = srgScanner.next();
				seargeName = seargeName.substring(seargeName.lastIndexOf('/') + 1);
				String deobfuscatedName = fieldSeargeMappings.get(seargeName);
				if (deobfuscatedName == null) {
					deobfuscatedName = seargeName;
				}
				FieldDescription obfuscatedField = new FieldDescription(obfuscatedMCPName);
				FieldDescription deobfuscatedField = new FieldDescription(classMappings.inverse().get(new ClassDescription(obfuscatedField.className)).name, deobfuscatedName);
				FieldDescription srgField = new FieldDescription(deobfuscatedField.className, seargeName);
				fieldSrgMappings.put(deobfuscatedField, srgField);
				recursiveExtendFieldMappings(deobfuscatedField, srgField);
			} else if (srgScanner.hasNext("MD:")) {
				srgScanner.next();
				String obfuscatedName = srgScanner.next();
				String obfuscatedTypeInfo = srgScanner.next();
				String seargeName = srgScanner.next();
				String deobfuscatedTypeInfo = srgScanner.next();
				String obfuscatedClassName = obfuscatedName.substring(0, obfuscatedName.lastIndexOf('/')).replace('/', '.');
				obfuscatedName = obfuscatedName.substring(obfuscatedName.lastIndexOf('/') + 1);
				String deobfuscatedClassName = seargeName.substring(0, seargeName.lastIndexOf('/')).replace('/', '.');
				seargeName = seargeName.substring(seargeName.lastIndexOf('/') + 1);
				String deobfuscatedName = methodSeargeMappings.get(seargeName);
				if (deobfuscatedName == null) {
					deobfuscatedName = seargeName;
				}
				MethodDescription deobfuscatedMethodDescription = new MethodDescription(deobfuscatedClassName, deobfuscatedName, deobfuscatedTypeInfo);
				MethodDescription obfuscatedMethodDescription = new MethodDescription(obfuscatedClassName, obfuscatedName, obfuscatedTypeInfo);
				MethodDescription srgMethodDescription = new MethodDescription(deobfuscatedClassName, seargeName, deobfuscatedTypeInfo);
				methodMappings.put(deobfuscatedMethodDescription, obfuscatedMethodDescription);
				methodSrgMappings.put(deobfuscatedMethodDescription, srgMethodDescription);
				parameterlessSrgMethodMappings.put(deobfuscatedMethodDescription.getShortName(), srgMethodDescription);
			} else {
				srgScanner.nextLine();
			}
		}
		mappings.close();
	}

	private void recursiveExtendFieldMappings(FieldDescription deobfuscatedField, FieldDescription srgField) {
		List<String> extendedBy = extendsMap.get(deobfuscatedField.className);
		if (extendedBy == null) {
			return;
		}
		for (String className : extendedBy) {
			FieldDescription newDeobf = new FieldDescription(className, deobfuscatedField.name);
			FieldDescription newSrgField = new FieldDescription(className, srgField.name);
			fieldSrgMappings.put(newDeobf, newSrgField);
			recursiveExtendFieldMappings(newDeobf, newSrgField);
		}
	}
}
