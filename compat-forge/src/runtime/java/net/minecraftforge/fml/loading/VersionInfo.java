package net.minecraftforge.fml.loading;

/** Forge、Minecraft 和 MCP 的版本号。 */
public record VersionInfo(String forgeVersion, String mcVersion, String mcpVersion, String forgeGroup) {
    public String mcAndForgeVersion() {
        return mcVersion + "-" + forgeVersion;
    }

    public String mcAndMCPVersion() {
        return mcVersion + "-" + mcpVersion;
    }

    /** NeoForge 的 FML 47.2 把 FML 版本单独列出来；Forge 47 里 FML 和 Forge 同一个版本号。 */
    public String fmlVersion() {
        return forgeVersion;
    }

    /** NeoForge 的 FML 47.2 多出来的方法。 */
    public String mcAndFmlVersion() {
        return mcVersion + "-" + fmlVersion();
    }
}
