package net.minecraftforge.forgespi.language;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 一个模组的元数据（mods.toml 里的一个 [[mods]] 表）。 */
public interface IModInfo {
    VersionRange UNBOUNDED = MavenVersionAdapter.createFromVersionSpec("");

    IModFileInfo getOwningFile();

    String getModId();

    String getDisplayName();

    String getDescription();

    ArtifactVersion getVersion();

    List<? extends ModVersion> getDependencies();

    List<? extends ForgeFeature.Bound> getForgeFeatures();

    String getNamespace();

    Map<String, Object> getModProperties();

    Optional<URL> getUpdateURL();

    Optional<URL> getModURL();

    Optional<String> getLogoFile();

    boolean getLogoBlur();

    IConfigurable getConfig();

    enum Ordering {
        BEFORE, AFTER, NONE
    }

    enum DependencySide {
        CLIENT(Dist.CLIENT), SERVER(Dist.DEDICATED_SERVER), BOTH(Dist.values());

        private final Dist[] dist;

        DependencySide(Dist... dist) {
            this.dist = dist;
        }

        public boolean isContained(Dist side) {
            return Arrays.asList(dist).contains(side);
        }

        public boolean isCorrectSide() {
            return this == BOTH || isContained(net.minecraftforge.fml.loading.FMLEnvironment.dist);
        }
    }

    interface ModVersion {
        String getModId();

        VersionRange getVersionRange();

        boolean isMandatory();

        Ordering getOrdering();

        DependencySide getSide();

        void setOwner(IModInfo owner);

        IModInfo getOwner();

        Optional<URL> getReferralURL();
    }
}
