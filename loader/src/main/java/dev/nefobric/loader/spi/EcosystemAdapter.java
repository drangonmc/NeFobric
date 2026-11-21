package dev.nefobric.loader.spi;

import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;

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
}
