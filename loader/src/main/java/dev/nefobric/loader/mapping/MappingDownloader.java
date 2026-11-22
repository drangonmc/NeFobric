package dev.nefobric.loader.mapping;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首次启动时从官方地址下载缺少的映射文件，并核对 sha1：
 * Mojang 映射来自 piston-meta 上的版本文件，MCPConfig 来自 Forge 的 Maven，intermediary 来自 Fabric 的 Maven。
 * 这些文件的许可证不允许 NeFobric 自己再分发，所以不打包进加载器。
 */
final class MappingDownloader {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
    /** 各游戏版本对应的 MCPConfig 版本（Forge 47.x 用的就是这一版）。 */
    private static final Map<String, String> MCP_CONFIG = Map.of("1.20.1", "1.20.1-20230612.114412");

    private static HttpClient client;

    private MappingDownloader() {
    }

    static String mcpConfigVersion(String mc) {
        return MCP_CONFIG.getOrDefault(mc, mc);
    }

    static Optional<Path> mojang(Path dir, String mc, Env env) {
        String kind = env == Env.SERVER ? "server" : "client";
        try {
            String manifest = get(VERSION_MANIFEST);
            Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"" + Pattern.quote(mc) + "\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(manifest);
            if (!m.find()) {
                throw new IOException("version " + mc + " is not in the version manifest");
            }
            String versionJson = get(m.group(1));
            Matcher d = Pattern.compile("\"" + kind + "_mappings\"\\s*:\\s*\\{([^}]*)}").matcher(versionJson);
            if (!d.find()) {
                throw new IOException("the version file has no " + kind + "_mappings");
            }
            String sha1 = field(d.group(1), "sha1");
            String url = field(d.group(1), "url");
            return Optional.of(download(url, sha1, dir.resolve(kind + ".txt")));
        } catch (IOException | InterruptedException e) {
            fail(kind + ".txt", e);
            return Optional.empty();
        }
    }

    static Optional<Path> mcpConfig(Path dir, String mc) {
        String v = mcpConfigVersion(mc);
        String url = FORGE_MAVEN + "de/oceanlabs/mcp/mcp_config/" + v + "/mcp_config-" + v + ".zip";
        return maven(url, dir.resolve("mcp_config-" + v + ".zip"));
    }

    static Optional<Path> intermediary(Path dir, String mc) {
        String url = FABRIC_MAVEN + "net/fabricmc/intermediary/" + mc + "/intermediary-" + mc + "-v2.jar";
        return maven(url, dir.resolve("intermediary-" + mc + "-v2.jar"));
    }

    private static Optional<Path> maven(String url, Path target) {
        try {
            String sha1 = get(url + ".sha1").trim().split("\\s+")[0];
            return Optional.of(download(url, sha1, target));
        } catch (IOException | InterruptedException e) {
            fail(target.getFileName().toString(), e);
            return Optional.empty();
        }
    }

    private static void fail(String what, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        Log.warn(Messages.tr("mappings.downloadFailed", what, e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    private static String field(String json, String name) throws IOException {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new IOException("missing " + name);
        }
        return m.group(1);
    }

    private static synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .proxy(ProxySelector.getDefault())
                    .build();
        }
        return client;
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "NeFobric").GET().build();
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpResponse<String> r = client().send(request(url), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IOException("HTTP " + r.statusCode() + " for " + url);
        }
        return r.body();
    }

    private static Path download(String url, String sha1, Path target) throws IOException, InterruptedException {
        Log.info(Messages.tr("mappings.downloading", target.getFileName(), url));
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> r = client().send(request(url), HttpResponse.BodyHandlers.ofFile(tmp));
        if (r.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + r.statusCode() + " for " + url);
        }
        String actual = MappingStore.sha1(tmp);
        if (!actual.equalsIgnoreCase(sha1)) {
            Files.deleteIfExists(tmp);
            throw new IOException("checksum mismatch for " + url + ": expected " + sha1 + ", got " + actual);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
}
