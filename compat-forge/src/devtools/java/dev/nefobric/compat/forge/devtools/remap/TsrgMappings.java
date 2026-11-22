package dev.nefobric.compat.forge.devtools.remap;

import org.objectweb.asm.commons.SimpleRemapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * MCPConfig 的 {@code joined.tsrg}（TSRG2，首行 {@code tsrg2 obf srg id}）：混淆名 → SRG 成员名。
 * 类名一列不用（运行时的类名是 Mojang 名）。
 */
public final class TsrgMappings {
    final Map<String, Map<String, String>> fields = new HashMap<>();
    final Map<String, Map<String, String>> methods = new HashMap<>();

    private TsrgMappings() {
    }

    public static TsrgMappings read(BufferedReader in) throws IOException {
        TsrgMappings m = new TsrgMappings();
        String owner = null;
        String first = in.readLine();
        if (first != null && !first.startsWith("tsrg2")) {
            owner = classLine(first);
        }
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("\t\t")) {
                continue;
            }
            if (!line.startsWith("\t")) {
                owner = classLine(line);
                continue;
            }
            String[] p = line.substring(1).split(" ");
            if (p.length >= 3 && p[1].startsWith("(")) {
                m.methods.computeIfAbsent(owner, k -> new HashMap<>()).put(p[0] + p[1], p[2]);
            } else if (p.length >= 3 && !p[1].startsWith("(") && !isNumber(p[2])) {
                // 带描述符的字段：obf desc srg [id]
                m.fields.computeIfAbsent(owner, k -> new HashMap<>()).put(p[0], p[2]);
            } else if (p.length >= 2) {
                m.fields.computeIfAbsent(owner, k -> new HashMap<>()).put(p[0], p[1]);
            }
        }
        return m;
    }

    private static String classLine(String line) {
        return line.split(" ")[0];
    }

    private static boolean isNumber(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    public String field(String obfOwner, String obfName) {
        Map<String, String> f = fields.get(obfOwner);
        return f == null ? null : f.get(obfName);
    }

    public String method(String obfOwner, String obfName, String obfDesc) {
        Map<String, String> ms = methods.get(obfOwner);
        return ms == null ? null : ms.get(obfName + obfDesc);
    }

    /**
     * Mojang 名 → SRG 成员名（类名不变），用来把按 Mojang 名编译的代码转成运行时命名。
     */
    public MemberMap namedToSrg(ProguardMappings mojang) {
        SimpleRemapper toObf = new SimpleRemapper(mojang.namedToObf);
        return new MemberMap() {
            @Override
            public String field(String owner, String name, String desc) {
                String obfOwner = mojang.namedToObf.get(owner);
                Map<String, String> f = obfOwner == null ? null : mojang.fieldsNamedToObf.get(obfOwner);
                String obf = f == null ? null : f.get(name);
                return obf == null ? null : TsrgMappings.this.field(obfOwner, obf);
            }

            @Override
            public String method(String owner, String name, String desc) {
                String obfOwner = mojang.namedToObf.get(owner);
                Map<String, String> ms = obfOwner == null ? null : mojang.methodsNamedToObf.get(obfOwner);
                String obf = ms == null ? null : ms.get(name + desc);
                return obf == null ? null : TsrgMappings.this.method(obfOwner, obf, obfDesc(desc));
            }

            @Override
            public Map<String, String> methodsOf(String owner) {
                String obfOwner = mojang.namedToObf.get(owner);
                Map<String, String> ms = obfOwner == null ? null : mojang.methodsNamedToObf.get(obfOwner);
                if (ms == null) {
                    return Map.of();
                }
                Map<String, String> out = new HashMap<>();
                ms.forEach((key, obf) -> {
                    int paren = key.indexOf('(');
                    String srg = TsrgMappings.this.method(obfOwner, obf, obfDesc(key.substring(paren)));
                    if (srg != null) {
                        out.put(key, srg);
                    }
                });
                return out;
            }

            private String obfDesc(String desc) {
                return toObf.mapMethodDesc(desc);
            }
        };
    }
}
