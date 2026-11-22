package dev.nefobric.loader.discovery;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 只读打开的模组 jar，给各兼容层读取元数据和扫描类用。 */
public final class ModJar implements Closeable {
    private final Path path;
    private final ZipFile zip;
    private Manifest manifest;
    private boolean manifestRead;

    private ModJar(Path path, ZipFile zip) {
        this.path = path;
        this.zip = zip;
    }

    public static ModJar open(Path path) throws IOException {
        return new ModJar(path, new ZipFile(path.toFile()));
    }

    public Path path() {
        return path;
    }

    public String fileName() {
        return String.valueOf(path.getFileName());
    }

    public boolean has(String entry) {
        return zip.getEntry(entry) != null;
    }

    public Optional<byte[]> read(String entry) throws IOException {
        ZipEntry e = zip.getEntry(entry);
        if (e == null || e.isDirectory()) {
            return Optional.empty();
        }
        try (InputStream in = zip.getInputStream(e)) {
            return Optional.of(in.readAllBytes());
        }
    }

    public Optional<String> readString(String entry) throws IOException {
        return read(entry).map(b -> new String(b, StandardCharsets.UTF_8));
    }

    /** jar 的 MANIFEST.MF；没有时返回 {@code null}。 */
    public Manifest manifest() throws IOException {
        if (!manifestRead) {
            manifestRead = true;
            Optional<byte[]> bytes = read("META-INF/MANIFEST.MF");
            if (bytes.isPresent()) {
                manifest = new Manifest(new java.io.ByteArrayInputStream(bytes.get()));
            }
        }
        return manifest;
    }

    /** 直接位于 {@code dir}（以 / 结尾）下、以 {@code suffix} 结尾的文件条目名，不含子目录里的。 */
    public List<String> entries(String dir, String suffix) {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (!e.isDirectory() && name.startsWith(dir) && name.endsWith(suffix)
                    && name.indexOf('/', dir.length()) < 0) {
                out.add(name);
            }
        }
        out.sort(null);
        return out;
    }

    /** jar 里所有普通类文件的条目名（不含 module-info 和多版本目录）。 */
    public List<String> classEntries() {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (!e.isDirectory() && name.endsWith(".class") && !name.startsWith("META-INF/")
                    && !name.endsWith("module-info.class")) {
                out.add(name);
            }
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
