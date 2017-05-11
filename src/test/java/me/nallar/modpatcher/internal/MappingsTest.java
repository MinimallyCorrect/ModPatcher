package me.nallar.modpatcher.internal;

import LZMA.LzmaInputStream;
import lombok.SneakyThrows;
import org.junit.Test;

import java.io.*;
import java.util.*;

public class MappingsTest {
	@SneakyThrows
	@Test
	public void testMappings() {
		MCPMappings.loadExtends(new LzmaInputStream(new FileInputStream("./generated/extendsMap.obj.lzma")), new HashMap<>());
		MCPMappings.loadCsv(new LzmaInputStream(new FileInputStream("./generated/fields.csv.lzma")), new HashMap<>());
		MCPMappings.loadCsv(new LzmaInputStream(new FileInputStream("./generated/methods.csv.lzma")), new HashMap<>());
	}
}
