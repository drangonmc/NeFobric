package dev.nefobric.compat.fabric.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/** 版本号解析：先按语义版本，不行就当普通字符串（与 Fabric Loader 相同）。 */
public final class VersionParser {
    private VersionParser() {
    }

    public static Version parse(String s, boolean storeX) throws VersionParsingException {
        if (s == null || s.isEmpty()) {
            throw new VersionParsingException("Version must be a non-empty string!");
        }
        try {
            return new SemanticVersionImpl(s, storeX);
        } catch (VersionParsingException e) {
            return new StringVersion(s);
        }
    }

    public static SemanticVersion parseSemantic(String s) throws VersionParsingException {
        if (s == null || s.isEmpty()) {
            throw new VersionParsingException("Version must be a non-empty string!");
        }
        return new SemanticVersionImpl(s, false);
    }

    /** 不抛出受检异常的版本，给内部使用。 */
    public static Version parseLenient(String s) {
        try {
            return parse(s, false);
        } catch (VersionParsingException e) {
            return new StringVersion(s == null ? "" : s);
        }
    }
}
