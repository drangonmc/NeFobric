package net.minecraftforge.fml.core;

import net.minecraftforge.fml.IModStateTransition;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ThreadSelector;
import net.minecraftforge.fml.event.lifecycle.ParallelDispatchEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** 给每个模组发一个 {@link ParallelDispatchEvent}，结束后跑这个阶段 enqueueWork 进来的任务。 */
record ParallelTransition(ModLoadingStage stage, Class<? extends ParallelDispatchEvent> event)
        implements IModStateTransition {
    @Override
    public Supplier<Stream<EventGenerator<?>>> eventFunctionStream() {
        return () -> Stream.of(IModStateTransition.EventGenerator.fromFunction((ModContainer mc) -> {
            try {
                return event.getConstructor(ModContainer.class, ModLoadingStage.class).newInstance(mc, stage);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public ThreadSelector threadSelector() {
        return ThreadSelector.PARALLEL;
    }

    @Override
    public BiFunction<Executor, CompletableFuture<Void>, CompletableFuture<Void>> finalActivityGenerator() {
        return (e, prev) -> prev.thenApplyAsync(t -> {
            stage.getDeferredWorkQueue().runTasks();
            return t;
        }, e);
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
