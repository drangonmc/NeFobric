package net.minecraftforge.fml.loading.moddiscovery;

import dev.nefobric.forge.fml.TomlConfigurable;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.MavenVersionAdapter;
import net.minecraftforge.forgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 一个模组（mods.toml 里的一个 [[mods]] 表）。 */
public class ModInfo implements IModInfo, IConfigurable {
    private final ModFileInfo owningFile;
    private final IConfigurable modConfig;
    private final String modId;
    private final String namespace;
    private final ArtifactVersion version;
    private final String displayName;
    private final String description;
    private final Optional<String> logoFile;
    private final boolean logoBlur;
    private final Optional<URL> updateJSONURL;
    private final Optional<URL> modURL;
    private final List<ModVersion> dependencies = new ArrayList<>();
    private final List<ForgeFeature.Bound> features = new ArrayList<>();
    private final Map<String, Object> properties;

    public ModInfo(ModFileInfo owningFile, IConfigurable modConfig, String resolvedVersion) {
        this.owningFile = owningFile;
        this.modConfig = modConfig;
        this.modId = modConfig.<String>getConfigElement("modId").orElseThrow();
        this.namespace = modConfig.<String>getConfigElement("namespace").orElse(modId);
        this.version = new DefaultArtifactVersion(resolvedVersion);
        this.displayName = modConfig.<String>getConfigElement("displayName").orElse(modId);
        this.description = modConfig.<String>getConfigElement("description").orElse("MISSING DESCRIPTION");
        this.logoFile = Optional.ofNullable(modConfig.<String>getConfigElement("logoFile")
                .orElseGet(() -> owningFile.<String>getConfigElement("logoFile").orElse(null)));
        this.logoBlur = modConfig.<Boolean>getConfigElement("logoBlur")
                .orElseGet(() -> owningFile.<Boolean>getConfigElement("logoBlur").orElse(true));
        this.updateJSONURL = modConfig.<String>getConfigElement("updateJSONURL").map(StringUtils::toURL);
        this.modURL = modConfig.<String>getConfigElement("modUrl").map(StringUtils::toURL);
        this.properties = owningFile.<Map<String, Object>>getConfigElement("modproperties", modId).orElse(Map.of());
        for (IConfigurable d : owningFile.getConfigList("dependencies", modId)) {
            dependencies.add(new ModVersion(this, d));
        }
        owningFile.<Map<String, Object>>getConfigElement("features", modId).ifPresent(m ->
                m.forEach((k, v) -> features.add(new ForgeFeature.Bound(k, String.valueOf(v), this))));
    }

    @Override
    public ModFileInfo getOwningFile() {
        return owningFile;
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<? extends IModInfo.ModVersion> getDependencies() {
        return dependencies;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Map<String, Object> getModProperties() {
        return properties;
    }

    @Override
    public Optional<URL> getUpdateURL() {
        return updateJSONURL;
    }

    @Override
    public Optional<String> getLogoFile() {
        return logoFile;
    }

    @Override
    public boolean getLogoBlur() {
        return logoBlur;
    }

    @Override
    public IConfigurable getConfig() {
        return modConfig;
    }

    @Override
    public List<? extends ForgeFeature.Bound> getForgeFeatures() {
        return features;
    }

    @Override
    public <T> Optional<T> getConfigElement(String... key) {
        return modConfig.getConfigElement(key);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(String... key) {
        return modConfig.getConfigList(key);
    }

    @Override
    public Optional<URL> getModURL() {
        return modURL;
    }

    @Override
    public String toString() {
        return modId + "@" + version;
    }

    class ModVersion implements IModInfo.ModVersion {
        private IModInfo owner;
        private final String modId;
        private final VersionRange versionRange;
        private final boolean mandatory;
        private final Ordering ordering;
        private final DependencySide side;
        private final Optional<URL> referralUrl;

        ModVersion(IModInfo owner, IConfigurable config) {
            this.owner = owner;
            this.modId = config.<String>getConfigElement("modId").orElse("");
            VersionRange r;
            try {
                r = MavenVersionAdapter.createFromVersionSpec(config.<String>getConfigElement("versionRange").orElse(""));
            } catch (RuntimeException e) {
                r = IModInfo.UNBOUNDED;
            }
            this.versionRange = r;
            Optional<String> type = config.getConfigElement("type");
            this.mandatory = type.map(t -> t.equalsIgnoreCase("required"))
                    .orElseGet(() -> config.<Boolean>getConfigElement("mandatory").orElse(true));
            this.ordering = parseEnum(Ordering.class, config.<String>getConfigElement("ordering").orElse("NONE"),
                    Ordering.NONE);
            this.side = parseEnum(DependencySide.class, config.<String>getConfigElement("side").orElse("BOTH"),
                    DependencySide.BOTH);
            this.referralUrl = config.<String>getConfigElement("referralUrl").map(StringUtils::toURL);
        }

        private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }

        @Override
        public String getModId() {
            return modId;
        }

        @Override
        public VersionRange getVersionRange() {
            return versionRange;
        }

        @Override
        public boolean isMandatory() {
            return mandatory;
        }

        @Override
        public Ordering getOrdering() {
            return ordering;
        }

        @Override
        public DependencySide getSide() {
            return side;
        }

        @Override
        public void setOwner(IModInfo owner) {
            this.owner = owner;
        }

        @Override
        public IModInfo getOwner() {
            return owner;
        }

        @Override
        public Optional<URL> getReferralURL() {
            return referralUrl;
        }
    }
}
