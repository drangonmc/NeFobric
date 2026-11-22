package net.minecraftforge.event;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.LogicalSide;

/**
 * 每个游戏刻（或每帧）开始和结束时发在 Forge 总线上的事件。按 {@link #phase} 区分开头和结尾。
 * 服务端、世界、玩家的 tick 事件带原版对象，和原版钩子一起加入。
 */
public class TickEvent extends Event {
    public enum Type {
        LEVEL, PLAYER, CLIENT, SERVER, RENDER
    }

    public enum Phase {
        START, END
    }

    public final Type type;
    public final LogicalSide side;
    public final Phase phase;

    public TickEvent(Type type, LogicalSide side, Phase phase) {
        this.type = type;
        this.side = side;
        this.phase = phase;
    }

    /** 客户端每个游戏刻。 */
    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) {
            super(Type.CLIENT, LogicalSide.CLIENT, phase);
        }
    }

    /** 客户端每帧渲染前后；{@link #renderTickTime} 是帧内插值（partial tick）。 */
    public static class RenderTickEvent extends TickEvent {
        public final float renderTickTime;

        public RenderTickEvent(Phase phase, float renderTickTime) {
            super(Type.RENDER, LogicalSide.CLIENT, phase);
            this.renderTickTime = renderTickTime;
        }
    }
}
