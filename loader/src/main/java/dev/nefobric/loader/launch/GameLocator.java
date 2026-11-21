package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.NeFobric;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 找到游戏 jar 并读出版本号。 */
public final class GameLocator {
    public static final String CLIENT_MAIN = "net.minecraft.client.main.Main";
    public static final String SERVER_MAIN = "net.minecraft.server.Main";

    private static final Pattern VERSION_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private GameLocator() {
    }

    public static GameInstall locate(LaunchArgs args) throws IOException {
        return args.env() == Env.CLIENT ? locateClient() : locateServer(args.gameDir());
    }

    /** 客户端：启动器把原版 jar 放在 classpath 上，找含有客户端主类的那一项。 */
    static GameInstall locateClient() throws IOException {
        Path jar = override();
        if (jar == null) {
            jar = findOnClasspath(CLIENT_MAIN.replace('.', '/') + ".class");
        }
        if (jar == null) {
            throw new NeFobricStartupException(Messages.tr("game.client.notFound"));
        }
        return new GameInstall(Env.CLIENT, jar, List.of(), readVersion(jar));
    }

    /** 专用服务端：读服务器目录里的原版 server.jar，是 bundler 时先解出真正的服务端 jar 和库。 */
    static GameInstall locateServer(Path serverDir) throws IOException {
        Path jar = override();
        if (jar == null) {
            for (String name : List.of("server.jar", "minecraft_server." + NeFobric.TARGET_MINECRAFT + ".jar")) {
                Path candidate = serverDir.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    jar = candidate;
                    break;
                }
            }
        }
        if (jar == null) {
            throw new NeFobricStartupException(Messages.tr("game.server.notFound", NeFobric.TARGET_MINECRAFT, serverDir));
        }
        if (BundlerExtractor.isBundler(jar)) {
            BundlerExtractor.Extracted extracted = BundlerExtractor.extract(jar, serverDir);
            return new GameInstall(Env.SERVER, extracted.gameJar(), extracted.libraries(), readVersion(extracted.gameJar()));
        }
        return new GameInstall(Env.SERVER, jar, List.of(), readVersion(jar));
    }

    private static Path override() {
        String override = System.getProperty("nefobric.gameJar");
        if (override == null || override.isBlank()) {
            return null;
        }
        Path p = Paths.get(override).toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) {
            throw new NeFobricStartupException(Messages.tr("game.override.missing", p));
        }
        return p;
    }

    private static Path findOnClasspath(String entry) {
        Path self = ownJar();
        String cp = System.getProperty("java.class.path", "");
        for (String part : cp.split(File.pathSeparator)) {
            if (part.isBlank()) {
                continue;
            }
            Path p = Paths.get(part).toAbsolutePath().normalize();
            if (p.equals(self)) {
                continue;
            }
            try {
                if (Files.isDirectory(p)) {
                    if (Files.isRegularFile(p.resolve(entry))) {
                        return p;
                    }
                } else if (Files.isRegularFile(p)) {
                    try (ZipFile zip = new ZipFile(p.toFile())) {
                        if (zip.getEntry(entry) != null) {
                            return p;
                        }
                    }
                }
            } catch (IOException e) {
                Log.debug("Cannot inspect classpath entry " + p + ": " + e);
            }
        }
        return null;
    }

    private static Path ownJar() {
        try {
            CodeSource cs = GameLocator.class.getProtectionDomain().getCodeSource();
            return cs == null ? null : Paths.get(cs.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** 原版 jar 根目录下的 version.json 里有 “id”: “1.20.1”。 */
    static String readVersion(Path jar) {
        try {
            if (Files.isDirectory(jar)) {
                Path vj = jar.resolve("version.json");
                return Files.isRegularFile(vj) ? parseVersion(Files.readString(vj)) : null;
            }
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry e = zip.getEntry("version.json");
                if (e == null) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(e)) {
                    return parseVersion(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            Log.debug("Cannot read version.json from " + jar + ": " + e);
            return null;
        }
    }

    private static String parseVersion(String json) {
        Matcher m = VERSION_ID.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
