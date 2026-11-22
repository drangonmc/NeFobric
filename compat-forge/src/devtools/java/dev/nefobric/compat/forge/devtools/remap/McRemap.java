package dev.nefobric.compat.forge.devtools.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 构建用的重映射工具，让 Forge 运行时能像 Forge 自己那样“按 Mojang 名写代码、发布成 SRG 名”：
 * <ul>
 *   <li>{@code named <混淆游戏 jar> <client.txt> <输出>}：混淆名 → Mojang 名（类和成员），得到编译用的游戏 jar；</li>
 *   <li>{@code reobf <输入 jar> <输出> <client.txt> <joined.tsrg 或 mcp_config.zip> <Mojang 名游戏 jar> [库 jar…]}：
 *       Mojang 成员名 → SRG 成员名（类名不变），得到运行时命名空间里的 jar，和核心重映射后的游戏 jar 对得上。</li>
 * </ul>
 * 这个 jar 只在本机构建时生成，不分发原版代码。
 */
public final class McRemap {
    private McRemap() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            usage();
            return;
        }
        switch (args[0]) {
            case "named" -> {
                if (args.length != 4) {
                    usage();
                    return;
                }
                int n = named(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
                System.out.println("Remapped " + n + " classes to Mojang names: " + args[3]);
            }
            case "reobf" -> {
                if (args.length < 6) {
                    usage();
                    return;
                }
                List<Path> libs = java.util.Arrays.stream(args, 6, args.length).map(Path::of).toList();
                int n = reobf(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]),
                        Path.of(args[5]), libs);
                System.out.println("Remapped " + n + " classes to SRG member names: " + args[2]);
            }
            default -> usage();
        }
    }

    private static void usage() {
        System.err.println("usage: named <obf.jar> <client.txt> <out.jar>");
        System.err.println("       reobf <in.jar> <out.jar> <client.txt> <joined.tsrg|mcp_config.zip> <named-mc.jar> [libs...]");
        System.exit(2);
    }

    public static int named(Path obfJar, Path mojangMappings, Path out) throws IOException {
        ProguardMappings mojang = readProguard(mojangMappings);
        ClassIndex index = new ClassIndex();
        index.addJar(obfJar);
        return JarRemapper.remap(obfJar, out, new HierarchyRemapper(mojang.classesObfToNamed(), mojang.obfToNamed(),
                index));
    }

    public static int reobf(Path in, Path out, Path mojangMappings, Path srg, Path namedGame, List<Path> libs)
            throws IOException {
        ProguardMappings mojang = readProguard(mojangMappings);
        TsrgMappings tsrg = readTsrg(srg);
        ClassIndex index = new ClassIndex();
        index.addJar(in);
        index.addJar(namedGame);
        for (Path lib : libs) {
            index.addJar(lib);
        }
        return JarRemapper.remap(in, out, new HierarchyRemapper(Map.of(), tsrg.namedToSrg(mojang), index));
    }

    static ProguardMappings readProguard(Path file) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return ProguardMappings.read(r);
        }
    }

    static TsrgMappings readTsrg(Path file) throws IOException {
        if (file.toString().endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                ZipEntry e = zip.getEntry("config/joined.tsrg");
                if (e == null) {
                    throw new IOException("config/joined.tsrg not found in " + file);
                }
                try (BufferedReader r = new BufferedReader(new InputStreamReader(zip.getInputStream(e),
                        StandardCharsets.UTF_8))) {
                    return TsrgMappings.read(r);
                }
            }
        }
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return TsrgMappings.read(r);
        }
    }
}
