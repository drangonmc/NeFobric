package dev.nefobric.compat.fabric.aw;

import dev.nefobric.compat.fabric.impl.CoreBridge;

import java.util.ArrayList;
import java.util.List;

/** 把 access widener 从文件里的命名空间（一般是 intermediary）换算到运行时命名空间。 */
public final class AccessWidenerRemapper {
    private AccessWidenerRemapper() {
    }

    public static AccessWidener toRuntime(AccessWidener aw) {
        String from = aw.namespace();
        String to = CoreBridge.runtimeNamespace();
        if (from.equals(to)) {
            return aw;
        }
        List<AccessWidener.Entry> out = new ArrayList<>(aw.entries().size());
        for (AccessWidener.Entry e : aw.entries()) {
            String ownerDotted = e.owner().replace('/', '.');
            String owner = mapClassInternal(from, to, e.owner());
            String name = e.name();
            String desc = e.desc();
            if (e.target() == AccessWidener.Target.METHOD) {
                String mapped = CoreBridge.mapMethod(from, to, ownerDotted, e.name(), e.desc());
                name = mapped != null ? mapped : e.name();
                desc = mapDescriptor(from, to, e.desc());
            } else if (e.target() == AccessWidener.Target.FIELD) {
                String mapped = CoreBridge.mapField(from, to, ownerDotted, e.name(), e.desc());
                name = mapped != null ? mapped : e.name();
                desc = mapDescriptor(from, to, e.desc());
            }
            out.add(new AccessWidener.Entry(e.target(), e.access(), e.transitive(), owner, name, desc));
        }
        return new AccessWidener(to, aw.version(), out);
    }

    static String mapClassInternal(String from, String to, String internalName) {
        String mapped = CoreBridge.mapClass(from, to, internalName.replace('/', '.'));
        return mapped != null ? mapped.replace('.', '/') : internalName;
    }

    /** 换算描述符里的所有 {@code Lpkg/Name;}。 */
    static String mapDescriptor(String from, String to, String desc) {
        StringBuilder sb = new StringBuilder(desc.length());
        int i = 0;
        while (i < desc.length()) {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end < 0) {
                    sb.append(desc, i, desc.length());
                    break;
                }
                sb.append('L').append(mapClassInternal(from, to, desc.substring(i + 1, end))).append(';');
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
