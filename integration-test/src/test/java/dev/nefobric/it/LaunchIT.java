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
    /** 原版自带的库（log4j、slf4j、commons-lang3）：真游戏的客户端由启动器放进 classpath。 */
    private static final Path FAKE_CLIENT_LIBS = prop("fakeClientLibs");
    private static final List<Path> TEST_MODS = List.of(prop("fabricHelloJar"), prop("forgeHelloJar"),
            prop("neoforgeHelloJar"));
    private static final Path FABRIC_JIJ = prop("fabricJijJar");

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

    /**
     * forge_hello 只用 Forge 公开 API：模组总线上的 setup 事件、enqueueWork、@EventBusSubscriber、ForgeConfigSpec。
     * 配置文件第一次加载时按规格补全并写回。
     */
    private static void assertForgeLifecycle(Run r) {
        boolean client = r.output().contains("(client)");
        r.assertInOrder(
                "[forge_hello] constructed",
                // forge-hello 的 access transformer 把模拟游戏的私有方法改成了 public；@OnlyIn(CLIENT) 的方法只在客户端保留
                "[forge_hello] access transformer: atProbe is public",
                "[forge_hello] @OnlyIn: clientOnly " + (client ? "present" : "stripped"),
                // @AutoRegisterCapability 的接口在构造之后、setup 之前注册（Forge 的 INJECT_CAPABILITIES 状态）
                "[forge_hello] capability registered: dev/nefobric/testmods/forge/HelloCapability, greet=hi",
                "[forge_hello] common setup: Hello from the config x3, mods=4, forge=true",
                "[forge_hello] enqueued work ran",
                "[forge_hello] load complete");
    }

    private static String clientClasspath() throws IOException {
        List<String> cp = new ArrayList<>(List.of(LOADER.toString(), FAKE_CLIENT.toString()));
        try (Stream<Path> libs = Files.list(FAKE_CLIENT_LIBS)) {
            libs.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(p -> cp.add(p.toString()));
        }
        return String.join(File.pathSeparator, cp);
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

        List<String> cmd = java("-cp", clientClasspath(),
                "dev.nefobric.loader.launch.ClientMain", "--username", "Tester", "--gameDir", gameDir.toString(),
                "--version", "nefobric-test");
        Run r = run(gameDir, cmd);

        assertEquals(0, r.exitCode(), r.output());
        r.assertInOrder(
                "NeFobric 0.0.1-alpha is starting Minecraft 1.20.1 (client)",
                "Loading 3 mod(s) (Fabric 1, Forge 1, NeoForge 1)",
                "Mixin 0.8.7 ready: 2 configs from 2 mods",
                "[fabric_hello] preLaunch",
                "[FakeGame] client bootstrap",
                // 和 Fabric Loader 一样插在游戏对象的构造函数里、游戏目录赋值之后
                "Installed the game hook in net.minecraft.client.FakeMinecraft",
                "[forge_hello] constructed: Hello from Forge!",
                "[neoforge_hello] constructed: Hello from NeoForge!",
                "[fabric_hello] main: Hello from Fabric!",
                "[fabric_hello] client: client entrypoint ran",
                "Initialized 3 mod(s)",
                "[FakeGame] constructing game",
                "[forge_hello] mixin: FakeMinecraft constructed",
                "[fabric_hello] mixin: before FakeMinecraft.run",
                "[FakeGame] client running, loader=NeFobric");
        // 钩子在 “Render thread” 上触发，模组彼此可见，游戏类由 NeFobric 加载
        assertTrue(r.output().contains("thread=Render thread, env=CLIENT, forge_hello=true, neoforge_hello=true,"
                + " minecraft=true, gameLoader=NeFobric"), r.output());
        assertTrue(r.output().contains("[forge_hello] constructed: Hello from Forge! / 你好，Forge！ thread=Render thread,"
                + " loader=NeFobric"), r.output());
        assertFalse(r.output().contains("server entrypoint ran"), r.output());
        // 游戏对象在客户端入口运行之前就交给了 FabricLoader.getGameInstance()
        assertTrue(r.output().contains("client entrypoint ran, game=net.minecraft.client.FakeMinecraft"), r.output());
        assertForgeLifecycle(r);
        // Fabric Loader 公开 API 的运行时检查（见 fabric-hello 的 ApiProbe）
        assertTrue(r.output().contains("[fabric_hello] api: OK"), r.output());
        assertTrue(r.output().contains("Skipped quilt-thing.jar: Quilt mods are not supported yet."), r.output());
        assertTrue(r.output().contains("Skipped some-library.jar: not a Fabric, Forge or NeoForge mod."), r.output());
        assertTrue(Files.isRegularFile(gameDir.resolve("logs/nefobric-latest.log")));
        // Forge 自己的客户端和通用配置由内置的 forge 模组写出
        assertTrue(Files.isRegularFile(gameDir.resolve("config/forge-client.toml")));
        assertTrue(Files.isRegularFile(gameDir.resolve("config/forge-common.toml")));
        assertTrue(Files.isRegularFile(gameDir.resolve("config/forge_hello-common.toml")));
    }

    @Test
    void dedicatedServerExtractsBundlerAndLoadsMods(@TempDir Path serverDir) throws Exception {
        installTestMods(serverDir);
        Files.copy(FAKE_SERVER, serverDir.resolve("server.jar"));

        Run r = run(serverDir, java("-jar", LOADER.toString(), "nogui"));

        assertEquals(0, r.exitCode(), r.output());
        r.assertInOrder(
                "NeFobric 0.0.1-alpha is starting Minecraft 1.20.1 (dedicated server)",
                "Extracted 6 file(s) from server.jar",
                "Mixin 0.8.7 ready: 2 configs from 2 mods",
                "[fabric_hello] preLaunch",
                "Installed the game hook in net.minecraft.server.Main",
                "[fabric_hello] mixin: server main",
                "[FakeGame] server bootstrap, nogui=true",
                "[forge_hello] constructed",
                "[neoforge_hello] constructed",
                "[fabric_hello] main: Hello from Fabric!",
                "[fabric_hello] server: server entrypoint ran",
                "[FakeGame] server running, properties=server.properties, library=fake-library-1.0, loader=NeFobric",
                "[forge_hello] mixin: server main returning");
        assertTrue(r.output().contains("env=SERVER"), r.output());
        assertFalse(r.output().contains("client entrypoint ran"), r.output());
        assertForgeLifecycle(r);
        assertFalse(r.output().contains("FakeMinecraft"), "client mixins must not apply on the server: " + r.output());
        assertTrue(r.output().contains("[fabric_hello] api: OK"), r.output());
        assertTrue(Files.isRegularFile(serverDir.resolve("versions/1.20.1/server-1.20.1.jar")));
        assertTrue(Files.isRegularFile(serverDir.resolve("config/forge-common.toml")));
        assertFalse(Files.exists(serverDir.resolve("config/forge-client.toml")), "client config is client only");
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

        List<String> cmd = java("-cp", clientClasspath(),
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
        List<String> cmd = java("-cp", clientClasspath(),
                "dev.nefobric.loader.launch.ClientMain", "--gameDir", gameDir.toString());
        Run r = run(gameDir, cmd);

        assertTrue(r.exitCode() != 0, r.output());
        assertTrue(r.output().contains("A mod failed to initialize"), r.output());
        assertTrue(r.output().contains("broken_mod 0.1.0 (Fabric, broken.jar) failed in main (com.example.DoesNotExist)"),
                r.output());
        assertFalse(r.output().contains("[FakeGame] client running"), r.output());
        // 原版的崩溃处理能拿到游戏目录，写出自己的 “Initializing game” 报告，而不是连锁报 NPE
        assertTrue(r.output().contains("[FakeGame] crash report: Initializing game: "
                + "dev.nefobric.loader.impl.ModInitializationError"), r.output());
        assertFalse(r.output().contains("NullPointerException"), r.output());
    }

    @Test
    void crashingModStopsTheServerWithExitCode1(@TempDir Path serverDir) throws Exception {
        Path mods = Files.createDirectories(serverDir.resolve("mods"));
        writeJar(mods.resolve("broken.jar"), "fabric.mod.json", """
                {"schemaVersion": 1, "id": "broken_mod", "version": "0.1.0",
                 "entrypoints": {"main": ["com.example.DoesNotExist"]}}
                """);
        Files.copy(FAKE_SERVER, serverDir.resolve("server.jar"));
        Run r = run(serverDir, java("-jar", LOADER.toString(), "nogui"));

        assertEquals(1, r.exitCode(), r.output());
        r.assertInOrder("A mod failed to initialize", "Stopping the server because a mod failed to initialize");
        assertFalse(r.output().contains("[FakeGame] server running"), r.output());
    }

    @Test
    void nestedJarsAreExtractedAndLoaded(@TempDir Path gameDir) throws Exception {
        // 和 Fabric Loom 打出来的模组一样：META-INF/jars 里有一个模组（它又内嵌一个模组）和一个普通库
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        Files.copy(FABRIC_JIJ, mods.resolve(FABRIC_JIJ.getFileName()));
        List<String> cmd = java("-cp", LOADER + File.pathSeparator + FAKE_CLIENT,
                "dev.nefobric.loader.launch.ClientMain", "--gameDir", gameDir.toString());
        Run r = run(gameDir, cmd);

        assertEquals(0, r.exitCode(), r.output());
        r.assertInOrder(
                "Loading 3 mod(s) (Fabric 3)",
                "fabric-jij-1.0.0.jar!/fabric-jij-lib-1.0.0.jar!/fabric-jij-deep-1.0.0.jar",
                "[fabric_jij_lib] nested mod entrypoint ran",
                "[fabric_jij] host: hello from a nested mod, plain=plain library loaded, libLoaded=true,"
                        + " deepLoaded=true",
                "[fabric_jij] containers: fabric_jij PATH, parent=-, children=[fabric_jij_lib]; "
                        + "fabric_jij_lib nested in fabric_jij at META-INF/jars/fabric-jij-lib-1.0.0.jar,"
                        + " parent=fabric_jij, children=[fabric_jij_deep]; "
                        + "fabric_jij_deep nested in fabric_jij_lib at META-INF/jars/fabric-jij-deep-1.0.0.jar,"
                        + " parent=fabric_jij_lib, children=[]",
                "[FakeGame] client running");
        try (Stream<Path> cached = Files.walk(gameDir.resolve(".nefobric/cache/jij"))) {
            assertEquals(3, cached.filter(p -> p.toString().endsWith(".jar")).count());
        }
    }
}
