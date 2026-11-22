package net.minecraftforge.api.distmarker;

/** 物理端：客户端（带图形界面）或专用服务端。 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isDedicatedServer() {
        return !isClient();
    }

    public boolean isClient() {
        return this == CLIENT;
    }
}
