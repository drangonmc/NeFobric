package dev.nefobric.loader.launch;

import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 1.18 起原版 {@code server.jar} 是一个 “bundler”：真正的服务端 jar 和依赖库都打包在 {@code META-INF/versions}
 * 和 {@code META-INF/libraries} 下，清单在 {@code versions.list} / {@code libraries.list}（每行：sha256、id、路径）。
 * 这里按原版 bundler 的方式把它们解到服务器目录的 {@code versions/} 和 {@code libraries/}，已存在且校验和相符的文件不再重复解压。
 */
public final class BundlerExtractor {

    public record Extracted(Path gameJar, List<Path> libraries) {
    }

    private record Entry(String sha256, String id, String path) {
    }

    private BundlerExtractor() {
    }

    public static boolean isBundler(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry("META-INF/versions.list") != null;
        }
    }

    public static Extracted extract(Path bundlerJar, Path baseDir) throws IOException {
        try (ZipFile zip = new ZipFile(bundlerJar.toFile())) {
            List<Entry> versions = readList(zip, "META-INF/versions.list");
            List<Entry> libraries = readList(zip, "META-INF/libraries.list");
            if (versions.isEmpty()) {
                throw new NeFobricStartupException(Messages.tr("game.bundler.missingEntry", "versions.list", bundlerJar));
            }
            Path versionsDir = baseDir.resolve("versions");
            Path librariesDir = baseDir.resolve("libraries");
            int written = 0;
            Path gameJar = null;
            for (Entry e : versions) {
                Path target = safeResolve(versionsDir, e.path);
                if (copyIfNeeded(zip, "META-INF/versions/" + e.path, target, e.sha256, bundlerJar)) {
                    written++;
                }
                if (gameJar == null) {
                    gameJar = target;
                }
            }
            List<Path> libs = new ArrayList<>();
            for (Entry e : libraries) {
                Path target = safeResolve(librariesDir, e.path);
                if (copyIfNeeded(zip, "META-INF/libraries/" + e.path, target, e.sha256, bundlerJar)) {
                    written++;
                }
                libs.add(target);
            }
            if (written > 0) {
                Log.info(Messages.tr("game.bundler.extracted", written, bundlerJar.getFileName()));
            }
            return new Extracted(gameJar, libs);
        }
    }

    private static List<Entry> readList(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length != 3) {
                    throw new NeFobricStartupException(Messages.tr("game.bundler.badList", name, line));
                }
                out.add(new Entry(parts[0].trim(), parts[1].trim(), parts[2].trim()));
            }
        }
        return out;
    }

    /** 防止清单里的路径跳出目标目录。 */
    private static Path safeResolve(Path dir, String relative) {
        Path base = dir.toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            throw new NeFobricStartupException(Messages.tr("game.bundler.badList", dir, relative));
        }
        return target;
    }

    private static boolean copyIfNeeded(ZipFile zip, String entryName, Path target, String sha256, Path bundlerJar)
            throws IOException {
        if (Files.isRegularFile(target) && sha256.equalsIgnoreCase(sha256(target))) {
            return false;
        }
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new NeFobricStartupException(Messages.tr("game.bundler.missingEntry", entryName, bundlerJar));
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = sha256(tmp);
        if (!sha256.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(tmp);
            throw new NeFobricStartupException(Messages.tr("game.bundler.badHash", entryName, sha256, actual));
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    static String sha256(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buf = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
