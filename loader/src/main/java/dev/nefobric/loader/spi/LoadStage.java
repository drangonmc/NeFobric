package dev.nefobric.loader.spi;

/**
 * 模组初始化的阶段，顺序与架构文档“生命周期对齐”一节一致。
 */
public enum LoadStage {
    /** 类加载器就绪、游戏主类运行之前（Fabric preLaunch）。 */
    PRE_LAUNCH,
    /** 游戏钩子触发后：构造 Forge 系模组的 {@code @Mod} 类。 */
    CONSTRUCT,
    /** Fabric {@code main} 入口。 */
    MAIN,
    /** 按端运行：Fabric {@code client} 或 {@code server} 入口。 */
    SIDE
}
