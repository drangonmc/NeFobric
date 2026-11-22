package dev.nefobric.forge.fml;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.nefobric.compat.forge.ForgeAdapter;
import dev.nefobric.compat.forge.ModsTomlParser;
import dev.nefobric.loader.Env;
import dev.nefobric.loader.NeFobric;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.ModCandidate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IConfigurable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Forge 运行时和 NeFobric 核心之间的桥：当前端、游戏目录、版本号，以及从核心解析好的模组列表构造 Forge 的
 * {@link LoadingModList}。没有初始化核心时（单元测试）可以用 {@link #setupForTests} 指定。
 */
public final class ForgeEnvironment {
    public static final String MC_VERSION = NeFobric.TARGET_MINECRAFT;
    public static final String MCP_VERSION = "20230612.114412";
    public static final String FORGE_GROUP = "net.minecraftforge";
    /** Forge 47 的规范版本（MANIFEST 的 Specification-Version）。 */
    public static final String FORGE_SPEC = "47";

    private static volatile Dist testDist;
    private static volatile Path testGameDir;
    private static volatile List<ModCandidate> testMods;

    private ForgeEnvironment() {
    }

    /** 单元测试用：不经过核心直接指定端、目录和模组。 */
    public static synchronized void setupForTests(Dist dist, Path gameDir, List<ModCandidate> mods) {
        testDist = dist;
        testGameDir = gameDir;
        testMods = mods == null ? List.of() : List.copyOf(mods);
    }

    private static boolean core() {
        return testDist == null && NeFobricLoader.isInitialized();
    }

    public static Dist dist() {
        if (core()) {
            return NeFobricLoader.get().env() == Env.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER;
        }
        return testDist != null ? testDist : Dist.DEDICATED_SERVER;
    }

    public static Path gameDir() {
        if (core()) {
            return NeFobricLoader.get().gameDir();
        }
        return testGameDir != null ? testGameDir : Path.of(".").toAbsolutePath().normalize();
    }

    public static String forgeVersion() {
        return ForgeAdapter.EMULATED_FORGE_VERSION;
    }

    public static VersionInfo versionInfo() {
        return new VersionInfo(forgeVersion(), MC_VERSION, MCP_VERSION, FORGE_GROUP);
    }

    /** 当前加载的 Forge 系模组，按核心的加载顺序。 */
    public static List<ModCandidate> forgeMods() {
        List<ModCandidate> all = core() ? NeFobricLoader.get().mods() : testMods == null ? List.of() : testMods;
        return all.stream().filter(m -> m.ecosystem().isForgeFamily()).toList();
    }

    public static Path runtimePath(ModCandidate mod) {
        return core() ? NeFobricLoader.get().runtimePath(mod) : mod.origin();
    }

    public static ClassLoader gameClassLoader() {
        if (core()) {
            return NeFobricLoader.get().classLoader();
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : ForgeEnvironment.class.getClassLoader();
    }

    /** {@link LoadingModList#get()} 第一次被调用时构造。 */
    public static synchronized void ensureModList() {
        if (built) {
            return;
        }
        built = true;
        List<ModFileInfo> files = new ArrayList<>();
        List<ModInfo> sorted = new ArrayList<>();
        ModFileInfo builtin = builtinFile();
        builtinFile = builtin;
        builtin.getFile().setScanResult(new net.minecraftforge.forgespi.language.ModFileScanData(), null);
        files.add(builtin);
        builtin.getMods().forEach(m -> sorted.add((ModInfo) m));

        Map<Path, List<ModCandidate>> byJar = new LinkedHashMap<>();
        for (ModCandidate mod : forgeMods()) {
            if (mod.origin() != null) {
                byJar.computeIfAbsent(mod.origin(), k -> new ArrayList<>()).add(mod);
            }
        }
        for (Map.Entry<Path, List<ModCandidate>> e : byJar.entrySet()) {
            ModCandidate first = e.getValue().get(0);
            Path jar = runtimePath(first);
            UnmodifiableConfig toml = readModsToml(jar);
            ModFile file = new ModFile(jar, implementationVersion(jar));
            ModFileInfo info = new ModFileInfo(file, toml, moduleName(first));
            for (ModCandidate mod : e.getValue()) {
                IConfigurable modConfig = modTable(info, mod.id());
                ModInfo modInfo = new ModInfo(info, modConfig, mod.version());
                info.addMod(modInfo);
                sorted.add(modInfo);
            }
            files.add(info);
        }
        LoadingModList.create(files, sorted);
    }

    private static boolean built;
    private static ModFileInfo builtinFile;

    /** minecraft 和 forge 两个虚拟模组所在的“文件”。 */
    public static boolean isBuiltinFile(ModFileInfo file) {
        return file != null && file == builtinFile;
    }

    /** minecraft 和 forge 两个虚拟模组：Forge 生产环境里它们分别来自游戏 jar 和 Forge 自己的 jar。 */
    private static ModFileInfo builtinFile() {
        CommentedConfig cfg = TomlFormat.newConfig();
        cfg.set("modLoader", "javafml");
        cfg.set("loaderVersion", "[1,)");
        cfg.set("license", "LGPL-2.1-only (NeFobric reimplementation)");
        List<Config> mods = new ArrayList<>();
        mods.add(modTable(cfg, "minecraft", "Minecraft", MC_VERSION, "Minecraft"));
        mods.add(modTable(cfg, "forge", "Forge", forgeVersion(),
                "Forge API reimplemented by NeFobric " + NeFobric.VERSION));
        cfg.set("mods", mods);
        ModFile file = new ModFile(selfJar(), forgeVersion());
        ModFileInfo info = new ModFileInfo(file, cfg, "forge");
        for (IConfigurable c : info.getConfigList("mods")) {
            String id = c.<String>getConfigElement("modId").orElseThrow();
            info.addMod(new ModInfo(info, c, id.equals("minecraft") ? MC_VERSION : forgeVersion()));
        }
        return info;
    }

    private static Config modTable(CommentedConfig parent, String id, String name, String version, String desc) {
        Config c = parent.createSubConfig();
        c.set("modId", id);
        c.set("version", version);
        c.set("displayName", name);
        c.set("description", desc);
        return c;
    }

    private static IConfigurable modTable(ModFileInfo info, String modId) {
        for (IConfigurable c : info.getConfigList("mods")) {
            if (modId.equals(c.<String>getConfigElement("modId").orElse(null))) {
                return c;
            }
        }
        // mods.toml 里找不到（不应该发生）：给一个只有 modId 的表
        CommentedConfig cfg = TomlFormat.newConfig();
        cfg.set("modId", modId);
        return new TomlConfigurable(cfg);
    }

    private static UnmodifiableConfig readModsToml(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entry = jf.getEntry(ModsTomlParser.PATH);
            if (entry == null) {
                return TomlFormat.newConfig();
            }
            try (InputStream in = jf.getInputStream(entry)) {
                return new TomlParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            return TomlFormat.newConfig();
        }
    }

    private static String implementationVersion(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest mf = jf.getManifest();
            return mf == null ? null : mf.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        } catch (IOException e) {
            return null;
        }
    }

    private static String moduleName(ModCandidate mod) {
        String name = String.valueOf(mod.origin().getFileName());
        try (JarFile jf = new JarFile(mod.origin().toFile())) {
            Manifest mf = jf.getManifest();
            String auto = mf == null ? null : mf.getMainAttributes().getValue("Automatic-Module-Name");
            if (auto != null && !auto.isBlank()) {
                return auto.trim();
            }
        } catch (IOException ignored) {
            // 用文件名
        }
        return Objects.requireNonNullElse(mod.id(), name);
    }

    /** Forge 运行时自己所在的 jar（开发环境下可能是一个目录）。 */
    public static Path selfJar() {
        try {
            var cs = ForgeEnvironment.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                return Path.of(cs.getLocation().toURI());
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // 落到游戏目录
        }
        return gameDir();
    }
}
