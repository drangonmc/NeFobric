package dev.nefobric.loader.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 以 {@value Mappings#OFFICIAL}（原版混淆名）为中心的映射表。每个类和成员在每个命名空间里各有一个名字；
 * 查询时先把来源命名空间的名字换回官方名，再换成目标命名空间的名字。
 *
 * <p>内部方法（带 {@code internal} 字样）用斜杠形式的类名；{@link Mappings} 接口的方法用点分形式。
 */
public final class MappingData implements Mappings {
    /** intermediary 的成员名全局唯一，可以不看所属类直接换名（Mixin 注解、refmap、反射字符串都靠这一点）。 */
    static final Pattern UNIQUE_MEMBER = Pattern.compile("(?:method|field|comp)_\\d+");

    private final List<String> namespaces;
    private final String runtimeNs;
    private final Map<String, String[]> classes;
    private final List<Map<String, String>> classToOfficial;
    private final Map<String, String[]> fields;
    private final Map<String, String[]> methods;
    private final List<Map<String, String>> fieldIndex;
    private final List<Map<String, String>> methodIndex;
    private final List<Map<String, String>> uniqueFields;
    private final List<Map<String, String>> uniqueMethods;
    private final int uniqueConflicts;

    private MappingData(Builder b, String runtimeNs) {
        this.namespaces = List.copyOf(b.namespaces);
        this.runtimeNs = runtimeNs;
        this.classes = b.classes;
        this.fields = b.fields;
        this.methods = b.methods;
        int n = namespaces.size();
        classToOfficial = new ArrayList<>(n);
        fieldIndex = new ArrayList<>(n);
        methodIndex = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            classToOfficial.add(new HashMap<>());
            fieldIndex.add(new HashMap<>());
            methodIndex.add(new HashMap<>());
        }
        classes.forEach((official, names) -> {
            for (int i = 0; i < n; i++) {
                classToOfficial.get(i).putIfAbsent(nameOr(names, i, official), official);
            }
        });
        fields.forEach((key, names) -> {
            int dot = key.indexOf('.');
            String owner = key.substring(0, dot);
            String official = key.substring(dot + 1);
            for (int i = 0; i < n; i++) {
                fieldIndex.get(i).putIfAbsent(owner + '.' + nameOr(names, i, official), key);
            }
        });
        methods.forEach((key, names) -> {
            int dot = key.indexOf('.');
            int paren = key.indexOf('(', dot);
            String owner = key.substring(0, dot);
            String official = key.substring(dot + 1, paren);
            String desc = key.substring(paren);
            for (int i = 0; i < n; i++) {
                methodIndex.get(i).putIfAbsent(owner + '.' + nameOr(names, i, official) + desc, key);
            }
        });
        // 唯一名索引：只对成员名全局唯一的命名空间有意义（intermediary）
        uniqueFields = new ArrayList<>(n);
        uniqueMethods = new ArrayList<>(n);
        int conflicts = 0;
        for (int i = 0; i < n; i++) {
            Map<String, String> uf = new HashMap<>();
            Map<String, String> um = new HashMap<>();
            conflicts += buildUnique(fields, i, uf, false);
            conflicts += buildUnique(methods, i, um, true);
            uniqueFields.add(uf);
            uniqueMethods.add(um);
        }
        this.uniqueConflicts = conflicts;
    }

    private static String nameOr(String[] names, int i, String fallback) {
        String s = names[i];
        return s == null ? fallback : s;
    }

    /** 唯一名 → 官方成员键。同一个唯一名对应多个官方成员时（同一方法族的重写），它们的其他名字也应一致。 */
    private static int buildUnique(Map<String, String[]> members, int ns, Map<String, String> out, boolean method) {
        int conflicts = 0;
        for (Map.Entry<String, String[]> e : members.entrySet()) {
            String name = e.getValue()[ns];
            if (name == null || !UNIQUE_MEMBER.matcher(name).matches()) {
                continue;
            }
            String prev = out.putIfAbsent(name, e.getKey());
            if (prev != null && !prev.equals(e.getKey())) {
                String[] a = members.get(prev);
                String[] b = e.getValue();
                for (int i = 0; i < a.length; i++) {
                    if (a[i] != null && b[i] != null && !a[i].equals(b[i])) {
                        conflicts++;
                        break;
                    }
                }
            }
        }
        return conflicts;
    }

    public static Builder builder(List<String> namespaces) {
        return new Builder(namespaces);
    }

    // ---- Mappings ----

    @Override
    public String runtimeNamespace() {
        return runtimeNs;
    }

    @Override
    public List<String> namespaces() {
        return namespaces;
    }

    @Override
    public boolean isIdentity() {
        return false;
    }

    @Override
    public String mapClass(String fromNs, String toNs, String className) {
        return mapClassInternal(fromNs, toNs, className.replace('.', '/')).replace('/', '.');
    }

    @Override
    public String mapField(String fromNs, String toNs, String owner, String name, String desc) {
        return mapFieldInternal(fromNs, toNs, owner.replace('.', '/'), name, desc);
    }

    @Override
    public String mapMethod(String fromNs, String toNs, String owner, String name, String desc) {
        return mapMethodInternal(fromNs, toNs, owner.replace('.', '/'), name, desc);
    }

    // ---- 内部形式 ----

    public int namespaceIndex(String ns) {
        int i = namespaces.indexOf(ns);
        if (i < 0) {
            throw new IllegalArgumentException("Unknown mapping namespace " + ns + " (have " + namespaces + ")");
        }
        return i;
    }

    public boolean hasNamespace(String ns) {
        return namespaces.contains(ns);
    }

    /** 唯一名索引里互相矛盾的条目数（诊断用）。 */
    public int uniqueConflicts() {
        return uniqueConflicts;
    }

    public int classCount() {
        return classes.size();
    }

    public int fieldCount() {
        return fields.size();
    }

    public int methodCount() {
        return methods.size();
    }

    /** 斜杠形式的类名；内部类如果没有单独的映射，就映射外部类再接上后缀。 */
    public String mapClassInternal(String fromNs, String toNs, String name) {
        if (fromNs.equals(toNs)) {
            return name;
        }
        int from = namespaceIndex(fromNs);
        int to = namespaceIndex(toNs);
        return mapClassInternal(from, to, name);
    }

    String mapClassInternal(int from, int to, String name) {
        String official = classToOfficial.get(from).get(name);
        if (official != null) {
            return nameOr(classes.get(official), to, official);
        }
        int dollar = name.lastIndexOf('$');
        if (dollar > 0) {
            String outer = mapClassInternal(from, to, name.substring(0, dollar));
            if (!outer.equals(name.substring(0, dollar))) {
                return outer + name.substring(dollar);
            }
        }
        return name;
    }

    /** 斜杠形式的类名换成官方名；不是游戏类时返回 {@code null}。 */
    String toOfficial(int from, String name) {
        if (from == 0) {
            return classes.containsKey(name) ? name : null;
        }
        return classToOfficial.get(from).get(name);
    }

    String[] classNames(String official) {
        return classes.get(official);
    }

    /** 官方成员键 {@code owner.name}（字段）的各命名空间名字。 */
    String[] fieldNames(String officialOwner, String officialName) {
        return fields.get(officialOwner + '.' + officialName);
    }

    /** 官方成员键 {@code owner.name(desc)}（方法）的各命名空间名字。 */
    String[] methodNames(String officialOwner, String officialName, String officialDesc) {
        return methods.get(officialOwner + '.' + officialName + officialDesc);
    }

    public String mapFieldInternal(String fromNs, String toNs, String owner, String name, String desc) {
        if (fromNs.equals(toNs)) {
            return name;
        }
        int from = namespaceIndex(fromNs);
        int to = namespaceIndex(toNs);
        String unique = mapUniqueField(from, to, name);
        if (unique != null) {
            return unique;
        }
        String ownerOff = toOfficial(from, owner);
        if (ownerOff == null) {
            return name;
        }
        String key = fieldIndex.get(from).get(ownerOff + '.' + name);
        return key == null ? name : nameOr(fields.get(key), to, key.substring(key.indexOf('.') + 1));
    }

    public String mapMethodInternal(String fromNs, String toNs, String owner, String name, String desc) {
        if (fromNs.equals(toNs)) {
            return name;
        }
        int from = namespaceIndex(fromNs);
        int to = namespaceIndex(toNs);
        String unique = mapUniqueMethod(from, to, name);
        if (unique != null) {
            return unique;
        }
        String ownerOff = toOfficial(from, owner);
        if (ownerOff == null || desc == null) {
            return name;
        }
        String descOff = mapDesc(from, 0, desc);
        String key = methodIndex.get(from).get(ownerOff + '.' + name + descOff);
        if (key == null) {
            return name;
        }
        String official = key.substring(key.indexOf('.') + 1, key.indexOf('('));
        return nameOr(methods.get(key), to, official);
    }

    /** 按唯一名换字段名；来源命名空间里不是唯一名时返回 {@code null}。 */
    String mapUniqueField(int from, int to, String name) {
        if (!UNIQUE_MEMBER.matcher(name).matches()) {
            return null;
        }
        String key = uniqueFields.get(from).get(name);
        if (key == null) {
            return null;
        }
        return nameOr(fields.get(key), to, key.substring(key.indexOf('.') + 1));
    }

    String mapUniqueMethod(int from, int to, String name) {
        if (!UNIQUE_MEMBER.matcher(name).matches()) {
            return null;
        }
        String key = uniqueMethods.get(from).get(name);
        if (key == null) {
            return null;
        }
        String official = key.substring(key.indexOf('.') + 1, key.indexOf('('));
        return nameOr(methods.get(key), to, official);
    }

    /** 把描述符里的类名从一个命名空间换到另一个。 */
    String mapDesc(int from, int to, String desc) {
        if (from == to || desc.indexOf('L') < 0) {
            return desc;
        }
        StringBuilder sb = new StringBuilder(desc.length() + 16);
        int i = 0;
        while (i < desc.length()) {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                sb.append('L').append(mapClassInternal(from, to, desc.substring(i + 1, end))).append(';');
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    public String mapDescInternal(String fromNs, String toNs, String desc) {
        return mapDesc(namespaceIndex(fromNs), namespaceIndex(toNs), desc);
    }

    /** 逐个添加官方名到各命名空间名字的对应。重复添加同一个名字时后加的覆盖先加的。 */
    public static final class Builder {
        private final List<String> namespaces;
        private final Map<String, String[]> classes = new HashMap<>();
        private final Map<String, String[]> fields = new HashMap<>();
        private final Map<String, String[]> methods = new HashMap<>();

        private Builder(List<String> namespaces) {
            if (namespaces.isEmpty() || !OFFICIAL.equals(namespaces.get(0))) {
                throw new IllegalArgumentException("The first namespace must be " + OFFICIAL);
            }
            this.namespaces = List.copyOf(namespaces);
        }

        private String[] slot(Map<String, String[]> map, String key, String official) {
            return map.computeIfAbsent(key, k -> {
                String[] a = new String[namespaces.size()];
                a[0] = official;
                return a;
            });
        }

        public Builder putClass(String official, String ns, String name) {
            slot(classes, official, official)[index(ns)] = name;
            return this;
        }

        public Builder putField(String officialOwner, String officialName, String ns, String name) {
            slot(classes, officialOwner, officialOwner);
            slot(fields, officialOwner + '.' + officialName, officialName)[index(ns)] = name;
            return this;
        }

        public Builder putMethod(String officialOwner, String officialName, String officialDesc, String ns,
                                 String name) {
            slot(classes, officialOwner, officialOwner);
            slot(methods, officialOwner + '.' + officialName + officialDesc, officialName)[index(ns)] = name;
            return this;
        }

        public boolean hasClass(String official) {
            return classes.containsKey(official);
        }

        private int index(String ns) {
            int i = namespaces.indexOf(ns);
            if (i <= 0) {
                throw new IllegalArgumentException("Unknown or official namespace " + ns);
            }
            return i;
        }

        public MappingData build(String runtimeNs) {
            if (!namespaces.contains(runtimeNs)) {
                throw new IllegalArgumentException("Runtime namespace " + runtimeNs + " is not in " + namespaces);
            }
            return new MappingData(this, runtimeNs);
        }
    }
}
