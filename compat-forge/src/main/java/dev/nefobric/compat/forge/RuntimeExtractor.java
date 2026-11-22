package dev.nefobric.compat.forge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 把内嵌在兼容层 jar 里的运行库解到缓存目录。清单里每行一个相对 {@code META-INF/nefobric/} 的路径；
 * 每个文件按内容的 sha1 放进子目录，所以升级 NeFobric 后不会用到旧文件，同一版本也不会重复写。
 */
final class RuntimeExtractor {
    private static final String BASE = "META-INF/nefobric/";

    private RuntimeExtractor() {
    }

    static List<Path> extract(ClassLoader from, String listResource, Path targetDir) throws IOException {
        byte[] list = read(from, listResource);
        List<Path> out = new ArrayList<>();
        for (String line : new String(list, StandardCharsets.UTF_8).split("\\R")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            byte[] data = read(from, BASE + entry);
            String name = entry.substring(entry.lastIndexOf('/') + 1);
            Path file = targetDir.resolve(sha1(data).substring(0, 12)).resolve(name);
            if (!Files.isRegularFile(file) || Files.size(file) != data.length) {
                Files.createDirectories(file.getParent());
                Path tmp = Files.createTempFile(file.getParent(), name, ".tmp");
                try {
                    Files.write(tmp, data);
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            out.add(file);
        }
        return out;
    }

    private static byte[] read(ClassLoader from, String resource) throws IOException {
        try (InputStream in = from.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing " + resource);
            }
            return in.readAllBytes();
        }
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
