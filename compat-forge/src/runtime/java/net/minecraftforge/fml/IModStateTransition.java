package net.minecraftforge.fml;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.progress.ProgressMeter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 一个加载状态要给每个模组分发的事件。Forge 用 CompletableFuture 在工作线程上并行分发；NeFobric 在调用线程上
 * 按模组顺序依次分发（见 {@link ModLoader}），{@link #build} 也是同步完成的。
 */
@SuppressWarnings("unchecked")
public interface IModStateTransition {
    static IModStateTransition buildNoopTransition() {
        return new NoopTransition();
    }

    default <T extends Event & IModBusEvent> CompletableFuture<Void> build(final String name,
                                                                          final Executor syncExecutor,
                                                                          final Executor parallelExecutor,
                                                                          final ProgressMeter progressBar,
                                                                          final Function<Executor, CompletableFuture<Void>> preSyncTask,
                                                                          final Function<Executor, CompletableFuture<Void>> postSyncTask) {
        try {
            preSyncTask.apply(Runnable::run).join();
            ModLoader.get().dispatchTransition(this, progressBar);
            postSyncTask.apply(Runnable::run);
            return finalActivityGenerator().apply(Runnable::run, CompletableFuture.completedFuture(null));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    default BiFunction<ModLoadingStage, Throwable, ModLoadingStage> nextModLoadingStage() {
        return ModLoadingStage::nextState;
    }

    Supplier<Stream<EventGenerator<?>>> eventFunctionStream();

    ThreadSelector threadSelector();

    BiFunction<Executor, CompletableFuture<Void>, CompletableFuture<Void>> finalActivityGenerator();

    BiFunction<Executor, ? extends EventGenerator<?>, CompletableFuture<Void>> preDispatchHook();

    BiFunction<Executor, ? extends EventGenerator<?>, CompletableFuture<Void>> postDispatchHook();

    interface EventGenerator<T extends Event & IModBusEvent> extends Function<ModContainer, T> {
        static <FN extends Event & IModBusEvent> EventGenerator<FN> fromFunction(Function<ModContainer, FN> fn) {
            return fn::apply;
        }
    }
}

record NoopTransition() implements IModStateTransition {
    @Override
    public Supplier<Stream<EventGenerator<?>>> eventFunctionStream() {
        return Stream::of;
    }

    @Override
    public ThreadSelector threadSelector() {
        return ThreadSelector.SYNC;
    }

    @Override
    public BiFunction<Executor, CompletableFuture<Void>, CompletableFuture<Void>> finalActivityGenerator() {
        return (e, t) -> t.thenApplyAsync(Function.identity(), e);
    }

    @Override
    public BiFunction<Executor, ? extends EventGenerator<?>, CompletableFuture<Void>> preDispatchHook() {
        return (t, f) -> CompletableFuture.completedFuture(null);
    }

    @Override
    public BiFunction<Executor, ? extends EventGenerator<?>, CompletableFuture<Void>> postDispatchHook() {
        return (t, f) -> CompletableFuture.completedFuture(null);
    }
}
