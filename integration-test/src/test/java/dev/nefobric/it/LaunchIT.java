package dev.nefobric.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 端到端测试：启动真实的子进程，用发布 jar 加载模拟的 1.20.1 游戏和三个测试模组。
 */
class LaunchIT {
    private static final Path LOADER = prop("loaderJar");
    private static final Path FAKE_CLIENT = prop("fakeClientJar");
    private static final Path FAKE_SERVER = prop("fakeServerJar");
    private static final List<Path> TEST_MODS = List.of(prop("fabricHelloJar"), prop("forgeHelloJar"),
            prop("neoforgeHelloJar"));

    private static Path prop(String key) {
        String v = System.getProperty("nefobric.it." + key);
        if (v == null) {
            throw new IllegalStateException("missing system property nefobric.it." + key);
        }
        return Paths.get(v);
    }

    record Run(int exitCode, String output) {
        void assertInOrder(String... markers) {
            int from = 0;
            for (String m : markers) {
                int idx = output.indexOf(m, from);
                if (idx < 0) {
                    fail("Expected \"" + m + "\" after position " + from + " in output:\n" + output);
                }
                from = idx + m.length();
            }
        }
    }

    private static Run run(Path workDir, List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("process timed out");
        }
        return new Run(p.exitValue(), new String(out, StandardCharsets.UTF_8));
    }

    private static List<String> java(String... jvmArgs) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Dnefobric.lang=en");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.addAll(List.of(jvmArgs));
        return cmd;
    }

    private static void installTestMods(Path gameDir) throws IOException {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        for (Path m : TEST_MODS) {
            Files.copy(m, mods.resolve(m.getFileName()));
        }
    }

    private static void writeJar(Path jar, String entry, String content) throws IOException {
        try (OutputStream os = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    @Test
    void clientLoadsFabricForgeAndNeoForgeMods(@TempDir Path gameDir) throws Exception {
        installTestMods(gameDir);
        // 不是模组的 jar 和 Quilt 模组：应该被跳过而不是让游戏崩溃
        writeJar(gameDir.resolve("mods/some-library.jar"), "org/example/Lib.txt", "not a mod");
        writeJar(gameDir.resolve("mods/quilt-thing.jar"), "quilt.mod.json", "{}");

        List<String> cmd = java("-cp", LOADER + File.pathSeparator + FAKE_CLIENT,
                "dev.nefobric.loader.launch.ClientMain", "--username", "Tester", "--gameDir", gameDir.toString(),
                "--version", "nefobric-test");
        Run r = run(gameDir, cmd);

        assertEquals(0, r.exitCode(), r.output());
        r.assertInOrder(
                "NeFobric 0.0.1-alpha is starting Minecraft 1.20.1 (client)",
                "Loading 3 mod(s) (Fabric 1, Forge 1, NeoForge 1)",
                "[fabric_hello] preLaunch",
                "Installed the game hook in net.minecraft.client.main.Main",
                "[FakeGame] client bootstrap",
                "[forge_hello] constructed: Hello from Forge!",
                "[neoforge_hello] constructed: Hello from NeoForge!",
                "[fabric_hello] main: Hello from Fabric!",
                "[fabric_hello] client: client entrypoint ran",
                "Initialized 3 mod(s)",
                "[FakeGame] constructing game",
                "[FakeGame] client running, loader=NeFobric");
        // 钩子在 “Render thread” 上触发，模组彼此可见，游戏类由 NeFobric 加载
        assertTrue(r.output().contains("thread=Render thread, env=CLIENT, forge_hello=true, neoforge_hello=true,"
                + " minecraft=true, gameLoader=NeFobric"), r.output());
        assertTrue(r.output().contains("[forge_hello] constructed: Hello from Forge! / 你好，Forge！ thread=Render thread,"
                + " loader=NeFobric"), r.output());
        assertFalse(r.output().contains("server entrypoint ran"), r.output());
        // Fabric Loader 公开 API 的运行时检查（见 fabric-hello 的 ApiProbe）
        assertTrue(r.output().contains("[fabric_hello] api: OK"), r.output());
        assertTrue(r.output().contains("Skipped quilt-thing.jar: Quilt mods are not supported yet."), r.output());
        assertTrue(r.output().contains("Skipped some-library.jar: not a Fabric, Forge or NeoForge mod."), r.output());
        assertTrue(Files.isRegularFile(gameDir.resolve("logs/nefobric-latest.log")));
    }

    @Test
    void dedicatedServerExtractsBundlerAndLoadsMods(@TempDir Path serverDir) throws Exception {
        installTestMods(serverDir);
        Files.copy(FAKE_SERVER, serverDir.resolve("server.jar"));

        Run r = run(serverDir, java("-jar", LOADER.toString(), "nogui"));

        assertEquals(0, r.exitCode(), r.output());
        r.assertInOrder(
                "NeFobric 0.0.1-alpha is starting Minecraft 1.20.1 (dedicated server)",
                "Extracted 2 file(s) from server.jar",
                "[fabric_hello] preLaunch",
                "Installed the game hook in net.minecraft.server.Main",
                "[FakeGame] server bootstrap, nogui=true",
                "[forge_hello] constructed",
                "[neoforge_hello] constructed",
                "[fabric_hello] main: Hello from Fabric!",
                "[fabric_hello] server: server entrypoint ran",
                "[FakeGame] server running, properties=server.properties, library=fake-library-1.0, loader=NeFobric");
        assertTrue(r.output().contains("env=SERVER"), r.output());
        assertFalse(r.output().contains("client entrypoint ran"), r.output());
        assertTrue(r.output().contains("[fabric_hello] api: OK"), r.output());
        assertTrue(Files.isRegularFile(serverDir.resolve("versions/1.20.1/server-1.20.1.jar")));
        assertTrue(Files.isRegularFile(serverDir.resolve("libraries/org/example/fake-library/1.0/fake-library-1.0.jar")));

        // 第二次启动：文件已解出且校验和相符，不再重复解压
        Run again = run(serverDir, java("-jar", LOADER.toString(), "nogui"));
        assertEquals(0, again.exitCode(), again.output());
        assertFalse(again.output().contains("Extracted"), again.output());
    }

    @Test
    void missingDependenciesStopWithReadableErrors(@TempDir Path gameDir) throws Exception {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        writeJar(mods.resolve("needs-fabric-api.jar"), "fabric.mod.json", """
                {"schemaVersion": 1, "id": "needs_api", "version": "2.0.0",
                 "depends": {"fabric-api": "*", "minecraft": "1.20.1"}}
                """);
        writeJar(mods.resolve("old-forge-mod.jar"), "META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[40,)"
                [[mods]]
                modId="old_mod"
                version="1.0"
                [[dependencies.old_mod]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.19.2,1.19.3)"
                """);

        List<String> cmd = java("-cp", LOADER + File.pathSeparator + FAKE_CLIENT,
                "dev.nefobric.loader.launch.ClientMain", "--gameDir", gameDir.toString());
        Run r = run(gameDir, cmd);

        assertEquals(1, r.exitCode(), r.output());
        r.assertInOrder("NeFobric could not start the game", "Found 2 problem(s):");
        assertTrue(r.output().contains("needs_api 2.0.0 (Fabric, needs-fabric-api.jar) requires fabric-api (any version),"
                + " which is not installed."), r.output());
        assertTrue(r.output().contains("old_mod 1.0 (Forge, old-forge-mod.jar) requires minecraft ([1.19.2,1.19.3)),"
                + " but version 1.20.1 is installed."), r.output());
        assertFalse(r.output().contains("[FakeGame]"), "game must not start: " + r.output());
        try (Stream<Path> reports = Files.list(gameDir.resolve("crash-reports"))) {
            assertEquals(1, reports.filter(p -> p.getFileName().toString().startsWith("nefobric-")).count());
        }
    }

    @Test
    void crashingModProducesReportNamingTheMod(@TempDir Path gameDir) throws Exception {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        // 入口类不存在：应报告是哪个模组、哪个入口出了错
        writeJar(mods.resolve("broken.jar"), "fabric.mod.json", """
                {"schemaVersion": 1, "id": "broken_mod", "version": "0.1.0",
                 "entrypoints": {"main": ["com.example.DoesNotExist"]}}
                """);
        List<String> cmd = java("-cp", LOADER + File.pathSeparator + FAKE_CLIENT,
                "dev.nefobric.loader.launch.ClientMain", "--gameDir", gameDir.toString());
        Run r = run(gameDir, cmd);

        assertTrue(r.exitCode() != 0, r.output());
        assertTrue(r.output().contains("A mod failed to initialize"), r.output());
        assertTrue(r.output().contains("broken_mod 0.1.0 (Fabric, broken.jar) failed in main (com.example.DoesNotExist)"),
                r.output());
        assertFalse(r.output().contains("[FakeGame] client running"), r.output());
    }
}
