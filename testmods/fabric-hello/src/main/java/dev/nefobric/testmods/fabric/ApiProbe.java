package dev.nefobric.testmods.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在 main 入口里把 Fabric Loader 公开 API 走一遍，结果打印成一行 “api: OK (n checks)” 或列出失败项。
 * 只用公开 API，结论在真正的 Fabric Loader 上也应该一样。
 */
public final class ApiProbe {
    static final AtomicInteger CUSTOM_CALLS = new AtomicInteger();

    /** fabric.mod.json 里 “fabric_hello:probe” 入口的字段写法。 */
    public static final Runnable FIELD = CUSTOM_CALLS::incrementAndGet;

    private final List<String> failures = new ArrayList<>();
    private int checks;

    private ApiProbe() {
    }

    /** fabric.mod.json 里 “fabric_hello:probe” 入口的方法写法。 */
    public static void run() {
        CUSTOM_CALLS.incrementAndGet();
    }

    private void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failures.add(what);
        }
    }

    static String runAll() {
        ApiProbe p = new ApiProbe();
        try {
            p.probe();
        } catch (Throwable t) {
            p.failures.add("threw " + t);
        }
        return p.failures.isEmpty() ? "OK (" + p.checks + " checks)" : "FAIL " + p.failures;
    }

    private static boolean hasMethod(Class<?> c, String name) {
        return Arrays.stream(c.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name));
    }

    private static boolean causeMentions(Throwable t, String text) {
        for (; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private void probe() throws Exception {
        FabricLoader loader = FabricLoader.getInstance();
        EnvType env = loader.getEnvironmentType();

        Optional<ModContainer> self = loader.getModContainer("fabric_hello");
        check("own container", self.isPresent());
        ModMetadata meta = self.get().getMetadata();
        check("type", meta.getType().equals("fabric"));
        check("name", meta.getName().equals("Fabric Hello"));
        check("version", meta.getVersion().getFriendlyString().equals("1.0.0"));
        check("environment", meta.getEnvironment() == ModEnvironment.UNIVERSAL);
        check("license", meta.getLicense().contains("MIT"));
        check("author", meta.getAuthors().stream().anyMatch(a -> a.getName().equals("NeFobric")));
        check("contact", meta.getContact().get("homepage").isPresent());
        check("icon", meta.getIconPath(64).orElse("").equals("assets/fabric_hello/icon.png"));
        CustomValue custom = meta.getCustomValue("fabric_hello:probe");
        check("custom object", custom != null && custom.getType() == CustomValue.CvType.OBJECT);
        check("custom number", custom.getAsObject().get("answer").getAsNumber().intValue() == 42);
        check("custom array", custom.getAsObject().get("list").getAsArray().get(0).getAsString().equals("a"));
        check("depends", meta.getDependencies().stream().anyMatch(d -> d.getModId().equals("minecraft")
                && d.matches(loader.getModContainer("minecraft").get().getMetadata().getVersion())));

        Optional<Path> modJson = self.get().findPath("fabric.mod.json");
        check("findPath", modJson.isPresent() && Files.readString(modJson.get()).contains("\"fabric_hello\""));
        check("root paths", !self.get().getRootPaths().isEmpty());
        check("origin", self.get().getOrigin() != null);

        check("minecraft visible", loader.isModLoaded("minecraft"));
        check("minecraft builtin", loader.getModContainer("minecraft").get().getMetadata().getType().equals("builtin"));
        check("java visible", loader.getModContainer("java").isPresent());
        check("fabricloader visible", loader.getModContainer("fabricloader").isPresent());
        check("all mods", loader.getAllMods().stream().anyMatch(m -> m.getMetadata().getId().equals("fabric_hello")));

        List<EntrypointContainer<Runnable>> probes = loader.getEntrypointContainers("fabric_hello:probe", Runnable.class);
        check("custom entrypoints", probes.size() == 2);
        for (EntrypointContainer<Runnable> c : probes) {
            check("entrypoint provider", c.getProvider().getMetadata().getId().equals("fabric_hello"));
            c.getEntrypoint().run();
        }
        check("custom entrypoints ran", CUSTOM_CALLS.get() == 2);

        loader.getObjectShare().put("fabric_hello:probe", "shared");
        check("object share", "shared".equals(loader.getObjectShare().get("fabric_hello:probe")));

        MappingResolver mr = loader.getMappingResolver();
        check("runtime namespace listed", mr.getNamespaces().contains(mr.getCurrentRuntimeNamespace()));
        check("map non-game class", mr.mapClassName(mr.getCurrentRuntimeNamespace(), "java.lang.String").equals("java.lang.String"));

        check("config dir", Files.isDirectory(loader.getConfigDir()));
        check("game dir", Files.isDirectory(loader.getGameDir()));
        check("sanitized args", !Arrays.asList(loader.getLaunchArguments(true)).contains("Tester"));

        check("semver", SemanticVersion.parse("1.2.3-beta+x").compareTo((Version) SemanticVersion.parse("1.2.3")) < 0);

        // 不是公开 API，但 fabric-language-kotlin 等模组直接用它找游戏类加载器
        check("launcher class loader", net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher()
                .getTargetClassLoader() == ApiProbe.class.getClassLoader());
        check("launcher namespace", net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher()
                .getTargetNamespace().equals(mr.getCurrentRuntimeNamespace()));
        check("launcher isClassLoaded", net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher()
                .isClassLoaded(ApiProbe.class.getName()));
        byte[] raw = net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher()
                .getClassByteArray(ApiProbe.class.getName(), false);
        check("launcher class bytes", raw != null && raw.length > 4 && (raw[0] & 0xff) == 0xCA && (raw[1] & 0xff) == 0xFE);

        // @Environment 剥离：另一端的方法被去掉；服务端加载整个只属于客户端的类会失败
        boolean hasClient = hasMethod(EnvProbe.class, "clientOnly");
        boolean hasServer = hasMethod(EnvProbe.class, "serverOnly");
        check("@Environment method strip", env == EnvType.CLIENT ? hasClient && !hasServer : hasServer && !hasClient);
        ClassLoader cl = ApiProbe.class.getClassLoader();
        if (env == EnvType.SERVER) {
            Throwable error = null;
            try {
                Class.forName("dev.nefobric.testmods.fabric.ClientOnlyProbe", false, cl);
            } catch (Throwable t) {
                error = t;
            }
            check("@Environment class strip", causeMentions(error, "Cannot load class dev.nefobric.testmods.fabric.ClientOnlyProbe"));
        }

        // access widener：模拟游戏里的私有方法被改成 public（在真正的游戏里没有这个类，跳过）
        String gameClass = env == EnvType.CLIENT ? "net.minecraft.client.FakeMinecraft" : "net.minecraft.server.Main";
        Class<?> game = null;
        try {
            game = Class.forName(gameClass, false, cl);
        } catch (ClassNotFoundException e) {
            // 真正的游戏
        }
        if (game != null && hasMethod(game, "awProbe")) {
            check("access widener", java.lang.reflect.Modifier.isPublic(game.getDeclaredMethod("awProbe").getModifiers()));
        }
    }

}
