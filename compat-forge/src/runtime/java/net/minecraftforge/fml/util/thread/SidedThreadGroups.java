package net.minecraftforge.fml.util.thread;

import net.minecraftforge.fml.LogicalSide;

/** 两个逻辑端的线程组。服务端线程（专用服务端和单人游戏的内置服务端）由原版补丁放进 {@link #SERVER}。 */
public final class SidedThreadGroups {
    public static final SidedThreadGroup CLIENT = new SidedThreadGroup(LogicalSide.CLIENT);
    public static final SidedThreadGroup SERVER = new SidedThreadGroup(LogicalSide.SERVER);

    private SidedThreadGroups() {
    }
}
