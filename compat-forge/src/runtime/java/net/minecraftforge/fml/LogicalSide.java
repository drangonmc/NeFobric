package net.minecraftforge.fml;

/** 逻辑端：客户端线程还是服务端线程（单人游戏里两者在同一个进程）。 */
public enum LogicalSide {
    CLIENT,
    SERVER;

    public boolean isServer() {
        return !isClient();
    }

    public boolean isClient() {
        return this == CLIENT;
    }
}
