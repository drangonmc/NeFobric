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
    private static final byte[] NEEDLE = MOD_DESC.getBytes(StandardCharsets.UTF_8);

    private ModAnnotationScanner() {
    }

    /** 返回 modId → 主类二进制名。 */
    static Map<String, String> scan(ModJar jar) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String entry : jar.classEntries()) {
            Optional<byte[]> bytes = jar.read(entry);
            if (bytes.isEmpty() || !contains(bytes.get(), NEEDLE)) {
                continue;
            }
            ClassReader reader = new ClassReader(bytes.get());
            String[] modId = new String[1];
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!MOD_DESC.equals(descriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("value".equals(name)) {
                                modId[0] = String.valueOf(value);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (modId[0] != null) {
                out.putIfAbsent(modId[0], reader.getClassName().replace('/', '.'));
            }
        }
        return out;
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
