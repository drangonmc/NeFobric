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

        // @Environment 剥离的检查等核心的 TransformerRegistry 合并、剥离变换器挂上之后再加回来
    }

}
