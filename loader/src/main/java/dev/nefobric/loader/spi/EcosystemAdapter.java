package dev.nefobric.loader.spi;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.transform.TransformerRegistry;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * 一个兼容层（Fabric 或 Forge 系）。核心通过 {@link java.util.ServiceLoader} 找到所有兼容层，
 * 自己不依赖任何一家的代码。
 */
public interface EcosystemAdapter {

    /** 给玩家看的名字，例如 “Fabric”。 */
    String displayName();

    /**
     * 一个 jar 同时带有多家元数据时，数字小的优先。架构文档要求优先 Fabric 版：它面对的是原版，不依赖重现的补丁。
     */
    int priority();

    boolean hasMetadata(ModJar jar);

    List<ModCandidate> readMods(ModJar jar) throws ModMetadataException;

    /** 这个兼容层提供的虚拟模组，例如 fabricloader、forge、neoforge。 */
    List<ModCandidate> builtinMods(GameInstall game);

    /**
     * 必须由加载器自己（而不是从模组 jar）提供的类：以点结尾的是包前缀，否则是类名（含其内部类）。
     * 这些通常是兼容层实现的 API 类，例如 {@code net.fabricmc.api.}。
     */
    Collection<String> loaderOwnedClasses();

    boolean owns(ModCandidate mod);

    /** 在给定阶段初始化属于本兼容层的模组。{@code mods} 已经按加载顺序排好。 */
    void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) throws ModLoadingException;

    /** 启动前对已选中的模组给出提示（例如未实现的功能）。 */
    default List<String> warningsFor(ModCandidate mod) {
        return List.of();
    }

    /**
     * 兼容层自己的运行库：必须由游戏类加载器加载的 jar（例如引用了原版类的 Forge API 实现）。它们和模组一样子优先、
     * 走完整的类变换流水线。内嵌在兼容层 jar 里的运行库请解到 {@code cacheDir} 下再返回路径。
     *
     * @param cacheDir {@code <游戏目录>/.nefobric/cache}，可能还不存在
     */
    default List<Path> runtimeLibraries(GameInstall game, Path cacheDir) throws IOException {
        return List.of();
    }

    /**
     * 往类变换流水线上挂变换器（按端剥离、AW/AT、原版补丁……）。在 {@link NeFobricLoader} 初始化之后、
     * Mixin 启动和 {@link LoadStage#PRE_LAUNCH} 之前调用一次。
     *
     * @param mods 属于本兼容层的模组，按加载顺序
     */
    default void registerTransformers(TransformerRegistry registry, NeFobricLoader loader, List<ModCandidate> mods) {
    }

    /**
     * 这个模组在当前端要注册的 Mixin 配置（jar 里的资源路径）。默认是元数据里读到的
     * {@link ModCandidate#mixinConfigs()}（例如 Forge 模组 MANIFEST 里的 {@code MixinConfigs}）。
     */
    default List<String> mixinConfigs(ModCandidate mod, Env env) {
        return mod.mixinConfigs();
    }

    /**
     * Mixin 注册了这个模组的一个配置之后、开始选择配置之前回调，兼容层可以在这里给配置加装饰
     * （{@link IMixinConfig#decorate}），例如 Fabric 记下模组 id 和 Mixin 兼容等级。
     */
    default void decorateMixinConfig(ModCandidate mod, String resource, IMixinConfig config) {
    }
}
