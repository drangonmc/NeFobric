package net.minecraftforge.common;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 工具能做的一种动作（挖掘、削皮、耕地……）。同名只有一个对象，可以用 {@code ==} 比较。 */
public final class ToolAction {
    private static final Map<String, ToolAction> ACTIONS = new ConcurrentHashMap<>();

    private final String name;

    private ToolAction(String name) {
        this.name = name;
    }

    /** 所有已经创建的动作。 */
    public static Collection<ToolAction> getActions() {
        return Collections.unmodifiableCollection(ACTIONS.values());
    }

    /** 按名字取，没有就创建。名字建议带模组前缀。 */
    public static ToolAction get(String name) {
        return ACTIONS.computeIfAbsent(name, ToolAction::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "ToolAction[" + name + "]";
    }
}
