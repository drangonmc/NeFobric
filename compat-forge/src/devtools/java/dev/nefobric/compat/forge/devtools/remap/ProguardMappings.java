package dev.nefobric.compat.forge.devtools.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mojang 官方映射（ProGuard 格式，{@code client.txt}）：Mojang 名和混淆名互查。类名都是内部形式
 * （{@code net/minecraft/client/Minecraft}）。字段按名字查，方法按名字加描述符查。
 * <pre>
 * net.minecraft.client.Minecraft -> enn:
 *     java.io.File gameDirectory -> p
 *     12:34:void tick() -> s
 *     net.minecraft.world.entity.Entity getCameraEntity() -> ak
 * </pre>
 */
public final class ProguardMappings {
    final Map<String, String> namedToObf = new HashMap<>();
    final Map<String, String> obfToNamed = new HashMap<>();
    /** 以混淆类名为键。 */
    final Map<String, Map<String, String>> fieldsObfToNamed = new HashMap<>();
    final Map<String, Map<String, String>> methodsObfToNamed = new HashMap<>();
    final Map<String, Map<String, String>> fieldsNamedToObf = new HashMap<>();
    final Map<String, Map<String, String>> methodsNamedToObf = new HashMap<>();

    private ProguardMappings() {
    }

    private record Member(String obfOwner, String type, String name, String args, String obf) {
    }

    public static ProguardMappings read(BufferedReader in) throws IOException {
        ProguardMappings m = new ProguardMappings();
        List<Member> members = new ArrayList<>();
        String owner = null;
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                String named = line.substring(0, arrow).trim().replace('.', '/');
                String obf = line.substring(arrow + 4).trim();
                if (obf.endsWith(":")) {
                    obf = obf.substring(0, obf.length() - 1);
                }
                obf = obf.replace('.', '/');
                m.namedToObf.put(named, obf);
                m.obfToNamed.put(obf, named);
                owner = obf;
                continue;
            }
            if (owner == null) {
                continue;
            }
            String left = line.substring(0, arrow).trim();
            String obfName = line.substring(arrow + 4).trim();
            // 行号前缀 “12:34:”
            while (!left.isEmpty() && Character.isDigit(left.charAt(0))) {
                int colon = left.indexOf(':');
                if (colon < 0) {
                    break;
                }
                left = left.substring(colon + 1);
            }
            int space = left.indexOf(' ');
            if (space < 0) {
                continue;
            }
            String type = left.substring(0, space);
            String rest = left.substring(space + 1);
            int paren = rest.indexOf('(');
            if (paren < 0) {
                members.add(new Member(owner, type, rest, null, obfName));
            } else {
                int close = rest.indexOf(')', paren);
                members.add(new Member(owner, type, rest.substring(0, paren), rest.substring(paren + 1, close), obfName));
            }
        }
        for (Member mem : members) {
            if (mem.args == null) {
                m.fieldsObfToNamed.computeIfAbsent(mem.obfOwner, k -> new HashMap<>()).put(mem.obf, mem.name);
                m.fieldsNamedToObf.computeIfAbsent(mem.obfOwner, k -> new HashMap<>()).put(mem.name, mem.obf);
            } else {
                StringBuilder named = new StringBuilder("(");
                StringBuilder obf = new StringBuilder("(");
                if (!mem.args.isEmpty()) {
                    for (String a : mem.args.split(",")) {
                        named.append(descriptor(a.trim(), null));
                        obf.append(descriptor(a.trim(), m.namedToObf));
                    }
                }
                named.append(')').append(descriptor(mem.type, null));
                obf.append(')').append(descriptor(mem.type, m.namedToObf));
                m.methodsObfToNamed.computeIfAbsent(mem.obfOwner, k -> new HashMap<>()).put(mem.obf + obf, mem.name);
                m.methodsNamedToObf.computeIfAbsent(mem.obfOwner, k -> new HashMap<>()).put(mem.name + named, mem.obf);
            }
        }
        return m;
    }

    /** Java 源码里的类型（{@code int}、{@code java.lang.String[]}）转成描述符；给了类名表时换成混淆名。 */
    static String descriptor(String javaType, Map<String, String> classMap) {
        StringBuilder sb = new StringBuilder();
        String t = javaType;
        while (t.endsWith("[]")) {
            sb.append('[');
            t = t.substring(0, t.length() - 2);
        }
        switch (t) {
            case "void" -> sb.append('V');
            case "boolean" -> sb.append('Z');
            case "byte" -> sb.append('B');
            case "char" -> sb.append('C');
            case "short" -> sb.append('S');
            case "int" -> sb.append('I');
            case "long" -> sb.append('J');
            case "float" -> sb.append('F');
            case "double" -> sb.append('D');
            default -> {
                String internal = t.replace('.', '/');
                if (classMap != null) {
                    internal = classMap.getOrDefault(internal, internal);
                }
                sb.append('L').append(internal).append(';');
            }
        }
        return sb.toString();
    }

    public int classCount() {
        return namedToObf.size();
    }

    /** 混淆名 → Mojang 名，用来生成编译用的游戏 jar。 */
    public MemberMap obfToNamed() {
        return new MemberMap() {
            @Override
            public String field(String owner, String name, String desc) {
                Map<String, String> f = fieldsObfToNamed.get(owner);
                return f == null ? null : f.get(name);
            }

            @Override
            public String method(String owner, String name, String desc) {
                Map<String, String> ms = methodsObfToNamed.get(owner);
                return ms == null ? null : ms.get(name + desc);
            }

            @Override
            public Map<String, String> methodsOf(String owner) {
                return methodsObfToNamed.getOrDefault(owner, Map.of());
            }
        };
    }

    public Map<String, String> classesObfToNamed() {
        return obfToNamed;
    }
}
