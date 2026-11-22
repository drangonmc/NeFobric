package dev.nefobric.compat.fabric.transform;

import dev.nefobric.loader.classloading.ClassTransformer;
import net.fabricmc.api.EnvType;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 与 Fabric Loader 相同的 {@code @Environment} 剥离：在另一端加载模组类时，去掉标了 {@code @Environment}
 * 的字段和方法、{@code @EnvironmentInterface} 声明的接口；整个类都标了另一端时拒绝加载。
 * 游戏自己的类不处理（发布版原版里没有这些注解）。
 */
public final class EnvironmentStripper implements ClassTransformer {
    static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
    static final String ENVIRONMENT_INTERFACE = "Lnet/fabricmc/api/EnvironmentInterface;";
    static final String ENVIRONMENT_INTERFACES = "Lnet/fabricmc/api/EnvironmentInterfaces;";
    private static final byte[] MARKER = "net/fabricmc/api/Environment".getBytes(StandardCharsets.UTF_8);
    private static final int ASM = Opcodes.ASM9;

    private final String envName;

    public EnvironmentStripper(EnvType env) {
        this.envName = env.name();
    }

    public static boolean isGameClass(String className) {
        return className.startsWith("net.minecraft.") || className.startsWith("com.mojang.")
                || className.indexOf('.') < 0;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (isGameClass(className) || !contains(bytes, MARKER)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        StrippingData data = new StrippingData(envName);
        reader.accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        if (data.stripEntireClass) {
            throw new RuntimeException("Cannot load class " + className + " in environment type " + envName);
        }
        if (data.isEmpty()) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassStripper(writer, data.interfaces, data.fields, data.methods), 0);
        return writer.toByteArray();
    }

    static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0, n = haystack.length - needle.length; i <= n; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** 第一遍：收集需要剥掉的东西。 */
    static final class StrippingData extends ClassVisitor {
        private final String envName;
        boolean stripEntireClass;
        final Set<String> interfaces = new HashSet<>();
        final Set<String> fields = new HashSet<>();
        final Set<String> methods = new HashSet<>();

        StrippingData(String envName) {
            super(ASM);
            this.envName = envName;
        }

        boolean isEmpty() {
            return interfaces.isEmpty() && fields.isEmpty() && methods.isEmpty();
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (ENVIRONMENT.equals(descriptor)) {
                return new EnvironmentVisitor(() -> stripEntireClass = true);
            } else if (ENVIRONMENT_INTERFACE.equals(descriptor)) {
                return new InterfaceVisitor();
            } else if (ENVIRONMENT_INTERFACES.equals(descriptor)) {
                return new AnnotationVisitor(ASM) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name)) {
                            return null;
                        }
                        return new AnnotationVisitor(ASM) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String n, String desc) {
                                return new InterfaceVisitor();
                            }
                        };
                    }
                };
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(ASM) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return ENVIRONMENT.equals(desc) ? new EnvironmentVisitor(() -> fields.add(name + descriptor)) : null;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(ASM) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return ENVIRONMENT.equals(desc) ? new EnvironmentVisitor(() -> methods.add(name + descriptor)) : null;
                }
            };
        }

        /** 读 {@code @Environment(EnvType.X)}，X 不是当前端时执行 onMismatch。 */
        private final class EnvironmentVisitor extends AnnotationVisitor {
            private final Runnable onMismatch;

            EnvironmentVisitor(Runnable onMismatch) {
                super(ASM);
                this.onMismatch = onMismatch;
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                if ("value".equals(name) && !envName.equals(value)) {
                    onMismatch.run();
                }
            }
        }

        /** 读 {@code @EnvironmentInterface(value = EnvType.X, itf = Y.class)}。 */
        private final class InterfaceVisitor extends AnnotationVisitor {
            private String env;
            private Type itf;

            InterfaceVisitor() {
                super(ASM);
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                if ("value".equals(name)) {
                    env = value;
                }
            }

            @Override
            public void visit(String name, Object value) {
                if ("itf".equals(name) && value instanceof Type t) {
                    itf = t;
                }
            }

            @Override
            public void visitEnd() {
                if (env != null && itf != null && !envName.equals(env)) {
                    interfaces.add(itf.getInternalName());
                }
            }
        }
    }

    /** 第二遍：删掉接口、字段和方法。 */
    static final class ClassStripper extends ClassVisitor {
        private final Collection<String> interfaces;
        private final Collection<String> fields;
        private final Collection<String> methods;

        ClassStripper(ClassVisitor next, Collection<String> interfaces, Collection<String> fields, Collection<String> methods) {
            super(ASM, next);
            this.interfaces = interfaces;
            this.fields = fields;
            this.methods = methods;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] itfs) {
            if (itfs != null && !interfaces.isEmpty()) {
                List<String> kept = new ArrayList<>();
                for (String i : itfs) {
                    if (!interfaces.contains(i)) {
                        kept.add(i);
                    }
                }
                // 泛型签名保持原样（与 Fabric Loader 相同），否则会丢掉父类的泛型信息
                itfs = kept.toArray(new String[0]);
            }
            super.visit(version, access, name, signature, superName, itfs);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return fields.contains(name + descriptor) ? null : super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return methods.contains(name + descriptor) ? null : super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
