package net.minecraftforge.fml;

/** 每个模组容器所处的阶段；每个阶段带一个延迟任务队列（{@code enqueueWork}）。 */
public enum ModLoadingStage {
    ERROR,
    VALIDATE,
    CONSTRUCT,
    COMMON_SETUP,
    SIDED_SETUP,
    ENQUEUE_IMC,
    PROCESS_IMC,
    COMPLETE,
    DONE;

    private final DeferredWorkQueue deferredWorkQueue;

    ModLoadingStage() {
        deferredWorkQueue = new DeferredWorkQueue(this);
    }

    ModLoadingStage nextState(Throwable exception) {
        return exception != null ? ERROR : values()[Math.min(ordinal() + 1, values().length - 1)];
    }

    public ModLoadingStage currentState(Throwable exception) {
        return exception != null ? ERROR : this;
    }

    public DeferredWorkQueue getDeferredWorkQueue() {
        return deferredWorkQueue;
    }
}
