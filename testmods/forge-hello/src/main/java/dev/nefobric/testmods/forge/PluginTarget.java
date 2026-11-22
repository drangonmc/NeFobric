package dev.nefobric.testmods.forge;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;

/** HelloLaunchPlugin 改的类。分支汇合处的栈帧要重算（公共父类是 AbstractList）。 */
public final class PluginTarget {
    private PluginTarget() {
    }

    public static String value(boolean array) {
        AbstractList<String> list;
        if (array) {
            list = new ArrayList<>();
        } else {
            list = new LinkedList<>();
        }
        list.add("original");
        return list.get(0) + " (" + list.getClass().getSimpleName() + ")";
    }
}
