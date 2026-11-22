package net.minecraftforge.fml;

import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** 当前线程上“正在加载哪个模组”。模组在构造函数和生命周期事件里通过它注册配置和扩展点。 */
public class ModLoadingContext {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ThreadLocal<ModLoadingContext> context = ThreadLocal.withInitial(ModLoadingContext::new);

    private ModContainer activeContainer;
    private Object languageExtension;

    @Deprecated(forRemoval = true, since = "1.21.1")
    public static ModLoadingContext get() {
        return context.get();
    }

    @Deprecated(forRemoval = true, since = "1.21.1")
    public void setActiveContainer(final ModContainer container) {
        this.activeContainer = container;
        this.languageExtension = container == null || container.contextExtension == null ? null
                : container.contextExtension.get();
    }

    @Deprecated(forRemoval = true, since = "1.21.1")
    public ModContainer getActiveContainer() {
        return activeContainer == null ? ModList.get().getModContainerById("minecraft")
                .orElseThrow(() -> new RuntimeException("Where is minecraft???!")) : activeContainer;
    }

    @Deprecated(forRemoval = true, since = "1.21.1")
    public String getActiveNamespace() {
        return activeContainer == null ? "minecraft" : activeContainer.getNamespace();
    }

    public ModContainer getContainer() {
        return getActiveContainer();
    }

    public <T extends Record & IExtensionPoint<T>> void registerExtensionPoint(Class<? extends IExtensionPoint<T>> point,
                                                                              Supplier<T> extension) {
        getContainer().registerExtensionPoint(point, extension);
    }

    public void registerDisplayTest(IExtensionPoint.DisplayTest displayTest) {
        getContainer().registerDisplayTest(() -> displayTest);
    }

    public void registerDisplayTest(Supplier<IExtensionPoint.DisplayTest> displayTest) {
        getContainer().registerDisplayTest(displayTest);
    }

    public void registerDisplayTest(String version, BiPredicate<String, Boolean> remoteVersionTest) {
        getContainer().registerDisplayTest(new IExtensionPoint.DisplayTest(version, remoteVersionTest));
    }

    public void registerDisplayTest(Supplier<String> suppliedVersion, BiPredicate<String, Boolean> remoteVersionTest) {
        getContainer().registerDisplayTest(new IExtensionPoint.DisplayTest(suppliedVersion, remoteVersionTest));
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {
        if (spec.isEmpty()) {
            LOGGER.debug("Attempted to register an empty config for type {} on mod {}", type, getContainer().getModId());
            return;
        }
        getContainer().addConfig(new ModConfig(type, spec, getContainer()));
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
        if (spec.isEmpty()) {
            LOGGER.debug("Attempted to register an empty config for type {} on mod {} using file name {}", type,
                    getContainer().getModId(), fileName);
            return;
        }
        getContainer().addConfig(new ModConfig(type, spec, getContainer(), fileName));
    }

    @SuppressWarnings("unchecked")
    public <T> T extension() {
        return (T) languageExtension;
    }
}
