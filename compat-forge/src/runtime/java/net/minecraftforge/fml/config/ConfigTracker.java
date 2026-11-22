package net.minecraftforge.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 所有模组配置：按类型分组，按文件名查找；负责打开（加载）和关闭配置文件。 */
public class ConfigTracker {
    private static final Logger LOGGER = LogManager.getLogger();
    static final Marker CONFIG = MarkerFactory.getMarker("CONFIG");
    public static final ConfigTracker INSTANCE = new ConfigTracker();

    private final ConcurrentHashMap<String, ModConfig> fileMap = new ConcurrentHashMap<>();
    private final EnumMap<ModConfig.Type, Set<ModConfig>> configSets = new EnumMap<>(ModConfig.Type.class);
    private final ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>> configsByMod = new ConcurrentHashMap<>();

    private ConfigTracker() {
        for (ModConfig.Type t : ModConfig.Type.values()) {
            configSets.put(t, Collections.synchronizedSet(new LinkedHashSet<>()));
        }
    }

    void trackConfig(final ModConfig config) {
        if (this.fileMap.containsKey(config.getFileName())) {
            LOGGER.error("Detected config file conflict {} between {} and {}", config.getFileName(),
                    this.fileMap.get(config.getFileName()).getModId(), config.getModId());
            throw new RuntimeException("Config conflict detected!");
        }
        this.fileMap.put(config.getFileName(), config);
        this.configSets.get(config.getType()).add(config);
        this.configsByMod.computeIfAbsent(config.getModId(), k -> new EnumMap<>(ModConfig.Type.class))
                .put(config.getType(), config);
        LOGGER.debug("Config file {} for {} tracking", config.getFileName(), config.getModId());
    }

    public void loadConfigs(ModConfig.Type type, Path configBasePath) {
        LOGGER.debug("Loading configs type {}", type);
        for (ModConfig config : snapshot(type)) {
            openConfig(config, configBasePath);
        }
    }

    public void unloadConfigs(ModConfig.Type type, Path configBasePath) {
        LOGGER.debug("Unloading configs type {}", type);
        for (ModConfig config : snapshot(type)) {
            closeConfig(config, configBasePath);
        }
    }

    private Set<ModConfig> snapshot(ModConfig.Type type) {
        Set<ModConfig> set = configSets.get(type);
        synchronized (set) {
            return new LinkedHashSet<>(set);
        }
    }

    private void openConfig(final ModConfig config, final Path configBasePath) {
        LOGGER.trace("Loading config file type {} at {} for {}", config.getType(), config.getFileName(),
                config.getModId());
        final CommentedFileConfig configData = config.getHandler().reader(configBasePath).apply(config);
        config.setConfigData(configData);
        config.fireEvent(IConfigEvent.loading(config));
        config.save();
    }

    private void closeConfig(final ModConfig config, final Path configBasePath) {
        if (config.getConfigData() != null) {
            LOGGER.trace("Closing config file type {} at {} for {}", config.getType(), config.getFileName(),
                    config.getModId());
            config.getHandler().unload(configBasePath, config);
            IConfigEvent unloading = IConfigEvent.unloading(config);
            if (unloading != null) {
                config.fireEvent(unloading);
            }
            config.save();
            config.setConfigData(null);
        }
    }

    public void loadDefaultServerConfigs() {
        for (ModConfig modConfig : snapshot(ModConfig.Type.SERVER)) {
            final CommentedConfig commentedConfig = CommentedConfig.inMemory();
            modConfig.getSpec().correct(commentedConfig);
            modConfig.setConfigData(commentedConfig);
            modConfig.fireEvent(IConfigEvent.loading(modConfig));
        }
    }

    public String getConfigFileName(String modId, ModConfig.Type type) {
        return Optional.ofNullable(configsByMod.getOrDefault(modId, Collections.emptyMap()).get(type))
                .map(ModConfig::getFullPath).map(Object::toString).orElse(null);
    }

    public Map<ModConfig.Type, Set<ModConfig>> configSets() {
        return configSets;
    }

    public ConcurrentHashMap<String, ModConfig> fileMap() {
        return fileMap;
    }
}
