package dev.nefobric.loader.mapping;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 找到并读取映射文件。默认放在 {@code <游戏目录>/.nefobric/mappings/<版本>/}，可以用
 * {@code -Dnefobric.mappingsDir} 换位置；缺的文件首次启动时从官方地址下载（{@code -Dnefobric.offline=true} 时不下载）。
 *
 * <p>需要三份文件（原样放进去即可，不用解压）：
 * <ul>
 *   <li>Mojang 官方映射：{@code client.txt}（服务端用 {@code server.txt}，没有时也可以用 client.txt）；</li>
 *   <li>MCPConfig：{@code mcp_config-<版本>.zip} 或从中取出的 {@code joined.tsrg}；</li>
 *   <li>Fabric intermediary：{@code intermediary-<版本>-v2.jar} 或从中取出的 {@code mappings.tiny}。</li>
 * </ul>
 * {@code -Dnefobric.mappings=none} 关闭重映射（只用于未混淆的测试游戏）。
 */
public final class MappingStore {
    /** 运行时命名空间的组成：命名空间顺序固定，缓存键也依赖它。 */
    static final List<String> NAMESPACES = List.of(Mappings.OFFICIAL, Mappings.SRG, Mappings.INTERMEDIARY);

    /** 读到的映射和它们的指纹（输入文件内容的 sha1），指纹参与重映射结果的缓存键。 */
    public record Loaded(Mappings mappings, MappingData data, String fingerprint) {
        public static final Loaded NONE = new Loaded(Mappings.identity(), null, "none");

        public boolean isIdentity() {
            return data == null;
        }
    }

    /** 缺少映射文件，且无法下载。 */
    public static final class MissingMappingsException extends Exception {
        public MissingMappingsException(String message) {
            super(message);
        }
    }

    private MappingStore() {
    }

    public static Loaded load(Path gameDir, GameInstall game) throws IOException, MissingMappingsException {
        if ("none".equalsIgnoreCase(System.getProperty("nefobric.mappings", ""))) {
            Log.debug("Remapping disabled by -Dnefobric.mappings=none");
            return Loaded.NONE;
        }
        if (!isObfuscated(game.gameJar())) {
            Log.debug("Game jar is not obfuscated; running without mappings");
            return Loaded.NONE;
        }
        String version = game.version() == null ? "unknown" : game.version();
        Path dir = mappingsDir(gameDir, version);
        Files.createDirectories(dir);

        Optional<Path> mojang = findMojang(dir, game.env());
        Optional<Path> tsrg = find(dir, "joined.tsrg", "mcp_config-", ".zip");
        Optional<Path> tiny = find(dir, "mappings.tiny", "intermediary-", ".jar");
        boolean offline = Boolean.getBoolean("nefobric.offline");
        List<String> missing = new ArrayList<>();
        if (mojang.isEmpty()) {
            mojang = offline ? Optional.empty() : MappingDownloader.mojang(dir, version, game.env());
            if (mojang.isEmpty()) {
                missing.add(game.env() == Env.SERVER ? "server.txt" : "client.txt");
            }
        }
        if (tsrg.isEmpty()) {
            tsrg = offline ? Optional.empty() : MappingDownloader.mcpConfig(dir, version);
            if (tsrg.isEmpty()) {
                missing.add("mcp_config-" + MappingDownloader.mcpConfigVersion(version) + ".zip");
            }
        }
        if (tiny.isEmpty()) {
            tiny = offline ? Optional.empty() : MappingDownloader.intermediary(dir, version);
            if (tiny.isEmpty()) {
                missing.add("intermediary-" + version + "-v2.jar");
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingMappingsException(Messages.tr("mappings.missing", version, dir, String.join(", ", missing)));
        }

        long start = System.nanoTime();
        MappingData.Builder b = MappingData.builder(NAMESPACES);
        MessageDigest digest = sha1();
        int mojangClasses;
        try (BufferedReader r = open(mojang.get(), null, digest)) {
            mojangClasses = MappingReaders.readProguardClasses(r, b, Mappings.SRG);
        }
        int srgMembers;
        try (BufferedReader r = open(tsrg.get(), "config/joined.tsrg", digest)) {
            srgMembers = MappingReaders.readTsrg(r, b, mojangClasses == 0 ? Mappings.SRG : null, Mappings.SRG);
        }
        try (BufferedReader r = open(tiny.get(), "mappings/mappings.tiny", digest)) {
            MappingReaders.readTiny2(r, b, Mappings.INTERMEDIARY, Mappings.INTERMEDIARY);
        }
        MappingData data = b.build(Mappings.SRG);
        String fingerprint = HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        long ms = (System.nanoTime() - start) / 1_000_000;
        Log.info(Messages.tr("mappings.loaded", version, data.classCount(), data.fieldCount(), data.methodCount(), ms));
        Log.debug("Mapping files: " + mojang.get() + ", " + tsrg.get() + ", " + tiny.get() + " (" + mojangClasses
                + " Mojang class names, " + srgMembers + " SRG members, " + data.uniqueConflicts()
                + " unique-name conflicts, fingerprint " + fingerprint + ")");
        return new Loaded(data, data, fingerprint);
    }

    public static Path mappingsDir(Path gameDir, String version) {
        String override = System.getProperty("nefobric.mappingsDir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        return gameDir.resolve(".nefobric").resolve("mappings").resolve(version);
    }

    /** 原版 jar 的类大多在默认包里（{@code a.class}、{@code enn.class}）；未混淆的 jar 没有。 */
    static boolean isObfuscated(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            int rootClasses = 0;
            for (var it = zip.entries(); it.hasMoreElements(); ) {
                String n = it.nextElement().getName();
                if (n.endsWith(".class") && n.indexOf('/') < 0 && ++rootClasses >= 50) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Optional<Path> findMojang(Path dir, Env env) throws IOException {
        List<String> names = env == Env.SERVER ? List.of("server.txt", "server_mappings.txt", "client.txt",
                "client_mappings.txt") : List.of("client.txt", "client_mappings.txt");
        for (String n : names) {
            Path p = dir.resolve(n);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** 先找取出来的文件名，再找原始归档（前缀 + 后缀，取文件名最大的一个，也就是最新版本）。 */
    private static Optional<Path> find(Path dir, String plain, String prefix, String suffix) throws IOException {
        Path p = dir.resolve(plain);
        if (Files.isRegularFile(p)) {
            return Optional.of(p);
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(f -> {
                        String n = f.getFileName().toString();
                        return n.startsWith(prefix) && n.endsWith(suffix);
                    })
                    .max((a, c) -> a.getFileName().toString().compareTo(c.getFileName().toString()));
        }
    }

    /** 打开文本文件，或 zip/jar 里的某个条目；读到的内容同时计入指纹。 */
    private static BufferedReader open(Path file, String entry, MessageDigest digest) throws IOException {
        byte[] bytes;
        String n = file.getFileName().toString();
        if (entry != null && (n.endsWith(".zip") || n.endsWith(".jar"))) {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                ZipEntry e = zip.getEntry(entry);
                if (e == null) {
                    throw new IOException(file + " does not contain " + entry);
                }
                try (InputStream in = zip.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
            }
        } else {
            bytes = Files.readAllBytes(file);
        }
        digest.update(bytes);
        return new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8));
    }

    static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha1(Path file) throws IOException {
        MessageDigest d = sha1();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) {
                d.update(buf, 0, r);
            }
        }
        return HexFormat.of().formatHex(d.digest());
    }
}
