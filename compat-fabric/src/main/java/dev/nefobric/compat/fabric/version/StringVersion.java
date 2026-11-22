package dev.nefobric.compat.fabric.version;

import net.fabricmc.loader.api.Version;

/** 不符合语义版本规则的版本号，只能按字符串比较。 */
public final class StringVersion implements Version {
    private final String version;

    public StringVersion(String version) {
        this.version = version;
    }

    @Override
    public String getFriendlyString() {
        return version;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StringVersion other && version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public int compareTo(Version o) {
        return getFriendlyString().compareTo(o.getFriendlyString());
    }

    @Override
    public String toString() {
        return version;
    }
}
