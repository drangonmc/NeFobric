package net.minecraftforge.forgespi.language;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/** 把 mods.toml 里的版本区间字符串解析成 Maven 的 {@link VersionRange}。 */
public final class MavenVersionAdapter {
    private MavenVersionAdapter() {
    }

    public static VersionRange createFromVersionSpec(String spec) {
        try {
            return VersionRange.createFromVersionSpec(spec);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }
}
