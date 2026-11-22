package dev.nefobric.loader.metadata;

import dev.nefobric.loader.util.Messages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一元数据模型：不管模组来自 {@code fabric.mod.json} 还是 {@code META-INF/mods.toml}，
 * 读出来之后都是一个 {@code ModCandidate}。
 */
public final class ModCandidate {
    private final String id;
    private final String version;
    private final String name;
    private final Ecosystem ecosystem;
    private final Path origin;
    private final SideRestriction environment;
    private final Map<String, List<String>> entrypoints;
    private final List<Dependency> dependencies;
    private final List<String> provides;
    private final List<String> nestedJars;
    private final List<String> mixinConfigs;
    private final List<String> accessTransformers;
    private final String sourceNamespace;
    private final Map<String, Object> extras;

    private ModCandidate(Builder b) {
        this.id = b.id;
        this.version = b.version;
        this.name = b.name == null || b.name.isBlank() ? b.id : b.name;
        this.ecosystem = b.ecosystem;
        this.origin = b.origin;
        this.environment = b.environment;
        Map<String, List<String>> eps = new LinkedHashMap<>();
        b.entrypoints.forEach((k, v) -> eps.put(k, List.copyOf(v)));
        this.entrypoints = Collections.unmodifiableMap(eps);
        this.dependencies = List.copyOf(b.dependencies);
        this.provides = List.copyOf(b.provides);
        this.nestedJars = List.copyOf(b.nestedJars);
        this.mixinConfigs = List.copyOf(b.mixinConfigs);
        this.accessTransformers = List.copyOf(b.accessTransformers);
        this.sourceNamespace = b.sourceNamespace;
        this.extras = Collections.unmodifiableMap(new LinkedHashMap<>(b.extras));
    }

    public static Builder builder(String id, String version, Ecosystem ecosystem) {
        return new Builder(id, version, ecosystem);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public String name() {
        return name;
    }

    public Ecosystem ecosystem() {
        return ecosystem;
    }

    /** 模组所在的 jar；内置模组为 {@code null}。 */
    public Path origin() {
        return origin;
    }

    public boolean isBuiltin() {
        return ecosystem == Ecosystem.BUILTIN;
    }

    public SideRestriction environment() {
        return environment;
    }

    public Map<String, List<String>> entrypoints() {
        return entrypoints;
    }

    public List<String> entrypoints(String key) {
        return entrypoints.getOrDefault(key, List.of());
    }

    public List<Dependency> dependencies() {
        return dependencies;
    }

    public List<String> provides() {
        return provides;
    }

    public List<String> nestedJars() {
        return nestedJars;
    }

    public List<String> mixinConfigs() {
        return mixinConfigs;
    }

    public List<String> accessTransformers() {
        return accessTransformers;
    }

    /**
     * 模组字节码用的命名空间，例如 Fabric 模组是 {@code intermediary}。{@code null} 表示已经是运行时命名空间
     * （Forge 系模组），不需要重映射。见 {@link dev.nefobric.loader.mapping.Mappings}。
     */
    public String sourceNamespace() {
        return sourceNamespace;
    }

    /** 兼容层自己的附加数据，例如 Forge 系模组的 {@code @Mod} 类名。 */
    public <T> T extra(String key, Class<T> type) {
        Object value = extras.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public String fileName() {
        return origin == null ? Messages.tr("mod.builtin") : String.valueOf(origin.getFileName());
    }

    /** 给玩家看的简短描述，例如 “examplemod 1.0.0（Fabric，example.jar）”。 */
    public String describe() {
        return Messages.tr("mod.describe", id, version, ecosystem.displayName(), fileName());
    }

    @Override
    public String toString() {
        return id + "@" + version + "[" + ecosystem + "]";
    }

    public static final class Builder {
        private final String id;
        private final String version;
        private final Ecosystem ecosystem;
        private String name;
        private Path origin;
        private SideRestriction environment = SideRestriction.ANY;
        private final Map<String, List<String>> entrypoints = new LinkedHashMap<>();
        private final List<Dependency> dependencies = new ArrayList<>();
        private final List<String> provides = new ArrayList<>();
        private final List<String> nestedJars = new ArrayList<>();
        private final List<String> mixinConfigs = new ArrayList<>();
        private final List<String> accessTransformers = new ArrayList<>();
        private String sourceNamespace;
        private final Map<String, Object> extras = new LinkedHashMap<>();

        private Builder(String id, String version, Ecosystem ecosystem) {
            this.id = Objects.requireNonNull(id, "id");
            this.version = Objects.requireNonNull(version, "version");
            this.ecosystem = Objects.requireNonNull(ecosystem, "ecosystem");
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder origin(Path origin) {
            this.origin = origin;
            return this;
        }

        public Builder environment(SideRestriction environment) {
            this.environment = environment == null ? SideRestriction.ANY : environment;
            return this;
        }

        public Builder entrypoint(String key, String value) {
            this.entrypoints.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder dependency(Dependency dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder provides(String id) {
            this.provides.add(id);
            return this;
        }

        public Builder nestedJar(String path) {
            this.nestedJars.add(path);
            return this;
        }

        public Builder mixinConfig(String config) {
            this.mixinConfigs.add(config);
            return this;
        }

        public Builder accessTransformer(String path) {
            this.accessTransformers.add(path);
            return this;
        }

        /** 见 {@link ModCandidate#sourceNamespace()}。 */
        public Builder sourceNamespace(String namespace) {
            this.sourceNamespace = namespace;
            return this;
        }

        public Builder extra(String key, Object value) {
            this.extras.put(key, value);
            return this;
        }

        public ModCandidate build() {
            return new ModCandidate(this);
        }
    }
}
