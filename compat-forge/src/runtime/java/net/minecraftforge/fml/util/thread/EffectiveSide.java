package net.minecraftforge.fml.util.thread;

import net.minecraftforge.fml.LogicalSide;

/** 按当前线程所在的线程组判断逻辑端；不在任何 {@link SidedThreadGroup} 里的线程算客户端，和 Forge 一样。 */
public final class EffectiveSide {
    private EffectiveSide() {
    }

    public static LogicalSide get() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        return group instanceof SidedThreadGroup sided ? sided.getSide() : LogicalSide.CLIENT;
    }
}
