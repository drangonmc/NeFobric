package dev.nefobric.loader.transform;

/** 某个变换器处理某个类时出错。消息里带上类名、阶段和变换器，方便从崩溃报告找到是哪个模组的问题。 */
public final class ClassTransformException extends RuntimeException {
    private final String className;
    private final TransformerPhase phase;

    public ClassTransformException(String className, TransformerPhase phase, Object transformer, Throwable cause) {
        super("Failed to transform class " + className + " in phase " + phase + " ("
                + transformer.getClass().getName() + "): " + cause, cause);
        this.className = className;
        this.phase = phase;
    }

    public String className() {
        return className;
    }

    public TransformerPhase phase() {
        return phase;
    }
}
