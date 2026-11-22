package dev.nefobric.forge.loading;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;

/** LaunchPluginTest 的目标类：{@link #pick} 的分支汇合处要重算栈帧（公共父类是 AbstractList）。 */
public class PluginFixture {
    public static String value() {
        return "original";
    }

    public static Object pick(boolean array) {
        AbstractList<String> list;
        if (array) {
            list = new ArrayList<>();
        } else {
            list = new LinkedList<>();
        }
        list.add(value());
        return list;
    }
}
