package net.minecraftforge.fml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 并行事件里 {@code enqueueWork} 排进来的任务：在阶段结束后于主线程按顺序执行。NeFobric 本来就在主线程顺序分发
 * 事件，这里保留“阶段结束后再跑”的时序，因为有模组依赖它（例如在 enqueueWork 里读其他模组 setup 的结果）。
 */
public class DeferredWorkQueue {
    private static final Logger LOGGER = LogManager.getLogger();
    /** 不能用 EnumMap：ModLoadingStage 的构造函数会在枚举初始化完成前调用这里。 */
    private static final Map<ModLoadingStage, DeferredWorkQueue> workQueues = new HashMap<>();

    private final ConcurrentLinkedDeque<TaskInfo> tasks = new ConcurrentLinkedDeque<>();
    private final ModLoadingStage modLoadingStage;

    public DeferredWorkQueue(ModLoadingStage modLoadingStage) {
        this.modLoadingStage = modLoadingStage;
        synchronized (workQueues) {
            workQueues.put(modLoadingStage, this);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static Optional<DeferredWorkQueue> lookup(Optional<ModLoadingStage> parallelClass) {
        synchronized (workQueues) {
            return parallelClass.map(workQueues::get);
        }
    }

    public void runTasks() {
        if (tasks.isEmpty()) {
            return;
        }
        LOGGER.debug(Logging.LOADING, "Dispatching synchronous work for work queue {}: {} jobs", modLoadingStage,
                tasks.size());
        RuntimeException aggregate = new RuntimeException();
        TaskInfo ti;
        while ((ti = tasks.pollFirst()) != null) {
            run(ti, aggregate);
        }
        if (aggregate.getSuppressed().length > 0) {
            LOGGER.fatal(Logging.LOADING, "Synchronous work queue completed exceptionally, see suppressed exceptions",
                    aggregate);
            throw aggregate;
        }
    }

    private static void run(TaskInfo ti, RuntimeException aggregate) {
        long start = System.nanoTime();
        ModLoadingContext.get().setActiveContainer(ti.owner);
        try {
            ti.future.exceptionally(t -> captureException(ti.owner.getModId(), aggregate, t));
            if (ti.task != null) {
                ti.task.run();
            }
        } finally {
            ModLoadingContext.get().setActiveContainer(null);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (ms >= 1000) {
            LOGGER.warn(Logging.LOADING, "Mod '{}' took {} ms to run a deferred task.", ti.owner.getModId(), ms);
        }
    }

    private static <T> T captureException(String modId, RuntimeException aggregate, Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            throwable = ce.getCause();
        }
        aggregate.addSuppressed(throwable);
        LOGGER.error("Mod '{}' encountered an error in a deferred task:", modId, throwable);
        return null;
    }

    public CompletableFuture<Void> enqueueWork(final ModContainer modInfo, final Runnable work) {
        return enqueueWork(modInfo, taskInfo -> CompletableFuture.runAsync(work, r -> taskInfo.task = r));
    }

    public <T> CompletableFuture<T> enqueueWork(final ModContainer modInfo, final Supplier<T> work) {
        return enqueueWork(modInfo, taskInfo -> CompletableFuture.supplyAsync(work, r -> taskInfo.task = r));
    }

    private <T> CompletableFuture<T> enqueueWork(final ModContainer modInfo,
                                                 Function<TaskInfo, CompletableFuture<T>> futureGen) {
        TaskInfo taskInfo = new TaskInfo(modInfo);
        CompletableFuture<T> future = futureGen.apply(taskInfo);
        taskInfo.future = future;
        tasks.add(taskInfo);
        return future;
    }

    private static final class TaskInfo {
        private final ModContainer owner;
        private Runnable task;
        private CompletableFuture<?> future;

        private TaskInfo(ModContainer owner) {
            this.owner = owner;
        }
    }
}
