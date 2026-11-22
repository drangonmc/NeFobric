package net.minecraftforge.fml.util.thread;

import net.minecraftforge.fml.LogicalSide;

import java.util.concurrent.ThreadFactory;

/** 属于某个逻辑端的线程组；在其中创建的线程，{@link EffectiveSide#get()} 就是这一端。 */
public final class SidedThreadGroup extends ThreadGroup implements ThreadFactory {
    private final LogicalSide side;

    SidedThreadGroup(LogicalSide side) {
        super(side.name());
        this.side = side;
    }

    public LogicalSide getSide() {
        return side;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        return new Thread(this, runnable);
    }
}
