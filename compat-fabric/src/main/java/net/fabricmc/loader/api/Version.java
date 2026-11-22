package net.fabricmc.loader.api;

import dev.nefobric.compat.fabric.version.VersionParser;

/** 模组版本号；不符合语义版本规则时按普通字符串处理。 */
public interface Version extends Comparable<Version> {
    String getFriendlyString();

    static Version parse(String string) throws VersionParsingException {
        return VersionParser.parse(string, false);
    }
}
