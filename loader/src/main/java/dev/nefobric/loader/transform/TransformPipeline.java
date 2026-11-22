package dev.nefobric.loader.transform;

import dev.nefobric.loader.classloading.ClassTransformer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 类变换流水线：按 {@link TransformerPhase} 的顺序运行各阶段的变换器。Mixin 变换器由核心单独设置，
 * 总在 {@link TransformerPhase#MIXIN} 阶段的最后运行。
 */
public final class TransformPipeline implements TransformerRegistry {
    private final Map<TransformerPhase, List<ClassTransformer>> phases = new EnumMap<>(TransformerPhase.class);
    private volatile ClassTransformer mixin;
    private volatile ClassGenerator generator;

    public TransformPipeline() {
        for (TransformerPhase p : TransformerPhase.values()) {
            phases.put(p, new CopyOnWriteArrayList<>());
        }
    }

    @Override
    public void add(TransformerPhase phase, ClassTransformer transformer) {
        if (phase == null || transformer == null) {
            throw new NullPointerException(phase == null ? "phase" : "transformer");
        }
        phases.get(phase).add(transformer);
    }

    /** 由核心的 Mixin 启动代码调用。 */
    public void setMixin(ClassTransformer mixin, ClassGenerator generator) {
        this.mixin = mixin;
        this.generator = generator;
    }

    public boolean hasMixin() {
        return mixin != null;
    }

    /** 跑完全部阶段（含 Mixin），得到最终定义的字节码。 */
    public byte[] transform(String className, byte[] bytes) {
        return run(className, bytes, null);
    }

    /** 只跑 {@link TransformerPhase#MIXIN} 之前的阶段：Mixin 读类信息时看到的字节码。 */
    public byte[] transformBeforeMixin(String className, byte[] bytes) {
        return run(className, bytes, TransformerPhase.MIXIN);
    }

    /** 在所有 jar 里都找不到的类：问 Mixin 是不是它要生成的合成类。 */
    public byte[] generate(String className) {
        ClassGenerator g = generator;
        return g == null ? null : g.generate(className);
    }

    private byte[] run(String className, byte[] bytes, TransformerPhase stopBefore) {
        for (TransformerPhase phase : TransformerPhase.values()) {
            if (phase == stopBefore) {
                return bytes;
            }
            for (ClassTransformer t : phases.get(phase)) {
                bytes = apply(t, phase, className, bytes);
            }
        }
        ClassTransformer m = mixin;
        if (m != null) {
            // Mixin 自己的异常（MixinApplyError、InvalidInjectionException 等）信息已经很完整，不再包一层
            bytes = m.transform(className, bytes);
        }
        return bytes;
    }

    private static byte[] apply(ClassTransformer t, TransformerPhase phase, String className, byte[] bytes) {
        byte[] out;
        try {
            out = t.transform(className, bytes);
        } catch (ClassTransformException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ClassTransformException(className, phase, t, e);
        }
        if (out == null) {
            throw new ClassTransformException(className, phase, t,
                    new NullPointerException("transformer returned null"));
        }
        return out;
    }
}
