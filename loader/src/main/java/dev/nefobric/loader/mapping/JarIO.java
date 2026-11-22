package dev.nefobric.loader.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 读写整个 jar。写出的 jar 条目时间固定，同样的输入总是得到同样的字节，方便缓存和比对。 */
final class JarIO {
    /** 1980-02-01，zip 能表示的最早时间附近，和 Gradle 的可重现归档一致。 */
    private static final long FIXED_TIME = 318211200000L;

    record Entry(String name, byte[] data) {
        boolean isClass() {
            return name.endsWith(".class") && !name.startsWith("META-INF/versions/")
                    && !name.equals("module-info.class");
        }
    }

    private JarIO() {
    }

    static List<Entry> read(Path jar) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (InputStream is = Files.newInputStream(jar); ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    entries.add(new Entry(e.getName(), zip.readAllBytes()));
                }
            }
        }
        return entries;
    }

    static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', 9) >= 0) {
            return false;
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                || upper.startsWith("META-INF/SIG-");
    }

    /** 先写到同目录的临时文件再改名，进程中途被杀也不会留下半个 jar。 */
    static void write(Path out, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = Files.createTempFile(out.toAbsolutePath().getParent(), out.getFileName().toString(), ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(os)) {
                // MANIFEST 放在最前面，JarInputStream 才认得
                byte[] manifest = entries.get("META-INF/MANIFEST.MF");
                if (manifest != null) {
                    put(zip, "META-INF/MANIFEST.MF", manifest);
                }
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    if (!e.getKey().equals("META-INF/MANIFEST.MF")) {
                        put(zip, e.getKey(), e.getValue());
                    }
                }
            }
            try {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(FIXED_TIME);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }
}
