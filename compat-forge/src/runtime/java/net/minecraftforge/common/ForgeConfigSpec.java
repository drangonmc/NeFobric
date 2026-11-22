package net.minecraftforge.common;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigSpec.CorrectionAction;
import com.electronwill.nightconfig.core.ConfigSpec.CorrectionListener;
import com.electronwill.nightconfig.core.EnumGetMethod;
import com.electronwill.nightconfig.core.InMemoryFormat;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.utils.UnmodifiableConfigWrapper;
import net.minecraftforge.fml.Logging;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Forge 的配置规格：模组用 {@link Builder} 声明配置项（路径、默认值、校验、注释），得到每一项的
 * {@link ConfigValue}。文件加载后用 {@link #correct} 把缺失、类型不对或超出范围的值改回默认值。
 *
 * <p>行为和 Forge 47 一致：规格存在一个以路径为键的配置里（值是 {@link ValueSpec}，层级是子配置）；
 * 配置文件加载前 {@link ConfigValue#get()} 返回默认值；读到的值会缓存，重新加载后清空缓存。</p>
 */
public class ForgeConfigSpec extends UnmodifiableConfigWrapper<UnmodifiableConfig> implements IConfigSpec<ForgeConfigSpec> {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<List<String>, String> levelComments;
    private final Map<List<String>, String> levelTranslationKeys;
    private final UnmodifiableConfig values;
    private Config childConfig;
    private boolean isCorrecting = false;

    private ForgeConfigSpec(UnmodifiableConfig storage, UnmodifiableConfig values,
                            Map<List<String>, String> levelComments, Map<List<String>, String> levelTranslationKeys) {
        super(storage);
        this.values = values;
        this.levelComments = levelComments;
        this.levelTranslationKeys = levelTranslationKeys;
    }

    public String getLevelComment(List<String> path) {
        return levelComments.get(path);
    }

    public String getLevelTranslationKey(List<String> path) {
        return levelTranslationKeys.get(path);
    }

    public void setConfig(CommentedConfig config) {
        this.childConfig = config;
        if (config != null && !isCorrect(config)) {
            String configName = config instanceof FileConfig fc ? fc.getNioPath().toString() : config.toString();
            LOGGER.warn(Logging.CORE, "Configuration file {} is not correct. Correcting", configName);
            correct(config,
                    (action, path, incorrectValue, correctedValue) -> LOGGER.warn(Logging.CORE,
                            "Incorrect key {} was corrected from {} to its default, {}. {}", String.join(".", path),
                            incorrectValue, correctedValue,
                            incorrectValue == correctedValue ? "This seems to be an error." : ""),
                    (action, path, incorrectValue, correctedValue) -> LOGGER.debug(Logging.CORE,
                            "The comment on key {} does not match the spec. This may create a backup.",
                            String.join(".", path)));
            if (config instanceof FileConfig fc) {
                fc.save();
            }
        }
        this.afterReload();
    }

    @Override
    public void acceptConfig(final CommentedConfig data) {
        setConfig(data);
    }

    @Override
    public boolean isCorrecting() {
        return isCorrecting;
    }

    public boolean isLoaded() {
        return childConfig != null;
    }

    public UnmodifiableConfig getSpec() {
        return this.config;
    }

    public UnmodifiableConfig getValues() {
        return this.values;
    }

    @Override
    public void afterReload() {
        clearCaches(getValues().valueMap().values());
    }

    private static void clearCaches(Iterable<Object> configValues) {
        for (Object value : configValues) {
            if (value instanceof ConfigValue<?> cv) {
                cv.clearCache();
            } else if (value instanceof UnmodifiableConfig inner) {
                clearCaches(inner.valueMap().values());
            }
        }
    }

    public void save() {
        Objects.requireNonNull(childConfig, "Cannot save config value without assigned Config object present");
        if (childConfig instanceof FileConfig fc) {
            fc.save();
        }
    }

    @Override
    public synchronized boolean isCorrect(CommentedConfig config) {
        LinkedList<String> parentPath = new LinkedList<>();
        return correct(this.config, config, parentPath, Collections.unmodifiableList(parentPath),
                (a, b, c, d) -> {
                }, null, true) == 0;
    }

    @Override
    public int correct(CommentedConfig config) {
        return correct(config, (action, path, incorrectValue, correctedValue) -> {
        }, null);
    }

    public synchronized int correct(CommentedConfig config, CorrectionListener listener) {
        return correct(config, listener, null);
    }

    public synchronized int correct(CommentedConfig config, CorrectionListener listener,
                                    CorrectionListener commentListener) {
        LinkedList<String> parentPath = new LinkedList<>();
        try {
            isCorrecting = true;
            return correct(this.config, config, parentPath, Collections.unmodifiableList(parentPath), listener,
                    commentListener, false);
        } finally {
            isCorrecting = false;
        }
    }

    /**
     * 按规格逐项检查（{@code dryRun} 时遇到第一个问题就返回 1）：子表递归，值用 {@link ValueSpec#test} 校验，
     * 注释不一致时改注释，规格里没有的键删除。返回改动的个数。
     */
    private int correct(UnmodifiableConfig spec, CommentedConfig config, LinkedList<String> parentPath,
                        List<String> parentPathView, CorrectionListener listener,
                        @Nullable CorrectionListener commentListener, boolean dryRun) {
        int count = 0;
        Map<String, Object> specMap = spec.valueMap();
        Map<String, Object> configMap = config.valueMap();
        for (Map.Entry<String, Object> specEntry : specMap.entrySet()) {
            String key = specEntry.getKey();
            Object specValue = specEntry.getValue();
            Object configValue = configMap.get(key);
            CorrectionAction action = configValue == null ? CorrectionAction.ADD : CorrectionAction.REPLACE;
            parentPath.addLast(key);
            if (specValue instanceof Config subSpec) {
                if (configValue instanceof CommentedConfig subConfig) {
                    count += correct(subSpec, subConfig, parentPath, parentPathView, listener, commentListener, dryRun);
                    if (count > 0 && dryRun) {
                        return count;
                    }
                } else if (dryRun) {
                    return 1;
                } else {
                    CommentedConfig newValue = config.createSubConfig();
                    configMap.put(key, newValue);
                    listener.onCorrect(action, parentPathView, configValue, newValue);
                    count++;
                    count += correct(subSpec, newValue, parentPath, parentPathView, listener, commentListener, false);
                }
                String newComment = levelComments.get(parentPath);
                String oldComment = config.getComment(key);
                if (!commentsMatch(oldComment, newComment)) {
                    if (commentListener != null) {
                        commentListener.onCorrect(action, parentPathView, oldComment, newComment);
                    }
                    if (dryRun) {
                        return 1;
                    }
                    config.setComment(key, newComment);
                }
            } else {
                ValueSpec valueSpec = (ValueSpec) specValue;
                if (!valueSpec.test(configValue)) {
                    if (dryRun) {
                        return 1;
                    }
                    Object newValue = valueSpec.correct(configValue);
                    configMap.put(key, newValue);
                    listener.onCorrect(action, parentPathView, configValue, newValue);
                    count++;
                }
                String oldComment = config.getComment(key);
                if (!commentsMatch(oldComment, valueSpec.getComment())) {
                    if (commentListener != null) {
                        commentListener.onCorrect(action, parentPathView, oldComment, valueSpec.getComment());
                    }
                    if (dryRun) {
                        return 1;
                    }
                    config.setComment(key, valueSpec.getComment());
                }
            }
            parentPath.removeLast();
        }
        for (Iterator<Map.Entry<String, Object>> it = configMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            if (!specMap.containsKey(entry.getKey())) {
                if (dryRun) {
                    return 1;
                }
                it.remove();
                parentPath.addLast(entry.getKey());
                listener.onCorrect(CorrectionAction.REMOVE, parentPathView, entry.getValue(), null);
                parentPath.removeLast();
                count++;
            }
        }
        return count;
    }

    /** 注释比较时把 Windows 换行当成 \n（文件在 Windows 上被编辑过时注释会变成 \r\n）。 */
    private static boolean commentsMatch(@Nullable String a, @Nullable String b) {
        if (a != null && b != null && !a.isEmpty() && !b.isEmpty()) {
            return a.replace("\r\n", "\n").equals(b.replace("\r\n", "\n"));
        }
        return Objects.equals(a, b);
    }

    private static List<String> split(String path) {
        return new ArrayList<>(Arrays.asList(path.split("\\.", -1)));
    }

    public static class Builder {
        private final Config storage = Config.of(LinkedHashMap::new, InMemoryFormat.withUniversalSupport());
        private BuilderContext context = new BuilderContext();
        private final Map<List<String>, String> levelComments = new HashMap<>();
        private final Map<List<String>, String> levelTranslationKeys = new HashMap<>();
        private final List<String> currentPath = new ArrayList<>();
        private final List<ConfigValue<?>> values = new ArrayList<>();

        // ---- 通用 ----

        public <T> ConfigValue<T> define(String path, T defaultValue) {
            return define(split(path), defaultValue);
        }

        public <T> ConfigValue<T> define(List<String> path, T defaultValue) {
            return define(path, defaultValue, o -> o != null && defaultValue.getClass().isAssignableFrom(o.getClass()));
        }

        public <T> ConfigValue<T> define(String path, T defaultValue, Predicate<Object> validator) {
            return define(split(path), defaultValue, validator);
        }

        public <T> ConfigValue<T> define(List<String> path, T defaultValue, Predicate<Object> validator) {
            Objects.requireNonNull(defaultValue, "Default value can not be null");
            return define(path, () -> defaultValue, validator);
        }

        public <T> ConfigValue<T> define(String path, Supplier<T> defaultSupplier, Predicate<Object> validator) {
            return define(split(path), defaultSupplier, validator);
        }

        public <T> ConfigValue<T> define(List<String> path, Supplier<T> defaultSupplier, Predicate<Object> validator) {
            return define(path, defaultSupplier, validator, Object.class);
        }

        public <T> ConfigValue<T> define(List<String> path, Supplier<T> defaultSupplier, Predicate<Object> validator,
                                         Class<?> clazz) {
            context.setClazz(clazz);
            return define(path, new ValueSpec(defaultSupplier, validator, context, path), defaultSupplier);
        }

        /** 所有 define 最终都到这里：把规格放进存储，返回值对象。 */
        public <T> ConfigValue<T> define(List<String> path, ValueSpec value, Supplier<T> defaultSupplier) {
            List<String> full = path;
            if (!currentPath.isEmpty()) {
                full = new ArrayList<>(currentPath.size() + path.size());
                full.addAll(currentPath);
                full.addAll(path);
            }
            storage.set(full, value);
            context = new BuilderContext();
            return new ConfigValue<>(this, full, defaultSupplier);
        }

        // ---- 范围 ----

        public <V extends Comparable<? super V>> ConfigValue<V> defineInRange(String path, V defaultValue, V min, V max,
                                                                              Class<V> clazz) {
            return defineInRange(split(path), defaultValue, min, max, clazz);
        }

        public <V extends Comparable<? super V>> ConfigValue<V> defineInRange(List<String> path, V defaultValue, V min,
                                                                              V max, Class<V> clazz) {
            return defineInRange(path, (Supplier<V>) () -> defaultValue, min, max, clazz);
        }

        public <V extends Comparable<? super V>> ConfigValue<V> defineInRange(String path, Supplier<V> defaultSupplier,
                                                                              V min, V max, Class<V> clazz) {
            return defineInRange(split(path), defaultSupplier, min, max, clazz);
        }

        public <V extends Comparable<? super V>> ConfigValue<V> defineInRange(List<String> path,
                                                                              Supplier<V> defaultSupplier, V min, V max,
                                                                              Class<V> clazz) {
            Range<V> range = new Range<>(clazz, min, max);
            context.setRange(range);
            comment("Range: " + range);
            if (min.compareTo(max) > 0) {
                throw new IllegalArgumentException("Range min most be less then max.");
            }
            return define(path, defaultSupplier, range);
        }

        // ---- 候选值 ----

        public <T> ConfigValue<T> defineInList(String path, T defaultValue, Collection<? extends T> acceptableValues) {
            return defineInList(split(path), defaultValue, acceptableValues);
        }

        public <T> ConfigValue<T> defineInList(String path, Supplier<T> defaultSupplier,
                                               Collection<? extends T> acceptableValues) {
            return defineInList(split(path), defaultSupplier, acceptableValues);
        }

        public <T> ConfigValue<T> defineInList(List<String> path, T defaultValue,
                                               Collection<? extends T> acceptableValues) {
            return defineInList(path, () -> defaultValue, acceptableValues);
        }

        public <T> ConfigValue<T> defineInList(List<String> path, Supplier<T> defaultSupplier,
                                               Collection<? extends T> acceptableValues) {
            return define(path, defaultSupplier, o -> o != null && acceptableValues.contains(o));
        }

        // ---- 列表 ----

        public <T> ConfigValue<List<? extends T>> defineList(String path, List<? extends T> defaultValue,
                                                             Predicate<Object> elementValidator) {
            return defineList(split(path), defaultValue, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineList(String path, Supplier<List<? extends T>> defaultSupplier,
                                                             Predicate<Object> elementValidator) {
            return defineList(split(path), defaultSupplier, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineList(List<String> path, List<? extends T> defaultValue,
                                                             Predicate<Object> elementValidator) {
            return defineList(path, () -> defaultValue, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineList(List<String> path,
                                                             Supplier<List<? extends T>> defaultSupplier,
                                                             Predicate<Object> elementValidator) {
            return defineListImpl(path, defaultSupplier, elementValidator, false);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String path, List<? extends T> defaultValue,
                                                                       Predicate<Object> elementValidator) {
            return defineListAllowEmpty(split(path), defaultValue, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String path,
                                                                       Supplier<List<? extends T>> defaultSupplier,
                                                                       Predicate<Object> elementValidator) {
            return defineListAllowEmpty(split(path), defaultSupplier, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(List<String> path,
                                                                       List<? extends T> defaultValue,
                                                                       Predicate<Object> elementValidator) {
            return defineListAllowEmpty(path, () -> defaultValue, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(List<String> path,
                                                                       Supplier<List<? extends T>> defaultSupplier,
                                                                       Predicate<Object> elementValidator) {
            return defineListImpl(path, defaultSupplier, elementValidator, true);
        }

        /**
         * 列表校验：每个元素都要通过 {@code elementValidator}。校正时去掉不合格的元素；剩下的为空就用默认值。
         * {@code allowEmpty} 为 false 时空列表本身也视为需要校正。
         */
        private <T> ConfigValue<List<? extends T>> defineListImpl(List<String> path,
                                                                  Supplier<List<? extends T>> defaultSupplier,
                                                                  Predicate<Object> elementValidator,
                                                                  boolean allowEmpty) {
            context.setClazz(List.class);
            Predicate<Object> validator = x -> x instanceof List<?> l && l.stream().allMatch(elementValidator);
            String key = path.get(path.size() - 1);
            return define(path, new ValueSpec(defaultSupplier, validator, context, path) {
                @Override
                public Object correct(Object value) {
                    if (!(value instanceof List<?> l) || (!allowEmpty && l.isEmpty())) {
                        LOGGER.debug(Logging.CORE, "List on key {} is deemed to need correction: it is null, not a "
                                + "list{}.", key, allowEmpty ? "" : ", or empty");
                        return getDefault();
                    }
                    List<?> list = new ArrayList<>(l);
                    list.removeIf(elementValidator.negate());
                    if (list.isEmpty()) {
                        LOGGER.debug(Logging.CORE, "List on key {} is deemed to need correction. It failed validation.",
                                key);
                        return getDefault();
                    }
                    return list;
                }
            }, defaultSupplier);
        }

        // ---- 枚举 ----

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue) {
            return defineEnum(split(path), defaultValue);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, EnumGetMethod converter) {
            return defineEnum(split(path), defaultValue, converter);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue) {
            return defineEnum(path, defaultValue, defaultValue.getDeclaringClass().getEnumConstants());
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue, EnumGetMethod converter) {
            return defineEnum(path, defaultValue, converter, defaultValue.getDeclaringClass().getEnumConstants());
        }

        @SuppressWarnings("unchecked")
        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, V... acceptableValues) {
            return defineEnum(split(path), defaultValue, acceptableValues);
        }

        @SuppressWarnings("unchecked")
        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, EnumGetMethod converter,
                                                           V... acceptableValues) {
            return defineEnum(split(path), defaultValue, converter, acceptableValues);
        }

        @SuppressWarnings("unchecked")
        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue, V... acceptableValues) {
            return defineEnum(path, defaultValue, (Collection<V>) Arrays.asList(acceptableValues));
        }

        @SuppressWarnings("unchecked")
        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue, EnumGetMethod converter,
                                                           V... acceptableValues) {
            return defineEnum(path, defaultValue, converter, Arrays.asList(acceptableValues));
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue,
                                                           Collection<V> acceptableValues) {
            return defineEnum(split(path), defaultValue, acceptableValues);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, EnumGetMethod converter,
                                                           Collection<V> acceptableValues) {
            return defineEnum(split(path), defaultValue, converter, acceptableValues);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue,
                                                           Collection<V> acceptableValues) {
            return defineEnum(path, defaultValue, EnumGetMethod.NAME_IGNORECASE, acceptableValues);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue, EnumGetMethod converter,
                                                           Collection<V> acceptableValues) {
            Class<V> type = defaultValue.getDeclaringClass();
            return defineEnum(path, defaultValue, converter, obj -> {
                if (obj instanceof Enum) {
                    return acceptableValues.contains(obj);
                }
                if (obj == null) {
                    return false;
                }
                try {
                    return acceptableValues.contains(converter.get(obj, type));
                } catch (IllegalArgumentException | ClassCastException e) {
                    return false;
                }
            });
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, Predicate<Object> validator) {
            return defineEnum(split(path), defaultValue, validator);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, EnumGetMethod converter,
                                                           Predicate<Object> validator) {
            return defineEnum(split(path), defaultValue, converter, validator);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue,
                                                           Predicate<Object> validator) {
            return defineEnum(path, () -> defaultValue, validator, defaultValue.getDeclaringClass());
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue, EnumGetMethod converter,
                                                           Predicate<Object> validator) {
            return defineEnum(path, () -> defaultValue, converter, validator, defaultValue.getDeclaringClass());
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, Supplier<V> defaultSupplier,
                                                           Predicate<Object> validator, Class<V> clazz) {
            return defineEnum(split(path), defaultSupplier, validator, clazz);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, Supplier<V> defaultSupplier,
                                                           EnumGetMethod converter, Predicate<Object> validator,
                                                           Class<V> clazz) {
            return defineEnum(split(path), defaultSupplier, converter, validator, clazz);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, Supplier<V> defaultSupplier,
                                                           Predicate<Object> validator, Class<V> clazz) {
            return defineEnum(path, defaultSupplier, EnumGetMethod.NAME_IGNORECASE, validator, clazz);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, Supplier<V> defaultSupplier,
                                                           EnumGetMethod converter, Predicate<Object> validator,
                                                           Class<V> clazz) {
            context.setClazz(clazz);
            V[] allowedValues = clazz.getEnumConstants();
            comment("Allowed Values: " + Arrays.stream(allowedValues).filter(validator).map(Enum::name)
                    .collect(Collectors.joining(", ")));
            List<String> full = define(path, new ValueSpec(defaultSupplier, validator, context, path), defaultSupplier)
                    .getPath();
            return new EnumValue<>(this, full, defaultSupplier, converter, clazz);
        }

        // ---- 基本类型 ----

        public BooleanValue define(String path, boolean defaultValue) {
            return define(split(path), defaultValue);
        }

        public BooleanValue define(List<String> path, boolean defaultValue) {
            return define(path, (Supplier<Boolean>) () -> defaultValue);
        }

        public BooleanValue define(String path, Supplier<Boolean> defaultSupplier) {
            return define(split(path), defaultSupplier);
        }

        public BooleanValue define(List<String> path, Supplier<Boolean> defaultSupplier) {
            Predicate<Object> isBoolean = o -> o instanceof Boolean
                    || o instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"));
            return new BooleanValue(this, define(path, defaultSupplier, isBoolean, Boolean.class).getPath(),
                    defaultSupplier);
        }

        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) {
            return defineInRange(split(path), defaultValue, min, max);
        }

        public DoubleValue defineInRange(List<String> path, double defaultValue, double min, double max) {
            return defineInRange(path, (Supplier<Double>) () -> defaultValue, min, max);
        }

        public DoubleValue defineInRange(String path, Supplier<Double> defaultSupplier, double min, double max) {
            return defineInRange(split(path), defaultSupplier, min, max);
        }

        public DoubleValue defineInRange(List<String> path, Supplier<Double> defaultSupplier, double min, double max) {
            return new DoubleValue(this, defineInRange(path, defaultSupplier, min, max, Double.class).getPath(),
                    defaultSupplier);
        }

        public IntValue defineInRange(String path, int defaultValue, int min, int max) {
            return defineInRange(split(path), defaultValue, min, max);
        }

        public IntValue defineInRange(List<String> path, int defaultValue, int min, int max) {
            return defineInRange(path, (Supplier<Integer>) () -> defaultValue, min, max);
        }

        public IntValue defineInRange(String path, Supplier<Integer> defaultSupplier, int min, int max) {
            return defineInRange(split(path), defaultSupplier, min, max);
        }

        public IntValue defineInRange(List<String> path, Supplier<Integer> defaultSupplier, int min, int max) {
            return new IntValue(this, defineInRange(path, defaultSupplier, min, max, Integer.class).getPath(),
                    defaultSupplier);
        }

        public LongValue defineInRange(String path, long defaultValue, long min, long max) {
            return defineInRange(split(path), defaultValue, min, max);
        }

        public LongValue defineInRange(List<String> path, long defaultValue, long min, long max) {
            return defineInRange(path, (Supplier<Long>) () -> defaultValue, min, max);
        }

        public LongValue defineInRange(String path, Supplier<Long> defaultSupplier, long min, long max) {
            return defineInRange(split(path), defaultSupplier, min, max);
        }

        public LongValue defineInRange(List<String> path, Supplier<Long> defaultSupplier, long min, long max) {
            return new LongValue(this, defineInRange(path, defaultSupplier, min, max, Long.class).getPath(),
                    defaultSupplier);
        }

        // ---- 注释、层级 ----

        public Builder comment(String comment) {
            context.addComment(comment);
            return this;
        }

        public Builder comment(String... comment) {
            for (int i = 0; i < comment.length; i++) {
                Objects.requireNonNull(comment[i], "Comment string at " + i + " is null.");
            }
            for (String s : comment) {
                context.addComment(s);
            }
            return this;
        }

        public Builder translation(String translationKey) {
            context.setTranslationKey(translationKey);
            return this;
        }

        public Builder worldRestart() {
            context.worldRestart();
            return this;
        }

        public Builder push(String path) {
            return push(split(path));
        }

        public Builder push(List<String> path) {
            currentPath.addAll(path);
            if (context.hasComment()) {
                levelComments.put(new ArrayList<>(currentPath), context.buildComment(path));
                context.clearComment();
            }
            if (context.getTranslationKey() != null) {
                levelTranslationKeys.put(new ArrayList<>(currentPath), context.getTranslationKey());
                context.setTranslationKey(null);
            }
            context.ensureEmpty();
            return this;
        }

        public Builder pop() {
            return pop(1);
        }

        public Builder pop(int count) {
            if (count > currentPath.size()) {
                throw new IllegalArgumentException("Attempted to pop " + count + " elements when we only had: "
                        + currentPath);
            }
            for (int x = 0; x < count; x++) {
                currentPath.remove(currentPath.size() - 1);
            }
            return this;
        }

        public <T> Pair<T, ForgeConfigSpec> configure(Function<Builder, T> consumer) {
            T o = consumer.apply(this);
            return Pair.of(o, this.build());
        }

        public ForgeConfigSpec build() {
            context.ensureEmpty();
            Config valueCfg = Config.of(Config.getDefaultMapCreator(true, true),
                    InMemoryFormat.withSupport(ConfigValue.class::isAssignableFrom));
            values.forEach(v -> valueCfg.set(v.getPath(), v));
            ForgeConfigSpec ret = new ForgeConfigSpec(storage, valueCfg, levelComments, levelTranslationKeys);
            values.forEach(v -> v.spec = ret);
            return ret;
        }

        public interface BuilderConsumer {
            void accept(Builder builder);
        }
    }

    /** 正在声明的那一项的附加信息（注释、翻译键、范围……），每次 define 之后清空。 */
    private static class BuilderContext {
        private final List<String> comment = new LinkedList<>();
        private String langKey;
        private Range<?> range;
        private boolean worldRestart = false;
        private Class<?> clazz;

        public void addComment(String value) {
            Objects.requireNonNull(value, "Passed in null value for comment");
            comment.add(value);
        }

        public void clearComment() {
            comment.clear();
        }

        public boolean hasComment() {
            return !comment.isEmpty();
        }

        public String buildComment() {
            return buildComment(List.of("unknown", "unknown"));
        }

        public String buildComment(final List<String> path) {
            if (comment.stream().allMatch(String::isBlank)) {
                String key = String.join(".", path);
                if (FMLEnvironment.production) {
                    LOGGER.warn(Logging.CORE, "Detected a comment that is all whitespace for config option {}. "
                            + "Please report this to the mod author.", key);
                } else {
                    throw new IllegalStateException("Can not build comment for config option " + key
                            + " as it comprises entirely of blank lines/whitespace.");
                }
                return "A developer of this mod has defined this config option with a blank comment. "
                        + "Please report this to the mod author.";
            }
            return String.join("\n", comment);
        }

        public void setTranslationKey(String value) {
            this.langKey = value;
        }

        public String getTranslationKey() {
            return this.langKey;
        }

        public <V extends Comparable<? super V>> void setRange(Range<V> value) {
            this.range = value;
            this.setClazz(value.getClazz());
        }

        @SuppressWarnings("unchecked")
        public <V extends Comparable<? super V>> Range<V> getRange() {
            return (Range<V>) this.range;
        }

        public void worldRestart() {
            this.worldRestart = true;
        }

        public boolean needsWorldRestart() {
            return this.worldRestart;
        }

        public void setClazz(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Class<?> getClazz() {
            return this.clazz;
        }

        public void ensureEmpty() {
            if (hasComment()) {
                throw new IllegalStateException("Non-empty comment when empty expected");
            }
            if (langKey != null) {
                throw new IllegalStateException("Non-null translation key when null expected");
            }
            if (range != null) {
                throw new IllegalStateException("Non-null range when null expected");
            }
            if (worldRestart) {
                throw new IllegalStateException("Dangeling world restart value set to true");
            }
        }
    }

    @SuppressWarnings("unused")
    public static class Range<V extends Comparable<? super V>> implements Predicate<Object> {
        private final Class<? extends V> clazz;
        private final V min;
        private final V max;

        private Range(Class<V> clazz, V min, V max) {
            this.clazz = clazz;
            this.min = min;
            this.max = max;
        }

        public Class<? extends V> getClazz() {
            return clazz;
        }

        public V getMin() {
            return min;
        }

        public V getMax() {
            return max;
        }

        private boolean isNumber(Object other) {
            return Number.class.isAssignableFrom(clazz) && other instanceof Number;
        }

        /** 数字按 double 比较（TOML 里 1 和 1.0 都算），其他类型要求同类再比较。 */
        @Override
        public boolean test(Object t) {
            if (isNumber(t)) {
                double v = ((Number) t).doubleValue();
                boolean ok = ((Number) min).doubleValue() <= v && v <= ((Number) max).doubleValue();
                if (!ok) {
                    LOGGER.debug(Logging.CORE, "Range value {} is not within its bounds {}-{}", v, min, max);
                }
                return ok;
            }
            if (!clazz.isInstance(t)) {
                return false;
            }
            V c = clazz.cast(t);
            boolean ok = c.compareTo(min) >= 0 && c.compareTo(max) <= 0;
            if (!ok) {
                LOGGER.debug(Logging.CORE, "Range value {} is not within its bounds {}-{}", c, min, max);
            }
            return ok;
        }

        public Object correct(Object value, Object def) {
            if (isNumber(value)) {
                double v = ((Number) value).doubleValue();
                return v < ((Number) min).doubleValue() ? min : v > ((Number) max).doubleValue() ? max : value;
            }
            if (!clazz.isInstance(value)) {
                return def;
            }
            V c = clazz.cast(value);
            return c.compareTo(min) < 0 ? min : c.compareTo(max) > 0 ? max : value;
        }

        @Override
        public String toString() {
            if (clazz == Integer.class) {
                if (max.equals(Integer.MAX_VALUE)) {
                    return "> " + min;
                } else if (min.equals(Integer.MIN_VALUE)) {
                    return "< " + max;
                }
            }
            return min + " ~ " + max;
        }
    }

    public static class ValueSpec {
        private final String comment;
        private final String langKey;
        private final Range<?> range;
        private final boolean worldRestart;
        private final Class<?> clazz;
        private final Supplier<?> supplier;
        private final Predicate<Object> validator;

        private ValueSpec(Supplier<?> supplier, Predicate<Object> validator, BuilderContext context, List<String> path) {
            Objects.requireNonNull(supplier, "Default supplier can not be null");
            Objects.requireNonNull(validator, "Validator can not be null");
            this.comment = context.hasComment() ? context.buildComment(path) : null;
            this.langKey = context.getTranslationKey();
            this.range = context.getRange();
            this.worldRestart = context.needsWorldRestart();
            this.clazz = context.getClazz();
            this.supplier = supplier;
            this.validator = validator;
        }

        public String getComment() {
            return comment;
        }

        public String getTranslationKey() {
            return langKey;
        }

        @SuppressWarnings("unchecked")
        public <V extends Comparable<? super V>> Range<V> getRange() {
            return (Range<V>) this.range;
        }

        public boolean needsWorldRestart() {
            return this.worldRestart;
        }

        public Class<?> getClazz() {
            return this.clazz;
        }

        public boolean test(Object value) {
            return validator.test(value);
        }

        public Object correct(Object value) {
            return range == null ? getDefault() : range.correct(value, getDefault());
        }

        public Object getDefault() {
            return supplier.get();
        }
    }

    public static class ConfigValue<T> implements Supplier<T> {
        private static final boolean USE_CACHES = true;

        private final Builder parent;
        private final List<String> path;
        private final Supplier<T> defaultSupplier;
        private T cachedValue = null;
        private ForgeConfigSpec spec;

        ConfigValue(Builder parent, List<String> path, Supplier<T> defaultSupplier) {
            this.parent = parent;
            this.path = path;
            this.defaultSupplier = defaultSupplier;
            this.parent.values.add(this);
        }

        public List<String> getPath() {
            return new ArrayList<>(path);
        }

        /** 配置文件还没加载时（例如模组在构造函数里就读）返回默认值，和 Forge 生产环境一样。 */
        @Override
        public T get() {
            Objects.requireNonNull(spec, "Cannot get config value before spec is built");
            if (spec.childConfig == null) {
                return defaultSupplier.get();
            }
            if (!USE_CACHES) {
                return getRaw(spec.childConfig, path, defaultSupplier);
            }
            if (cachedValue == null) {
                cachedValue = getRaw(spec.childConfig, path, defaultSupplier);
            }
            return cachedValue;
        }

        protected T getRaw(Config config, List<String> path, Supplier<T> defaultSupplier) {
            return config.getOrElse(path, defaultSupplier);
        }

        public T getDefault() {
            return defaultSupplier.get();
        }

        public Builder next() {
            return parent;
        }

        public void save() {
            Objects.requireNonNull(spec, "Cannot save config value before spec is built");
            Objects.requireNonNull(spec.childConfig, "Cannot save config value without assigned Config object present");
            spec.save();
        }

        public void set(T value) {
            Objects.requireNonNull(spec, "Cannot set config value before spec is built");
            Objects.requireNonNull(spec.childConfig, "Cannot set config value without assigned Config object present");
            spec.childConfig.set(path, value);
            this.cachedValue = value;
        }

        public void clearCache() {
            this.cachedValue = null;
        }
    }

    public static class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(Builder parent, List<String> path, Supplier<Boolean> defaultSupplier) {
            super(parent, path, defaultSupplier);
        }
    }

    public static class IntValue extends ConfigValue<Integer> {
        IntValue(Builder parent, List<String> path, Supplier<Integer> defaultSupplier) {
            super(parent, path, defaultSupplier);
        }

        @Override
        protected Integer getRaw(Config config, List<String> path, Supplier<Integer> defaultSupplier) {
            return config.getIntOrElse(path, defaultSupplier::get);
        }
    }

    public static class LongValue extends ConfigValue<Long> {
        LongValue(Builder parent, List<String> path, Supplier<Long> defaultSupplier) {
            super(parent, path, defaultSupplier);
        }

        @Override
        protected Long getRaw(Config config, List<String> path, Supplier<Long> defaultSupplier) {
            return config.getLongOrElse(path, defaultSupplier::get);
        }
    }

    public static class DoubleValue extends ConfigValue<Double> {
        DoubleValue(Builder parent, List<String> path, Supplier<Double> defaultSupplier) {
            super(parent, path, defaultSupplier);
        }

        @Override
        protected Double getRaw(Config config, List<String> path, Supplier<Double> defaultSupplier) {
            Number n = config.<Number>get(path);
            return n == null ? defaultSupplier.get() : n.doubleValue();
        }
    }

    public static class EnumValue<T extends Enum<T>> extends ConfigValue<T> {
        private final EnumGetMethod converter;
        private final Class<T> clazz;

        EnumValue(Builder parent, List<String> path, Supplier<T> defaultSupplier, EnumGetMethod converter,
                  Class<T> clazz) {
            super(parent, path, defaultSupplier);
            this.converter = converter;
            this.clazz = clazz;
        }

        @Override
        protected T getRaw(Config config, List<String> path, Supplier<T> defaultSupplier) {
            return config.getEnumOrElse(path, clazz, converter, defaultSupplier);
        }
    }
}
