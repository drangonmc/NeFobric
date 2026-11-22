package net.minecraftforge.fml;

import dev.nefobric.forge.fml.ModContainerFactory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.ForgeFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 驱动 Forge 系模组的加载状态机：GATHER（构造、注册表）、LOAD（配置、setup）、COMPLETE（IMC、加载完成、冻结）。
 *
 * <p>和 Forge 的区别：Forge 把每个状态的事件放到工作线程上并行分发，NeFobric 在调用线程（客户端是渲染线程，
 * 服务端是主线程）上按模组顺序依次分发。{@code enqueueWork} 的任务仍然在状态结束后执行，所以依赖这个时序的
 * 模组行为不变；只是原本在工作线程上可能出现的竞态不会出现。</p>
 */
public class ModLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static ModLoader INSTANCE;

    private final LoadingModList loadingModList;
    private final List<ModLoadingException> loadingExceptions = new ArrayList<>();
    private final List<ModLoadingWarning> loadingWarnings = new ArrayList<>();
    private final ModStateManager stateManager;
    private boolean loadingStateValid;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<Consumer<String>> statusConsumer = StartupMessageManager.modLoaderConsumer();
    private final Set<IModLoadingState> completedStates = new HashSet<>();
    private ModList modList;

    private ModLoader() {
        INSTANCE = this;
        this.loadingModList = FMLLoader.getLoadingModList();
        this.stateManager = new ModStateManager();
        CrashReportCallables.registerCrashCallable("ModLauncher", FMLLoader::getLauncherInfo);
        CrashReportCallables.registerCrashCallable("ModLauncher launch target", FMLLoader::launcherHandlerName);
        CrashReportCallables.registerCrashCallable("ModLauncher naming", FMLLoader::getNaming);
        CrashReportCallables.registerCrashCallable("FML Language Providers", () -> "\n\t\tjavafml@NeFobric\n\t\tlowcodefml@NeFobric");
    }

    public static ModLoader get() {
        return INSTANCE == null ? INSTANCE = new ModLoader() : INSTANCE;
    }

    public void gatherAndInitializeMods(final ModWorkManager.DrivenExecutor syncExecutor,
                                        final Executor parallelExecutor, final Runnable periodicTask) {
        ForgeFeature.registerFeature("javaVersion", ForgeFeature.VersionFeatureTest.forVersionString(
                IModInfo.DependencySide.BOTH, System.getProperty("java.version")));
        ForgeFeature.registerFeature("openGLVersion", ForgeFeature.VersionFeatureTest.forVersionString(
                IModInfo.DependencySide.CLIENT, ImmediateWindowHandler.getGLVersion()));
        loadingStateValid = true;
        final ModList list = ModList.of(loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),
                loadingModList.getMods());
        List<? extends ForgeFeature.Bound> failedBounds = loadingModList.getMods().stream()
                .map(ModInfo::getForgeFeatures)
                .flatMap(Collection::stream)
                .filter(bound -> !ForgeFeature.testFeature(FMLEnvironment.dist, bound))
                .toList();
        if (!failedBounds.isEmpty()) {
            LOGGER.fatal(Logging.CORE, "Failed to validate feature bounds for mods: {}", failedBounds);
            fail(list, failedBounds.stream()
                    .map(fb -> new ModLoadingException(fb.modInfo(), ModLoadingStage.CONSTRUCT,
                            "fml.modloading.feature.missing", null, fb, ForgeFeature.featureValue(fb)))
                    .toList());
        }
        List<ModContainer> containers = new ArrayList<>();
        for (ModFileInfo file : loadingModList.getModFiles()) {
            containers.addAll(ModContainerFactory.build(file, loadingExceptions));
        }
        if (!loadingExceptions.isEmpty()) {
            LOGGER.fatal(Logging.CORE, "Failed to initialize mod containers", loadingExceptions.get(0));
            fail(list, loadingExceptions);
        }
        list.setLoadedMods(containers);
        this.modList = list;
        runPhase(ModLoadingPhase.GATHER, "Mod Gather", syncExecutor, periodicTask);
    }

    private void fail(ModList list, List<ModLoadingException> errors) {
        statusConsumer.ifPresent(c -> c.accept("ERROR DURING MOD LOADING"));
        list.setLoadedMods(List.of());
        loadingStateValid = false;
        throw new LoadingFailedException(new ArrayList<>(errors));
    }

    public void loadMods(final ModWorkManager.DrivenExecutor syncExecutor, final Executor parallelExecutor,
                         final Runnable periodicTask) {
        runPhase(ModLoadingPhase.LOAD, "Mod Loading", syncExecutor, periodicTask);
    }

    public void finishMods(final ModWorkManager.DrivenExecutor syncExecutor, final Executor parallelExecutor,
                           final Runnable periodicTask) {
        runPhase(ModLoadingPhase.COMPLETE, "Mod Complete", syncExecutor, periodicTask);
        statusConsumer.ifPresent(c -> c.accept(String.format("Mod loading complete - %d mods loaded",
                this.modList.size())));
    }

    private void runPhase(ModLoadingPhase phase, String barName, ModWorkManager.DrivenExecutor syncExecutor,
                          Runnable ticker) {
        List<IModLoadingState> states = stateManager.getStates(phase);
        ProgressMeter progress = StartupMessageManager.addProgressBar(barName,
                states.stream().mapToInt(mls -> mls.size().applyAsInt(modList)).sum());
        try {
            for (IModLoadingState state : states) {
                dispatchAndHandleError(state, syncExecutor, ticker, progress);
            }
        } finally {
            progress.complete();
        }
    }

    private void dispatchAndHandleError(IModLoadingState state, ModWorkManager.DrivenExecutor syncExecutor,
                                        Runnable ticker, ProgressMeter progressBar) {
        if (!isLoadingStateValid()) {
            LOGGER.error("Cowardly refusing to process mod state change request from {}", state);
            return;
        }
        LOGGER.debug(Logging.LOADING, "Mod loading state {}", state.name());
        progressBar.label(progressBar.name() + " working");
        syncExecutor.drive(ticker);
        state.inlineRunnable().ifPresent(inline -> {
            try {
                inline.accept(this.modList);
            } catch (ModLoadingException e) {
                loadingStateValid = false;
                throw new LoadingFailedException(List.of(e));
            } catch (LoadingFailedException e) {
                loadingStateValid = false;
                throw e;
            }
        });
        syncExecutor.drive(ticker);
        if (state instanceof ModLoadingState mls) {
            mls.transition().ifPresent(t -> {
                dispatchTransition(t, progressBar);
                t.finalActivityGenerator().apply(Runnable::run, CompletableFuture.completedFuture(null)).join();
            });
        } else {
            state.buildTransition(syncExecutor, Runnable::run, progressBar).ifPresent(cf -> {
                try {
                    cf.join();
                } catch (CompletionException e) {
                    loadingStateValid = false;
                    throw e.getCause() instanceof RuntimeException re ? re : e;
                }
            });
        }
        completedStates.add(state);
    }

    /** 依次对每个模组分发一个转换里的所有事件。所有模组都跑完后，若有错误就一起抛出。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dispatchTransition(IModStateTransition transition, ProgressMeter progressBar) {
        List<IModStateTransition.EventGenerator<?>> generators = transition.eventFunctionStream().get().toList();
        List<ModLoadingException> errors = new ArrayList<>();
        for (int i = 0; i < generators.size(); i++) {
            IModStateTransition.EventGenerator generator = generators.get(i);
            BiFunction<ModLoadingStage, Throwable, ModLoadingStage> next = i == generators.size() - 1
                    ? transition.nextModLoadingStage() : ModLoadingStage::currentState;
            for (ModContainer mc : modList.containersInOrder()) {
                if (mc.getCurrentState() == ModLoadingStage.ERROR) {
                    continue;
                }
                try {
                    mc.runTransition(generator, next);
                } catch (ModLoadingException e) {
                    errors.add(e);
                } catch (Throwable t) {
                    errors.add(new ModLoadingException(mc.getModInfo(), mc.getCurrentState(),
                            "fml.modloading.uncaughterror", t));
                }
                progressBar.increment();
            }
        }
        if (!errors.isEmpty()) {
            loadingStateValid = false;
            LOGGER.fatal(Logging.LOADING, "Failed to complete lifecycle event, {} errors found", errors.size());
            statusConsumer.ifPresent(c -> c.accept("ERROR DURING MOD LOADING"));
            throw new LoadingFailedException(errors);
        }
    }

    public static boolean isLoadingStateValid() {
        return get().loadingStateValid;
    }

    public boolean hasCompletedState(final String stateName) {
        IModLoadingState state = stateManager.findState(stateName);
        return completedStates.contains(state);
    }

    public <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        if (!loadingStateValid) {
            LOGGER.error("Cowardly refusing to send event generator to a broken mod state");
            return;
        }
        ModList.get().forEachModInOrder(mc -> mc.acceptEvent(generator.apply(mc)));
    }

    public <T extends Event & IModBusEvent> void postEvent(T e) {
        if (!loadingStateValid) {
            LOGGER.error("Cowardly refusing to send event {} to a broken mod state", e.getClass().getName());
            return;
        }
        ModList.get().forEachModInOrder(mc -> mc.acceptEvent(e));
    }

    public <T extends Event & IModBusEvent> T postEventWithReturn(T e) {
        postEvent(e);
        return e;
    }

    public <T extends Event & IModBusEvent> void postEventWrapContainerInModOrder(T event) {
        postEventWithWrapInModOrder(event, (mc, e) -> ModLoadingContext.get().setActiveContainer(mc),
                (mc, e) -> ModLoadingContext.get().setActiveContainer(null));
    }

    public <T extends Event & IModBusEvent> void postEventWithWrapInModOrder(T e, BiConsumer<ModContainer, T> pre,
                                                                             BiConsumer<ModContainer, T> post) {
        if (!loadingStateValid) {
            LOGGER.error("Cowardly refusing to send event {} to a broken mod state", e.getClass().getName());
            return;
        }
        ModList.get().forEachModInOrder(mc -> {
            pre.accept(mc, e);
            try {
                mc.acceptEvent(e);
            } finally {
                post.accept(mc, e);
            }
        });
    }

    public List<ModLoadingWarning> getWarnings() {
        return List.copyOf(this.loadingWarnings);
    }

    public void addWarning(ModLoadingWarning warning) {
        this.loadingWarnings.add(warning);
    }

    public static boolean isDataGenRunning() {
        return false;
    }

    /** 只用于报告：哪些模组的容器出错了。 */
    String describeErrors() {
        return loadingExceptions.stream().map(ModLoadingException::getMessage).collect(Collectors.joining("\n"));
    }

    static Dist dist() {
        return FMLEnvironment.dist;
    }
}
