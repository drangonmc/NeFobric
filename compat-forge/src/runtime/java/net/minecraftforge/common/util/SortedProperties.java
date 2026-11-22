package net.minecraftforge.common.util;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** 按键排序写出的 {@link Properties}，让生成的 .properties 文件稳定。 */
public class SortedProperties extends Properties {
    @Override
    public synchronized Enumeration<Object> keys() {
        return Collections.enumeration(keySet());
    }

    @Override
    public Set<Object> keySet() {
        TreeSet<Object> sorted = new TreeSet<>(Comparator.comparing(Object::toString));
        sorted.addAll(super.keySet());
        return Collections.synchronizedSet(sorted);
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        TreeSet<Map.Entry<Object, Object>> sorted = new TreeSet<>(Comparator.comparing(e -> e.getKey().toString()));
        sorted.addAll(super.entrySet());
        return Collections.synchronizedSet(new LinkedHashSet<>(sorted));
    }

    public static void store(Properties props, Writer writer, String comments) throws IOException {
        SortedProperties sorted = new SortedProperties();
        sorted.putAll(props);
        sorted.store(writer, comments);
    }
}
