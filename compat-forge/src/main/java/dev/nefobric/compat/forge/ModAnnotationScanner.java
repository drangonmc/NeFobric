package dev.nefobric.compat.forge;

import dev.nefobric.loader.discovery.ModJar;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 像 Forge 的 ModFileScanData 一样，用 ASM 扫描 jar 里的类找 {@code @Mod} 注解，不需要先加载类。
 * 先在字节里查找注解描述符，跳过绝大多数不相关的类。
 */
final class ModAnnotationScanner {
    static final String MOD_DESC = "Lnet/minecraftforge/fml/common/Mod;";
    /** NeoForge 20.2（Minecraft 1.20.2）起改名后的 {@code @Mod}；出现它说明这不是给 1.20.1 用的模组。 */
    static final String NEO_MOD_DESC = "Lnet/neoforged/fml/common/Mod;";
    private static final byte[] NEEDLE = MOD_DESC.getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEO_NEEDLE = NEO_MOD_DESC.getBytes(StandardCharsets.UTF_8);

    /**
     * @param modClasses    modId → 带 Forge 1.20.1 {@code @Mod} 的主类二进制名
     * @param neoModClasses modId → 带 NeoForge 20.2+ {@code @Mod}（net.neoforged）的主类二进制名
     */
    record Result(Map<String, String> modClasses, Map<String, String> neoModClasses) {
    }

    private ModAnnotationScanner() {
    }

    static Result scan(ModJar jar) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> neo = new LinkedHashMap<>();
        for (String entry : jar.classEntries()) {
            Optional<byte[]> bytes = jar.read(entry);
            if (bytes.isEmpty()) {
                continue;
            }
            boolean forge = contains(bytes.get(), NEEDLE);
            boolean neoForge = contains(bytes.get(), NEO_NEEDLE);
            if (!forge && !neoForge) {
                continue;
            }
            ClassReader reader = new ClassReader(bytes.get());
            String[] modId = new String[2];
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    int slot;
                    if (MOD_DESC.equals(descriptor)) {
                        slot = 0;
                    } else if (NEO_MOD_DESC.equals(descriptor)) {
                        slot = 1;
                    } else {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("value".equals(name)) {
                                modId[slot] = String.valueOf(value);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            String className = reader.getClassName().replace('/', '.');
            if (modId[0] != null) {
                out.putIfAbsent(modId[0], className);
            }
            if (modId[1] != null) {
                neo.putIfAbsent(modId[1], className);
            }
        }
        return new Result(out, neo);
    }

    static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
