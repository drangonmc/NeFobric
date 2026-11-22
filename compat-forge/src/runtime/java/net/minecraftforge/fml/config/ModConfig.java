package net.minecraftforge.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraftforge.fml.ModContainer;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Locale;

/** 一个模组的一份配置文件：类型、规格（ForgeConfigSpec）、文件名，以及加载后的数据。 */
public class ModConfig {
    private final Type type;
    private final IConfigSpec<?> spec;
    private final String fileName;
    private final ModContainer container;
    private final ConfigFileTypeHandler configHandler;
    private CommentedConfig configData;

    public ModConfig(final Type type, final IConfigSpec<?> spec, final ModContainer container, final String fileName) {
        this.type = type;
        this.spec = spec;
        this.fileName = fileName;
        this.container = container;
        this.configHandler = ConfigFileTypeHandler.TOML;
        ConfigTracker.INSTANCE.trackConfig(this);
    }

    public ModConfig(final Type type, final IConfigSpec<?> spec, final ModContainer activeContainer) {
        this(type, spec, activeContainer, defaultConfigName(type, activeContainer.getModId()));
    }

    private static String defaultConfigName(Type type, String modId) {
        return String.format(Locale.ROOT, "%s-%s.toml", modId, type.extension());
    }

    public Type getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public ConfigFileTypeHandler getHandler() {
        return configHandler;
    }

    @SuppressWarnings("unchecked")
    public <T extends IConfigSpec<T>> IConfigSpec<T> getSpec() {
        return (IConfigSpec<T>) spec;
    }

    public String getModId() {
        return container.getModId();
    }

    public CommentedConfig getConfigData() {
        return this.configData;
    }

    void setConfigData(final CommentedConfig configData) {
        this.configData = configData;
        this.spec.acceptConfig(this.configData);
    }

    void fireEvent(final IConfigEvent configEvent) {
        this.container.dispatchConfigEvent(configEvent);
    }

    public void save() {
        if (this.configData instanceof CommentedFileConfig fc) {
            fc.save();
        }
    }

    public Path getFullPath() {
        return this.configData instanceof CommentedFileConfig fc ? fc.getNioPath() : null;
    }

    public void acceptSyncedConfig(byte[] bytes) {
        setConfigData(TomlFormat.instance().createParser().parse(new ByteArrayInputStream(bytes)));
        fireEvent(IConfigEvent.reloading(this));
    }

    public enum Type {
        COMMON,
        CLIENT,
        SERVER;

        public String extension() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
