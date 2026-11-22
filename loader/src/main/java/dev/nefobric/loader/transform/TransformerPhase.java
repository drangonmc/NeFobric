package dev.nefobric.loader.transform;

/**
 * 类变换流水线的阶段，按声明顺序依次运行。同一阶段里的变换器按注册顺序运行。
 *
 * <p>顺序和 Fabric Loader（AW、@Environment 剥离在 Mixin 之前）以及 Forge（AT 在 Mixin 之前）一致。
 * Mixin 读目标类和父类信息时看到的是 {@link #MIXIN} 之前各阶段的结果。
 */
public enum TransformerPhase {
    /** 对原版的补丁：游戏入口钩子，以及 Forge 系对原版补丁的重现。 */
    GAME_PATCH,
    /** 按端剥离：Fabric {@code @Environment}、Forge {@code @OnlyIn}。 */
    STRIP,
    /** 访问修改：Fabric access widener、Forge access transformer。 */
    ACCESS,
    /** Mixin。核心自己的 Mixin 变换器总在这个阶段的最后运行。 */
    MIXIN
}
