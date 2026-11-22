package net.minecraftforge.fml.loading.moddiscovery;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.nefobric.forge.fml.TomlConfigurable;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.MavenVersionAdapter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 一个模组 jar 的 mods.toml。 */
public class ModFileInfo implements IModFileInfo, IConfigurable {
    public static final String CLIENT_SIDE_ONLY_PROP = "__FORGE_clientSideOnly";

    private final ModFile file;
    private final TomlConfigurable config;
    private final List<IModInfo> mods = new ArrayList<>();
    private final List<LanguageSpec> languageSpecs;
    private final String moduleName;

    public ModFileInfo(ModFile file, UnmodifiableConfig toml, String moduleName) {
        this.file = file;
        this.config = new TomlConfigurable(toml);
        this.moduleName = moduleName;
        String loader = config.<String>getConfigElement("modLoader").orElse("javafml");
        String version = config.<String>getConfigElement("loaderVersion").orElse("");
        this.languageSpecs = List.of(new LanguageSpec(loader, MavenVersionAdapter.createFromVersionSpec(
                safeRange(version))));
        file.setModFileInfo(this);
        config.<Map<String, Object>>getConfigElement("properties").ifPresent(file::setFileProperties);
    }

    private static String safeRange(String spec) {
        try {
            MavenVersionAdapter.createFromVersionSpec(spec);
            return spec;
        } catch (RuntimeException e) {
            return "";
        }
    }

    public void addMod(IModInfo info) {
        mods.add(info);
    }

    @Override
    public List<IModInfo> getMods() {
        return mods;
    }

    @Override
    public ModFile getFile() {
        return file;
    }

    @Override
    public List<LanguageSpec> requiredLanguageLoaders() {
        return languageSpecs;
    }

    @Override
    public Map<String, Object> getFileProperties() {
        return config.<Map<String, Object>>getConfigElement("properties").orElse(Map.of());
    }

    @Override
    public boolean showAsResourcePack() {
        return config.<Boolean>getConfigElement("showAsResourcePack").orElse(false);
    }

    @Override
    public <T> Optional<T> getConfigElement(String... key) {
        return config.getConfigElement(key);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(String... key) {
        return config.getConfigList(key);
    }

    @Override
    public String getLicense() {
        return config.<String>getConfigElement("license").orElse("");
    }

    @Override
    public IConfigurable getConfig() {
        return this;
    }

    public URL getIssueURL() {
        return config.<String>getConfigElement("issueTrackerURL").map(StringUtils::toURL).orElse(null);
    }

    public boolean missingLicense() {
        return getLicense().isBlank();
    }

    public Optional<String> getCodeSigningFingerprint() {
        return Optional.empty();
    }

    public Optional<String> getTrustData() {
        return Optional.empty();
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public String versionString() {
        return file.getJarVersion().toString();
    }

    @Override
    public List<String> usesServices() {
        return config.<List<String>>getConfigElement("services").orElse(List.of());
    }
}
