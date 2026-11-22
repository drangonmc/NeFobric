package net.minecraftforge.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 流体等发声的一种场合（装桶、倒出、蒸发）。同名只有一个对象。 */
public final class SoundAction {
    private static final Map<String, SoundAction> ACTIONS = new ConcurrentHashMap<>();

    private final String name;

    private SoundAction(String name) {
        this.name = name;
    }

    public static SoundAction get(String name) {
        return ACTIONS.computeIfAbsent(name, SoundAction::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "SoundAction[" + name + "]";
    }
}
