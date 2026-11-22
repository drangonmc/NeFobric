package dev.nefobric.compat.fabric.metadata;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 一个模组的完整 Fabric 元数据。Fabric 模组直接来自 fabric.mod.json；内置模组和 Forge 系模组由 NeFobric 生成一份，
 * 这样 Fabric 模组（例如 Mod Menu）通过 {@code FabricLoader.getAllMods()} 能看到全部模组。
 */
public final class FabricModMetadata implements ModMetadata {
    /** 入口：适配器名（默认 “default”）和值。 */
    public record EntrypointMetadata(String adapter, String value) {
    }

    /** mixins 里的一项：配置文件和生效的端（{@code null} 表示两端）。 */
    public record MixinEntry(String config, EnvType environment) {
        public boolean appliesTo(EnvType env) {
            return environment == null || environment == env;
        }
    }

    private final String type;
    private final String id;
    private final Version version;
    private final List<String> provides;
    private final ModEnvironment environment;
    private final Map<String, List<EntrypointMetadata>> entrypoints;
    private final List<String> jars;
    private final List<MixinEntry> mixins;
    private final String accessWidener;
    private final List<ModDependency> dependencies;
    private final String name;
    private final String description;
    private final List<Person> authors;
    private final List<Person> contributors;
    private final ContactInformation contact;
    private final List<String> license;
    private final SortedMap<Integer, String> icons;
    private final String singleIcon;
    private final Map<String, String> languageAdapters;
    private final Map<String, CustomValue> customValues;

    private FabricModMetadata(Builder b) {
        this.type = b.type;
        this.id = b.id;
        this.version = b.version;
        this.provides = List.copyOf(b.provides);
        this.environment = b.environment;
        Map<String, List<EntrypointMetadata>> eps = new LinkedHashMap<>();
        b.entrypoints.forEach((k, v) -> eps.put(k, List.copyOf(v)));
        this.entrypoints = Collections.unmodifiableMap(eps);
        this.jars = List.copyOf(b.jars);
        this.mixins = List.copyOf(b.mixins);
        this.accessWidener = b.accessWidener;
        this.dependencies = List.copyOf(b.dependencies);
        this.name = b.name;
        this.description = b.description == null ? "" : b.description;
        this.authors = List.copyOf(b.authors);
        this.contributors = List.copyOf(b.contributors);
        this.contact = b.contact == null ? ContactInformation.EMPTY : b.contact;
        this.license = List.copyOf(b.license);
        this.icons = Collections.unmodifiableSortedMap(new TreeMap<>(b.icons));
        this.singleIcon = b.singleIcon;
        this.languageAdapters = Collections.unmodifiableMap(new LinkedHashMap<>(b.languageAdapters));
        this.customValues = Collections.unmodifiableMap(new LinkedHashMap<>(b.customValues));
    }

    public static Builder builder(String type, String id, Version version) {
        return new Builder(type, id, version);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<String> getProvides() {
        return provides;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public ModEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return dependencies;
    }

    @Override
    public String getName() {
        return name == null || name.isEmpty() ? id : name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Collection<Person> getAuthors() {
        return authors;
    }

    @Override
    public Collection<Person> getContributors() {
        return contributors;
    }

    @Override
    public ContactInformation getContact() {
        return contact;
    }

    @Override
    public Collection<String> getLicense() {
        return license;
    }

    /** 单个图标时总是返回它；按尺寸给出时，返回不小于 size 的最小图标，没有则返回最大的。 */
    @Override
    public Optional<String> getIconPath(int size) {
        if (singleIcon != null) {
            return Optional.of(singleIcon);
        }
        if (icons.isEmpty()) {
            return Optional.empty();
        }
        int chosen = -1;
        for (int i : icons.keySet()) {
            chosen = i;
            if (chosen >= size) {
                break;
            }
        }
        return Optional.of(icons.get(chosen));
    }

    @Override
    public boolean containsCustomValue(String key) {
        return customValues.containsKey(key);
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return customValues.get(key);
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return customValues;
    }

    public Map<String, List<EntrypointMetadata>> entrypoints() {
        return entrypoints;
    }

    public List<EntrypointMetadata> entrypoints(String key) {
        return entrypoints.getOrDefault(key, List.of());
    }

    public List<String> jars() {
        return jars;
    }

    public List<MixinEntry> mixins() {
        return mixins;
    }

    public List<String> mixinConfigs(EnvType env) {
        List<String> out = new ArrayList<>();
        for (MixinEntry m : mixins) {
            if (m.appliesTo(env)) {
                out.add(m.config());
            }
        }
        return out;
    }

    /** accessWidener 文件在 jar 里的路径；没有时为 {@code null}。 */
    public String accessWidener() {
        return accessWidener;
    }

    public Map<String, String> languageAdapters() {
        return languageAdapters;
    }

    @Override
    public String toString() {
        return id + " " + version.getFriendlyString();
    }

    public static final class Builder {
        private final String type;
        private final String id;
        private final Version version;
        private final List<String> provides = new ArrayList<>();
        private ModEnvironment environment = ModEnvironment.UNIVERSAL;
        private final Map<String, List<EntrypointMetadata>> entrypoints = new LinkedHashMap<>();
        private final List<String> jars = new ArrayList<>();
        private final List<MixinEntry> mixins = new ArrayList<>();
        private String accessWidener;
        private final List<ModDependency> dependencies = new ArrayList<>();
        private String name;
        private String description;
        private final List<Person> authors = new ArrayList<>();
        private final List<Person> contributors = new ArrayList<>();
        private ContactInformation contact;
        private final List<String> license = new ArrayList<>();
        private final SortedMap<Integer, String> icons = new TreeMap<>();
        private String singleIcon;
        private final Map<String, String> languageAdapters = new LinkedHashMap<>();
        private final Map<String, CustomValue> customValues = new LinkedHashMap<>();

        private Builder(String type, String id, Version version) {
            this.type = type;
            this.id = id;
            this.version = version;
        }

        public Builder provides(String p) {
            provides.add(p);
            return this;
        }

        public Builder environment(ModEnvironment e) {
            environment = e;
            return this;
        }

        public Builder entrypoint(String key, EntrypointMetadata e) {
            entrypoints.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            return this;
        }

        public Builder jar(String file) {
            jars.add(file);
            return this;
        }

        public Builder mixin(MixinEntry m) {
            mixins.add(m);
            return this;
        }

        public Builder accessWidener(String aw) {
            accessWidener = aw;
            return this;
        }

        public Builder dependency(ModDependency d) {
            dependencies.add(d);
            return this;
        }

        public Builder name(String n) {
            name = n;
            return this;
        }

        public Builder description(String d) {
            description = d;
            return this;
        }

        public Builder author(Person p) {
            authors.add(p);
            return this;
        }

        public Builder contributor(Person p) {
            contributors.add(p);
            return this;
        }

        public Builder contact(ContactInformation c) {
            contact = c;
            return this;
        }

        public Builder license(String l) {
            license.add(l);
            return this;
        }

        public Builder icon(String path) {
            singleIcon = path;
            icons.clear();
            return this;
        }

        public Builder icon(int size, String path) {
            singleIcon = null;
            icons.put(size, path);
            return this;
        }

        public Builder languageAdapter(String key, String className) {
            languageAdapters.put(key, className);
            return this;
        }

        public Builder custom(String key, CustomValue value) {
            customValues.put(key, value);
            return this;
        }

        public FabricModMetadata build() {
            return new FabricModMetadata(this);
        }
    }
}
