package dev.nefobric.compat.fabric;

import dev.nefobric.compat.fabric.aw.AccessWidener;
import dev.nefobric.compat.fabric.aw.AccessWidenerReader;
import dev.nefobric.compat.fabric.aw.AccessWidenerRemapper;
import dev.nefobric.compat.fabric.entrypoint.EntrypointStorage;
import dev.nefobric.compat.fabric.impl.FabricLoaderImpl;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata;
import dev.nefobric.compat.fabric.transform.EnvironmentStripper;
import dev.nefobric.loader.Env;
import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.spi.ModLoadingException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Fabric 兼容层：读 fabric.mod.json，提供 Fabric Loader API，按 Fabric 的顺序和语义调用入口。 */
public final class FabricAdapter implements EcosystemAdapter {
    /**
     * 对外声明的 Fabric Loader 版本。Fabric API 和模组按它检查 fabricloader 依赖；NeFobric 实现的 API 签名以这个版本为准。
     */
    public static final String EMULATED_FABRIC_LOADER_VERSION = "0.16.10";

    /** Fabric 模组 jar 里类和成员引用使用的命名空间，核心据此把模组重映射到运行时命名空间。 */
    public static final String MOD_NAMESPACE = "intermediary";

    static final String METADATA = "fabric.mod.json";

    @Override
    public String displayName() {
        return "Fabric";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean hasMetadata(ModJar jar) {
        return jar.has(METADATA);
    }

    @Override
    public List<ModCandidate> readMods(ModJar jar) throws ModMetadataException {
        try {
            String json = jar.readString(METADATA).orElseThrow();
            return List.of(FabricMetadataParser.parse(json, jar.path()));
        } catch (IOException e) {
            throw new ModMetadataException(e.toString(), e);
        }
    }

    @Override
    public List<ModCandidate> builtinMods(GameInstall game) {
        return List.of(ModCandidate.builder("fabricloader", EMULATED_FABRIC_LOADER_VERSION, Ecosystem.BUILTIN)
                .name("Fabric Loader (NeFobric)")
                .build());
    }

    /** Fabric Loader 的全部类（API 和少数被模组直接引用的实现类）都由加载器提供。 */
    @Override
    public Collection<String> loaderOwnedClasses() {
        return List.of("net.fabricmc.api.", "net.fabricmc.loader.");
    }

    @Override
    public boolean owns(ModCandidate mod) {
        return mod.ecosystem() == Ecosystem.FABRIC;
    }

    @Override
    public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) throws ModLoadingException {
        switch (stage) {
            case PRE_LAUNCH:
                invoke(loader, "preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
                break;
            case MAIN:
                invoke(loader, "main", ModInitializer.class, ModInitializer::onInitialize);
                break;
            case SIDE:
                if (loader.env() == Env.CLIENT) {
                    invoke(loader, "client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
                } else {
                    invoke(loader, "server", DedicatedServerModInitializer.class,
                            DedicatedServerModInitializer::onInitializeServer);
                }
                break;
            default:
                break;
        }
    }

    /** 与 Fabric 相同：一个模组出错后继续调用其他模组，最后报告第一个错误（其余作为 suppressed）。 */
    private static <T> void invoke(NeFobricLoader loader, String key, Class<T> type, Consumer<T> call)
            throws ModLoadingException {
        List<EntrypointStorage.Failure> failures = FabricLoaderImpl.INSTANCE.entrypointStorage()
                .invokeCollecting(key, type, call);
        if (failures.isEmpty()) {
            return;
        }
        EntrypointStorage.Failure first = failures.get(0);
        Throwable cause = first.error();
        for (int i = 1; i < failures.size(); i++) {
            cause.addSuppressed(failures.get(i).error());
        }
        throw new ModLoadingException(first.mod().candidate(), key + " (" + first.definition() + ")", cause);
    }

    // ---- 以下是与核心约定的新接口（核心合并后加上 @Override）----

    /** 核心把模组 jar 重映射到运行时命名空间时用的源命名空间。 */
    public String sourceNamespace(ModCandidate mod) {
        return MOD_NAMESPACE;
    }

    /** 当前端要加载的 Mixin 配置（fabric.mod.json 的 mixins 可以按 environment 限定端）。 */
    public List<String> mixinConfigs(ModCandidate mod, Env env) {
        FabricModMetadata meta = FabricMetadataParser.metadata(mod);
        return meta == null ? mod.mixinConfigs() : meta.mixinConfigs(env == Env.CLIENT ? EnvType.CLIENT : EnvType.SERVER);
    }

    /** Mixin 注册配置时由核心回调：记下所属模组和按依赖算出的兼容等级。 */
    public void decorateMixinConfig(ModCandidate mod, String resource, IMixinConfig config) {
        FabricModMetadata meta = FabricMetadataParser.metadata(mod);
        if (meta == null) {
            return;
        }
        config.decorate(FabricMixinSupport.modIdKey(), mod.id());
        config.decorate(FabricMixinSupport.compatibilityKey(), FabricMixinSupport.compatibilityFor(meta));
    }

    /** 读出模组的 access widener 并换算到运行时命名空间；没有时返回空。 */
    public Optional<AccessWidener> accessWidener(ModCandidate mod, Path jarRoot) throws IOException, AccessWidenerReader.FormatException {
        FabricModMetadata meta = FabricMetadataParser.metadata(mod);
        if (meta == null || meta.accessWidener() == null) {
            return Optional.empty();
        }
        Path file = jarRoot.resolve(meta.accessWidener());
        if (!Files.isRegularFile(file)) {
            throw new IOException("Access widener " + meta.accessWidener() + " of mod " + mod.id() + " not found");
        }
        AccessWidener aw = AccessWidenerReader.read(Files.readString(file, StandardCharsets.UTF_8));
        return Optional.of(AccessWidenerRemapper.toRuntime(aw));
    }

    /** 挂在类变换流水线 STRIP 阶段的变换器。 */
    public EnvironmentStripper environmentStripper(Env env) {
        return new EnvironmentStripper(env == Env.CLIENT ? EnvType.CLIENT : EnvType.SERVER);
    }
}
