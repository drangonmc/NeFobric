package net.minecraftforge.forgespi.locating;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * mods.toml 里 {@code [features.<modid>]} 声明的特性要求（javaVersion、openGLVersion）。和 Forge 不同的一点：
 * NeFobric 不认识的特性按“满足”处理，不让模组因此加载失败。
 */
public class ForgeFeature {
    private static final Map<String, IFeatureTest<?>> features = new ConcurrentHashMap<>();

    private ForgeFeature() {
    }

    public static <T> void registerFeature(final String featureName, final IFeatureTest<T> featureTest) {
        if (features.putIfAbsent(featureName, featureTest) != null) {
            throw new IllegalArgumentException("ForgeFeature with name " + featureName + " exists");
        }
    }

    public static boolean testFeature(final Dist side, final Bound bound) {
        IFeatureTest<?> test = features.get(bound.featureName());
        try {
            return test == null || test.testSideWithString(side, bound.featureBound());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static Object featureValue(final Bound bound) {
        IFeatureTest<?> test = features.get(bound.featureName());
        return test == null ? "NONE" : test.featureValue();
    }

    public interface IFeatureTest<F> extends Predicate<F> {
        IModInfo.DependencySide applicableSides();

        F convertFromString(final String value);

        String featureValue();

        default boolean testSideWithString(final Dist side, final String value) {
            return !applicableSides().isContained(side) || test(convertFromString(value));
        }
    }

    public record Bound(String featureName, String featureBound, IModInfo modInfo) {
    }

    public record VersionFeatureTest(IModInfo.DependencySide applicableSides, ArtifactVersion version)
            implements IFeatureTest<VersionRange> {
        public static VersionFeatureTest forVersionString(final IModInfo.DependencySide side, final String version) {
            return new VersionFeatureTest(side, new DefaultArtifactVersion(version));
        }

        @Override
        public String featureValue() {
            return version.toString();
        }

        @Override
        public boolean test(final VersionRange versionRange) {
            return versionRange.containsVersion(version);
        }

        @Override
        public VersionRange convertFromString(final String value) {
            try {
                return VersionRange.createFromVersionSpec(value);
            } catch (InvalidVersionSpecificationException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public record BooleanFeatureTest(IModInfo.DependencySide applicableSides, boolean value)
            implements IFeatureTest<Boolean> {
        @Override
        public boolean test(final Boolean b) {
            return b.equals(value);
        }

        @Override
        public String featureValue() {
            return Boolean.toString(value);
        }

        @Override
        public Boolean convertFromString(final String value) {
            return Boolean.parseBoolean(value);
        }
    }
}
