package net.fabricmc.loader.api;

import dev.nefobric.compat.fabric.version.VersionParser;

import java.util.Optional;

/** 语义版本（https://semver.org/），允许任意个数字段。 */
public interface SemanticVersion extends Version {
    /** 版本条件里的通配段（x、X、*）。 */
    int COMPONENT_WILDCARD = Integer.MIN_VALUE;

    int getVersionComponentCount();

    int getVersionComponent(int pos);

    Optional<String> getPrereleaseKey();

    Optional<String> getBuildKey();

    boolean hasWildcard();

    @Deprecated
    default int compareTo(SemanticVersion o) {
        return compareTo((Version) o);
    }

    static SemanticVersion parse(String s) throws VersionParsingException {
        return VersionParser.parseSemantic(s);
    }
}
