package net.minecraftforge.fml;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 一个已加载的模组：模组信息、当前阶段、扩展点、配置。 */
public abstract class ModContainer {
    protected final String modId;
    protected final String namespace;
    protected final IModInfo modInfo;
    protected ModLoadingStage modLoadingStage;
    protected Supplier<?> contextExtension;
    protected final Map<ModLoadingStage, Runnable> activityMap = new HashMap<>();
    protected final Map<Class<? extends IExtensionPoint<?>>, Supplier<?>> extensionPoints = new IdentityHashMap<>();
    protected final EnumMap<ModConfig.Type, ModConfig> configs = new EnumMap<>(ModConfig.Type.class);
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected Optional<Consumer<IConfigEvent>> configHandler = Optional.empty();

    public ModContainer(IModInfo info) {
        this.modId = info.getModId();
        this.namespace = this.modId;
        this.modInfo = info;
        this.modLoadingStage = ModLoadingStage.CONSTRUCT;

        boolean clientSideOnly = Boolean.TRUE.equals(info.getOwningFile() == null ? null
                : info.getOwningFile().getFileProperties().get(ModFileInfo.CLIENT_SIDE_ONLY_PROP));
        String displayTest = info.getConfig() == null ? null
                : info.getConfig().<String>getConfigElement("displayTest").orElse(null);
        if (displayTest == null) {
            displayTest = clientSideOnly ? "IGNORE_ALL_VERSION" : "MATCH_VERSION";
        }
        Supplier<IExtensionPoint.DisplayTest> supplier = switch (displayTest) {
            case "MATCH_VERSION" -> () -> new IExtensionPoint.DisplayTest(() -> this.modInfo.getVersion().toString(),
                    (incoming, isNetwork) -> Objects.equals(incoming, this.modInfo.getVersion().toString()));
            case "IGNORE_SERVER_VERSION" -> IExtensionPoint.DisplayTest.IGNORE_SERVER_VERSION;
            case "IGNORE_ALL_VERSION" -> IExtensionPoint.DisplayTest.IGNORE_ALL_VERSION;
            case "NONE" -> null;
            default -> throw new IllegalArgumentException("Invalid displayTest value supplied in mods.toml");
        };
        if (supplier != null) {
            extensionPoints.put(IExtensionPoint.DisplayTest.class, supplier);
        }
    }

    ModContainer() {
        this.modLoadingStage = ModLoadingStage.ERROR;
        this.modId = "BROKEN";
        this.namespace = "BROKEN";
        this.modInfo = null;
    }

    public final String getModId() {
        return modId;
    }

    public final String getNamespace() {
        return namespace;
    }

    public ModLoadingStage getCurrentState() {
        return modLoadingStage;
    }

    /** 在 {@code executor} 上为这个容器跑一个阶段：先跑该阶段的活动（例如构造主类），再分发事件。 */
    public static <T extends Event & IModBusEvent> CompletableFuture<Void> buildTransitionHandler(
            final ModContainer target,
            final IModStateTransition.EventGenerator<T> eventGenerator,
            final ProgressMeter progressBar,
            final BiFunction<ModLoadingStage, Throwable, ModLoadingStage> stateChangeHandler,
            final Executor executor) {
        return CompletableFuture
                .runAsync(() -> {
                    ModLoadingContext.get().setActiveContainer(target);
                    target.activityMap.getOrDefault(target.modLoadingStage, () -> {
                    }).run();
                    target.acceptEvent(eventGenerator.apply(target));
                }, executor)
                .whenComplete((mc, exception) -> {
                    target.modLoadingStage = stateChangeHandler.apply(target.modLoadingStage, exception);
                    progressBar.increment();
                    ModLoadingContext.get().setActiveContainer(null);
                });
    }

    /** NeFobric：同步跑一个阶段（主线程顺序分发）。出错时状态变成 ERROR 并抛出。 */
    final <T extends Event & IModBusEvent> void runTransition(IModStateTransition.EventGenerator<T> eventGenerator,
                                                              BiFunction<ModLoadingStage, Throwable, ModLoadingStage> stateChange) {
        Throwable failure = null;
        ModLoadingContext.get().setActiveContainer(this);
        try {
            activityMap.getOrDefault(modLoadingStage, () -> {
            }).run();
            T event = eventGenerator.apply(this);
            if (event != null) {
                acceptEvent(event);
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            modLoadingStage = stateChange.apply(modLoadingStage, failure);
            ModLoadingContext.get().setActiveContainer(null);
        }
        if (failure instanceof RuntimeException re) {
            throw re;
        }
        if (failure instanceof Error err) {
            throw err;
        }
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }

    public IModInfo getModInfo() {
        return modInfo;
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> Optional<T> getCustomExtension(Class<? extends IExtensionPoint<T>> point) {
        return Optional.ofNullable((T) extensionPoints.getOrDefault(point, () -> null).get());
    }

    public <T extends Record & IExtensionPoint<T>> void registerExtensionPoint(Class<? extends IExtensionPoint<T>> point,
                                                                              Supplier<T> extension) {
        extensionPoints.put(point, extension);
    }

    public void registerDisplayTest(IExtensionPoint.DisplayTest displayTest) {
        registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> displayTest);
    }

    public void registerDisplayTest(Supplier<IExtensionPoint.DisplayTest> displayTest) {
        registerExtensionPoint(IExtensionPoint.DisplayTest.class, displayTest);
    }

    public void registerDisplayTest(String version, BiPredicate<String, Boolean> remoteVersionTest) {
        registerDisplayTest(new IExtensionPoint.DisplayTest(version, remoteVersionTest));
    }

    public void registerDisplayTest(Supplier<String> suppliedVersion, BiPredicate<String, Boolean> remoteVersionTest) {
        registerDisplayTest(new IExtensionPoint.DisplayTest(suppliedVersion, remoteVersionTest));
    }

    public void addConfig(final ModConfig modConfig) {
        configs.put(modConfig.getType(), modConfig);
    }

    public void dispatchConfigEvent(IConfigEvent event) {
        configHandler.ifPresent(h -> h.accept(event));
    }

    public abstract boolean matches(Object mod);

    public abstract Object getMod();

    protected <T extends Event & IModBusEvent> void acceptEvent(T e) {
    }
}
