package dev.nefobric.loader.transform;

import dev.nefobric.loader.classloading.ClassTransformer;

/** 兼容层往类变换流水线上挂变换器的入口，见 {@code EcosystemAdapter#registerTransformers}。 */
public interface TransformerRegistry {
    /**
     * 在给定阶段的末尾加一个变换器。变换器只会收到游戏、模组和兼容层运行库里的类，
     * 不会收到加载器自己的类（JDK、ASM、Mixin、{@code dev.nefobric.loader.} 等）。
     */
    void add(TransformerPhase phase, ClassTransformer transformer);
}
