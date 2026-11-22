package net.minecraftforge.fml;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.progress.ProgressMeter;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public interface IModLoadingState {
    String name();

    /** 同一阶段（phase）里排在它前面的状态名；空串表示阶段的第一个。 */
    String previous();

    ModLoadingPhase phase();

    Function<ModList, String> message();

    ToIntFunction<ModList> size();

    Optional<Consumer<ModList>> inlineRunnable();

    default <T extends Event & IModBusEvent> Optional<CompletableFuture<Void>> buildTransition(
            final Executor syncExecutor, final Executor parallelExecutor, final ProgressMeter progressBar) {
        return buildTransition(syncExecutor, parallelExecutor, progressBar,
                e -> CompletableFuture.completedFuture(null), e -> CompletableFuture.completedFuture(null));
    }

    <T extends Event & IModBusEvent> Optional<CompletableFuture<Void>> buildTransition(
            final Executor syncExecutor, final Executor parallelExecutor, final ProgressMeter progressBar,
            final Function<Executor, CompletableFuture<Void>> preSyncTask,
            final Function<Executor, CompletableFuture<Void>> postSyncTask);
}
