package dev.nefobric.compat.forge.devtools.remap;

import dev.nefobric.loader.mapping.MappingData;
import dev.nefobric.loader.mapping.MappingReaders;
import dev.nefobric.loader.mapping.Mappings;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCPConfig 的 {@code joined.tsrg}：混淆名 → SRG 成员名。解析用核心的 {@link MappingReaders#readTsrg}，和加载器
 * 重映射游戏时读的是同一份逻辑；类名一列不用（运行时的类名是 Mojang 名）。
 */
public final class TsrgMappings {
    private final MappingData data;

    private TsrgMappings(MappingData data) {
        this.data = data;
    }

    public static TsrgMappings read(BufferedReader in) throws IOException {
        MappingData.Builder b = MappingData.builder(List.of(Mappings.OFFICIAL, Mappings.SRG));
        MappingReaders.readTsrg(in, b, null, Mappings.SRG);
        return new TsrgMappings(b.build(Mappings.SRG));
    }

    /** @return SRG 名；表里没有（或 SRG 名和混淆名相同）时返回 {@code null} */
    public String field(String obfOwner, String obfName) {
        String srg = data.mapFieldInternal(Mappings.OFFICIAL, Mappings.SRG, obfOwner, obfName, null);
        return srg.equals(obfName) ? null : srg;
    }

    /** @return SRG 名；表里没有（或 SRG 名和混淆名相同）时返回 {@code null} */
    public String method(String obfOwner, String obfName, String obfDesc) {
        String srg = data.mapMethodInternal(Mappings.OFFICIAL, Mappings.SRG, obfOwner, obfName, obfDesc);
        return srg.equals(obfName) ? null : srg;
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
