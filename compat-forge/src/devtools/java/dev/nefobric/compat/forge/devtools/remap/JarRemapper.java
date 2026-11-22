package dev.nefobric.compat.forge.devtools.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 重映射整个 jar：类改名、成员改名，资源原样复制；签名文件去掉（改过的类签名对不上）。目录条目按改名后的路径重建，
 * 清单放在最前面（{@code JarInputStream} 只在开头找它）。
 */
public final class JarRemapper {
    private JarRemapper() {
    }

    /** @return 处理的类数 */
    public static int remap(Path in, Path out, Remapper remapper) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        int classes = 0;
        try (InputStream is = Files.newInputStream(in); ZipInputStream zin = new ZipInputStream(is)) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                if (e.isDirectory() || isSignature(e.getName())) {
                    continue;
                }
                byte[] data = zin.readAllBytes();
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                    ClassReader reader = new ClassReader(data);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, remapper), 0);
                    entries.put(remapper.map(reader.getClassName()) + ".class", writer.toByteArray());
                    classes++;
                } else {
                    entries.put(e.getName(), data);
                }
            }
        }
        for (String name : List.copyOf(entries.keySet())) {
            for (int i = name.indexOf('/'); i >= 0; i = name.indexOf('/', i + 1)) {
                entries.putIfAbsent(name.substring(0, i + 1), null);
            }
        }
        Set<String> order = new LinkedHashSet<>();
        for (String first : List.of("META-INF/", "META-INF/MANIFEST.MF")) {
            if (entries.containsKey(first)) {
                order.add(first);
            }
        }
        order.addAll(entries.keySet());
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = Files.createTempFile(out.toAbsolutePath().getParent(), out.getFileName().toString(), ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zout = new ZipOutputStream(os)) {
            for (String name : order) {
                ZipEntry ze = new ZipEntry(name);
                ze.setTime(0L);
                zout.putNextEntry(ze);
                byte[] data = entries.get(name);
                if (data != null) {
                    zout.write(data);
                }
                zout.closeEntry();
            }
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return classes;
    }

    private static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', 9) >= 0) {
            return false;
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
    }
}
