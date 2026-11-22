package net.minecraftforge.forgespi.language;

import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.List;
import java.util.Map;

/** 一个模组 jar 的元数据（整个 mods.toml）。 */
public interface IModFileInfo {
    List<IModInfo> getMods();

    record LanguageSpec(String languageName, VersionRange acceptedVersions) {
    }

    List<LanguageSpec> requiredLanguageLoaders();

    boolean showAsResourcePack();

    Map<String, Object> getFileProperties();

    String getLicense();

    String moduleName();

    String versionString();

    List<String> usesServices();

    IModFile getFile();

    IConfigurable getConfig();
}
