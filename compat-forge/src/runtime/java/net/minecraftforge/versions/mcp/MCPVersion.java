package net.minecraftforge.versions.mcp;

import dev.nefobric.forge.fml.ForgeEnvironment;

public class MCPVersion {
    public static String getMCVersion() {
        return ForgeEnvironment.MC_VERSION;
    }

    public static String getMCPVersion() {
        return ForgeEnvironment.MCP_VERSION;
    }

    public static String getMCPandMCVersion() {
        return getMCVersion() + "-" + getMCPVersion();
    }
}
