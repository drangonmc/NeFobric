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

public record ModLoadingState(String name, String previous,
                              Function<ModList, String> message,
                              ToIntFunction<ModList> size,
                              ModLoadingPhase phase,
                              Optional<Consumer<ModList>> inlineRunnable,
                              Optional<IModStateTransition> transition) implements IModLoadingState {
    @Override
    public <T extends Event & IModBusEvent> Optional<CompletableFuture<Void>> buildTransition(
            final Executor syncExecutor, final Executor parallelExecutor, final ProgressMeter progressBar,
            final Function<Executor, CompletableFuture<Void>> preSyncTask,
            final Function<Executor, CompletableFuture<Void>> postSyncTask) {
        return transition.map(t -> t.build(name, syncExecutor, parallelExecutor, progressBar, preSyncTask,
                postSyncTask));
    }

    public static ModLoadingState empty(final String name, final String previous, final ModLoadingPhase phase) {
        return new ModLoadingState(name, previous, ml -> "", ml -> 0, phase, Optional.empty(), Optional.empty());
    }

    public static ModLoadingState withTransition(final String name, final String previous,
                                                 final ModLoadingPhase phase,
                                                 final IModStateTransition transition) {
        return new ModLoadingState(name, previous, ml -> "Processing transition " + name, ModList::size, phase,
                Optional.empty(), Optional.of(transition));
    }

    public static ModLoadingState withTransition(final String name, final String previous,
                                                 final Function<ModList, String> message,
                                                 final ModLoadingPhase phase,
                                                 final IModStateTransition transition) {
        return new ModLoadingState(name, previous, message, ModList::size, phase, Optional.empty(),
                Optional.of(transition));
    }

    public static ModLoadingState withInline(final String name, final String previous, final ModLoadingPhase phase,
                                             final Consumer<ModList> inline) {
        return new ModLoadingState(name, previous, ml -> "Processing work " + name, ml -> 0, phase,
                Optional.of(inline), Optional.empty());
    }
}
