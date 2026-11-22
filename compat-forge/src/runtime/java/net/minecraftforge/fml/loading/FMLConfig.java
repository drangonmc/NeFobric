package net.minecraftforge.fml.loading;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FML 自己的设置（Forge 的 {@code config/fml.toml}）。NeFobric 没有早期窗口、不做版本检查，只保留默认值，
 * 并让 {@link #updateConfig} 在内存里生效。
 */
public class FMLConfig {
    public enum ConfigValue {
        EARLY_WINDOW_CONTROL("earlyWindowControl", Boolean.FALSE),
        MAX_THREADS("maxThreads", -1),
        VERSION_CHECK("versionCheck", Boolean.FALSE),
        DEFAULT_CONFIG_PATH("defaultConfigPath", "defaultconfigs"),
        DISABLE_OPTIMIZED_DFU("disableOptimizedDFU", Boolean.TRUE),
        EARLY_WINDOW_PROVIDER("earlyWindowProvider", "none"),
        EARLY_WINDOW_WIDTH("earlyWindowWidth", 854),
        EARLY_WINDOW_HEIGHT("earlyWindowHeight", 480),
        EARLY_WINDOW_FBSCALE("earlyWindowFBScale", 1),
        EARLY_WINDOW_MAXIMIZED("earlyWindowMaximized", Boolean.FALSE),
        EARLY_WINDOW_SKIP_GL_VERSIONS("earlyWindowSkipGLVersions", List.of()),
        EARLY_WINDOW_SQUIR("earlyWindowSquir", Boolean.FALSE),
        EARLY_WINDOW_SHOW_CPU("earlyWindowShowCPU", Boolean.FALSE),
        EARLY_WINDOW_LOG_HELP_MSG("earlyWindowLogHelpMessage", Boolean.FALSE);

        private final String entry;
        private final Object defaultValue;

        ConfigValue(final String entry, final Object defaultValue) {
            this.entry = entry;
            this.defaultValue = defaultValue;
        }

        @SuppressWarnings("unchecked")
        public <T> void updateValue(final CommentedFileConfig configData, final T value) {
            VALUES.put(this, value);
            if (configData != null) {
                configData.set(entry, value);
            }
        }
    }

    private static final Map<ConfigValue, Object> VALUES = new ConcurrentHashMap<>();

    public static void load() {
        // 只用默认值
    }

    private static Object value(ConfigValue v) {
        return VALUES.getOrDefault(v, v.defaultValue);
    }

    public static String getConfigValue(ConfigValue v) {
        return String.valueOf(value(v));
    }

    public static boolean getBoolConfigValue(ConfigValue v) {
        return Boolean.parseBoolean(String.valueOf(value(v)));
    }

    public static int getIntConfigValue(ConfigValue v) {
        Object o = value(v);
        if (v == ConfigValue.MAX_THREADS && o instanceof Integer i && i == -1) {
            return Runtime.getRuntime().availableProcessors();
        }
        return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o));
    }

    @SuppressWarnings("unchecked")
    public static <A> List<A> getListConfigValue(ConfigValue v) {
        Object o = value(v);
        return o instanceof List<?> l ? (List<A>) l : List.of();
    }

    public static <T> void updateConfig(ConfigValue v, T value) {
        v.updateValue(null, value);
    }

    public static String defaultConfigPath() {
        return getConfigValue(ConfigValue.DEFAULT_CONFIG_PATH);
    }
}
