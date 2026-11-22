package dev.nefobric.forge.fml;

import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 扫描模组 jar 里所有类的继承关系和注解，产出和 Forge 一样形状的 {@link ModFileScanData}：
 * 类注解的 member 是类名，字段注解是字段名，方法注解是 “方法名+描述符”；枚举值是
 * {@link ModAnnotation.EnumHolder}，数组是 List，嵌套注解既作为父注解里的 Map，也单独出现一条。
 */
public final class ModScanner {
    private ModScanner() {
    }

    public static ModFileScanData scan(ModFile file) {
        ModFileScanData result = new ModFileScanData();
        if (file.getModFileInfo() != null) {
            result.addModFileInfo(file.getModFileInfo());
        }
        try (ZipFile zip = new ZipFile(file.getFilePath().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/")
                        || name.endsWith("module-info.class") || name.endsWith("package-info.class")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(e)) {
                    scanClass(in.readAllBytes(), result);
                } catch (IOException | RuntimeException ignored) {
                    // 坏类文件：和 Forge 一样跳过
                }
            }
        } catch (IOException e) {
            // 读不了的 jar：返回空结果
        }
        return result;
    }

    public static void scanClass(byte[] bytes, ModFileScanData result) {
        ClassScan cv = new ClassScan();
        new ClassReader(bytes).accept(cv, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        cv.emit(result.getClasses(), result.getAnnotations());
    }

    private static final class ClassScan extends ClassVisitor {
        private Type type;
        private Type superType;
        private Set<Type> interfaces;
        /** 读到的注解，嵌套注解读完后放到末尾，和 Forge 的顺序一致。 */
        private final Deque<ModAnnotation> annotations = new ArrayDeque<>();

        ClassScan() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] ifaces) {
            type = Type.getObjectType(name);
            superType = superName == null || superName.isEmpty() ? null : Type.getObjectType(superName);
            Set<Type> set = new LinkedHashSet<>();
            if (ifaces != null) {
                Arrays.stream(ifaces).map(Type::getObjectType).forEach(set::add);
            }
            interfaces = set;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return begin(new ModAnnotation(ElementType.TYPE, Type.getType(desc), type.getClassName()));
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    return begin(new ModAnnotation(ElementType.FIELD, Type.getType(annDesc), name));
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                         String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    return begin(new ModAnnotation(ElementType.METHOD, Type.getType(annDesc), name + desc));
                }
            };
        }

        private AnnotationVisitor begin(ModAnnotation ann) {
            annotations.addFirst(ann);
            return new Values(ann, null, false);
        }

        void emit(Set<ModFileScanData.ClassData> classes, Set<ModFileScanData.AnnotationData> out) {
            classes.add(new ModFileScanData.ClassData(type, superType, interfaces));
            List<ModFileScanData.AnnotationData> list = new ArrayList<>();
            for (ModAnnotation a : annotations) {
                if (ModFileScanData.interestingAnnotations().test(a.getASMType())) {
                    list.add(ModAnnotation.fromModAnnotation(type, a));
                }
            }
            out.addAll(list);
        }

        private final class Values extends AnnotationVisitor {
            private final ModAnnotation target;
            private final String arrayName;
            private final boolean nested;

            Values(ModAnnotation target, String arrayName, boolean nested) {
                super(Opcodes.ASM9);
                this.target = target;
                this.arrayName = arrayName;
                this.nested = nested;
                if (arrayName != null) {
                    target.addArray(arrayName);
                }
            }

            @Override
            public void visit(String name, Object value) {
                target.addProperty(name, value);
            }

            @Override
            public void visitEnum(String name, String desc, String value) {
                target.addEnumProperty(name, desc, value);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return new Values(target, name, false);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String desc) {
                ModAnnotation child = target.addChildAnnotation(name, desc);
                annotations.addFirst(child);
                return new Values(child, null, true);
            }

            @Override
            public void visitEnd() {
                if (arrayName != null) {
                    target.endArray();
                }
                if (nested) {
                    annotations.remove(target);
                    annotations.addLast(target);
                }
            }
        }
    }
}
