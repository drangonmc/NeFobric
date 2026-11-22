package net.minecraftforge.fml.config;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * 读写 TOML 配置文件：文件不存在时先看 {@code defaultconfigs/} 里有没有同名默认文件，再新建；加载后监视文件变化，
 * 改动后重新读取、校正并发出 Reloading 事件。
 */
public class ConfigFileTypeHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    static ConfigFileTypeHandler TOML = new ConfigFileTypeHandler();

    private static Path defaultConfigPath() {
        return FMLPaths.GAMEDIR.get().resolve(FMLConfig.getConfigValue(FMLConfig.ConfigValue.DEFAULT_CONFIG_PATH));
    }

    public Function<ModConfig, CommentedFileConfig> reader(Path configBasePath) {
        return c -> {
            final Path configPath = configBasePath.resolve(c.getFileName());
            final CommentedFileConfig configData = CommentedFileConfig.builder(configPath).sync()
                    .preserveInsertionOrder()
                    .autosave()
                    .onFileNotFound((newfile, configFormat) -> setupConfigFile(c, newfile, configFormat))
                    .writingMode(WritingMode.REPLACE)
                    .build();
            LOGGER.debug("Built TOML config for {}", configPath);
            try {
                configData.load();
            } catch (ParsingException ex) {
                throw new ConfigLoadingException(c, ex);
            }
            LOGGER.debug("Loaded TOML config file {}", configPath);
            try {
                FileWatcher.defaultInstance().addWatch(configPath, new ConfigWatcher(c, configData,
                        Thread.currentThread().getContextClassLoader()));
            } catch (IOException e) {
                throw new RuntimeException("Couldn't watch config file", e);
            }
            return configData;
        };
    }

    public void unload(Path configBasePath, ModConfig config) {
        Path configPath = configBasePath.resolve(config.getFileName());
        try {
            FileWatcher.defaultInstance().removeWatch(configPath);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to remove config {} from tracker!", configPath, e);
        }
    }

    private boolean setupConfigFile(final ModConfig modConfig, final Path file, final ConfigFormat<?> conf)
            throws IOException {
        Files.createDirectories(file.getParent());
        Path p = defaultConfigPath().resolve(modConfig.getFileName());
        if (Files.exists(p)) {
            LOGGER.info("Loading default config file from path {}", p);
            Files.copy(p, file);
        } else {
            Files.createFile(file);
            conf.initEmptyFile(file);
        }
        return true;
    }

    public static void backUpConfig(final CommentedFileConfig commentedFileConfig) {
        backUpConfig(commentedFileConfig, 5);
    }

    public static void backUpConfig(final CommentedFileConfig commentedFileConfig, final int maxBackups) {
        Path dir = commentedFileConfig.getNioPath().getParent();
        String name = commentedFileConfig.getNioPath().getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = (dot < 0 ? "" : name.substring(dot + 1)) + ".bak";
        try {
            for (int i = maxBackups; i > 0; i--) {
                Path oldBak = dir.resolve(base + "-" + i + "." + ext);
                if (Files.exists(oldBak)) {
                    if (i >= maxBackups) {
                        Files.delete(oldBak);
                    } else {
                        Files.move(oldBak, dir.resolve(base + "-" + (i + 1) + "." + ext));
                    }
                }
            }
            Files.copy(commentedFileConfig.getNioPath(), dir.resolve(base + "-1." + ext));
        } catch (IOException exception) {
            LOGGER.warn("Failed to back up config file {}", commentedFileConfig.getNioPath(), exception);
        }
    }

    private static final class ConfigWatcher implements Runnable {
        private final ModConfig modConfig;
        private final CommentedFileConfig commentedFileConfig;
        private final ClassLoader realClassLoader;

        ConfigWatcher(final ModConfig modConfig, final CommentedFileConfig commentedFileConfig,
                      final ClassLoader classLoader) {
            this.modConfig = modConfig;
            this.commentedFileConfig = commentedFileConfig;
            this.realClassLoader = classLoader;
        }

        @Override
        public void run() {
            Thread.currentThread().setContextClassLoader(realClassLoader);
            if (this.modConfig.getSpec().isCorrecting()) {
                return;
            }
            try {
                this.commentedFileConfig.load();
                if (!this.modConfig.getSpec().isCorrect(commentedFileConfig)) {
                    LOGGER.warn("Configuration file {} is not correct. Correcting",
                            commentedFileConfig.getNioPath().toAbsolutePath());
                    ConfigFileTypeHandler.backUpConfig(commentedFileConfig);
                    this.modConfig.getSpec().correct(commentedFileConfig);
                    commentedFileConfig.save();
                }
            } catch (ParsingException ex) {
                throw new ConfigLoadingException(modConfig, ex);
            }
            LOGGER.debug("Config file {} changed, sending notifies", this.modConfig.getFileName());
            this.modConfig.getSpec().afterReload();
            this.modConfig.fireEvent(IConfigEvent.reloading(this.modConfig));
        }
    }

    private static final class ConfigLoadingException extends RuntimeException {
        ConfigLoadingException(ModConfig config, Exception cause) {
            super("Failed loading config file " + config.getFileName() + " of type " + config.getType()
                    + " for modid " + config.getModId(), cause);
        }
    }
}
