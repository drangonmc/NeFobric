package dev.nefobric.compat.forge;

import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.spi.ModLoadingException;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Forge 系兼容层（1.20.1 上 Forge 47 与 NeoForge 47.1 共用）：读 mods.toml，扫描 {@code @Mod} 主类，
 * 在游戏钩子触发时构造主类。Forge 的事件总线、注册表、配置等 API 留到路线图的阶段 2。
 */
public final class ForgeAdapter implements EcosystemAdapter {
    /** 对外声明的版本号，只为通过模组的依赖检查，不代表已经实现了对应版本的 API。 */
    public static final String EMULATED_FORGE_VERSION = "47.4.0";
    public static final String EMULATED_NEOFORGE_VERSION = "47.1.106";
    /** mods.toml 里 loaderVersion 对照的 FML 语言加载器版本。 */
    public static final String FML_LANGUAGE_VERSION = "47";

    private final Map<String, Object> modInstances = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public String displayName() {
        return "Forge/NeoForge";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean hasMetadata(ModJar jar) {
        return jar.has(ModsTomlParser.PATH);
    }

    @Override
    public List<ModCandidate> readMods(ModJar jar) throws ModMetadataException {
        try {
            String toml = jar.readString(ModsTomlParser.PATH).orElseThrow();
            Manifest mf = jar.manifest();
            String implVersion = null;
            List<String> manifestMixins = new ArrayList<>();
            if (mf != null) {
                Attributes main = mf.getMainAttributes();
                implVersion = main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                String mixinConfigs = main.getValue("MixinConfigs");
                if (mixinConfigs != null) {
                    Arrays.stream(mixinConfigs.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                            .forEach(manifestMixins::add);
                }
            }
            ModAnnotationScanner.Result scan = ModAnnotationScanner.scan(jar);
            Map<String, String> modClasses = scan.modClasses();
            ModsTomlParser.JarInfo info = new ModsTomlParser.JarInfo(jar.path(), implVersion, modClasses,
                    manifestMixins, jar.has("META-INF/accesstransformer.cfg"),
                    jar.has("META-INF/jarjar/metadata.json"));
            List<ModCandidate> mods = ModsTomlParser.parse(toml, info);
            if (modClasses.isEmpty() && !scan.neoModClasses().isEmpty()) {
                // 只有 net.neoforged 的 @Mod：NeoForge 20.2 以后的模组，在 1.20.1 上一构造就会缺类
                throw ModMetadataException.skip(Messages.tr("forge.modernNeoForgeAnnotation",
                        scan.neoModClasses().values().iterator().next()));
            }
            for (Map.Entry<String, String> e : modClasses.entrySet()) {
                if (mods.stream().noneMatch(m -> m.id().equals(e.getKey()))) {
                    Log.warn(Messages.tr("forge.extraModClass", e.getValue(), e.getKey(), jar.fileName()));
                }
            }
            return mods;
        } catch (IOException e) {
            throw new ModMetadataException(e.toString(), e);
        }
    }

    @Override
    public List<ModCandidate> builtinMods(GameInstall game) {
        return List.of(
                ModCandidate.builder("forge", EMULATED_FORGE_VERSION, Ecosystem.BUILTIN)
                        .name("Forge (NeFobric)").build(),
                ModCandidate.builder("neoforge", EMULATED_NEOFORGE_VERSION, Ecosystem.BUILTIN)
                        .name("NeoForge (NeFobric)").build(),
                ModCandidate.builder("javafml", FML_LANGUAGE_VERSION, Ecosystem.BUILTIN)
                        .name("FML Java language provider (NeFobric)").build(),
                ModCandidate.builder("lowcodefml", FML_LANGUAGE_VERSION, Ecosystem.BUILTIN)
                        .name("FML low-code language provider (NeFobric)").build());
    }

    @Override
    public Collection<String> loaderOwnedClasses() {
        return List.of("net.minecraftforge.fml.common.Mod");
    }

    @Override
    public boolean owns(ModCandidate mod) {
        return mod.ecosystem().isForgeFamily();
    }

    @Override
    public List<String> warningsFor(ModCandidate mod) {
        List<String> out = new ArrayList<>();
        if ("javafml".equals(mod.extra(ModsTomlParser.EXTRA_MOD_LOADER, String.class))
                && mod.extra(ModsTomlParser.EXTRA_MOD_CLASS, String.class) == null) {
            out.add(Messages.tr("forge.noModClass", mod.describe(), mod.id()));
        }
        if (!mod.accessTransformers().isEmpty()) {
            out.add(Messages.tr("forge.atIgnored", mod.describe()));
        }
        if (!mod.nestedJars().isEmpty()) {
            out.add(Messages.tr("compat.nestedIgnored", mod.describe(), mod.nestedJars().size()));
        }
        return out;
    }

    @Override
    public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) throws ModLoadingException {
        if (stage != LoadStage.CONSTRUCT) {
            return;
        }
        for (ModCandidate mod : mods) {
            String className = mod.extra(ModsTomlParser.EXTRA_MOD_CLASS, String.class);
            if (className == null) {
                continue;
            }
            Log.debug("Constructing @Mod " + className + " for " + mod.id());
            try {
                Class<?> c = Class.forName(className, true, loader.classLoader());
                modInstances.put(mod.id(), c.getDeclaredConstructor().newInstance());
            } catch (InvocationTargetException e) {
                throw new ModLoadingException(mod, "@Mod " + className, e.getCause() != null ? e.getCause() : e);
            } catch (ExceptionInInitializerError e) {
                throw new ModLoadingException(mod, "@Mod " + className, e.getCause() != null ? e.getCause() : e);
            } catch (Throwable t) {
                throw new ModLoadingException(mod, "@Mod " + className, t);
            }
        }
    }

    /** 已构造的模组主类实例，之后的 ModList / ModContainer 会用到。 */
    public Map<String, Object> modInstances() {
        synchronized (modInstances) {
            return Map.copyOf(modInstances);
        }
    }
}
