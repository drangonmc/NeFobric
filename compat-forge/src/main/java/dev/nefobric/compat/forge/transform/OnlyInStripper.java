package dev.nefobric.compat.forge.transform;

import dev.nefobric.loader.classloading.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * STRIP 阶段：和 Forge 一样按物理端处理 {@code @OnlyIn}。
 * <ul>
 *   <li>类上标了另一端：拒绝加载这个类（抛出异常，类加载失败）；</li>
 *   <li>类上 {@code @OnlyIn(value = 另一端, _interface = X.class)}：去掉对接口 X 的实现；</li>
 *   <li>字段、方法上标了另一端：去掉它们，连同只在被去掉的方法里用到的 lambda 合成方法。</li>
 * </ul>
 * 类级的 {@code @OnlyIn}/{@code @OnlyIns} 注解本身也会去掉，和 Forge 一致。
 */
public final class OnlyInStripper implements ClassTransformer {
    static final String ONLY_IN = "Lnet/minecraftforge/api/distmarker/OnlyIn;";
    static final String ONLY_INS = "Lnet/minecraftforge/api/distmarker/OnlyIns;";
    private static final byte[] MARKER = "Lnet/minecraftforge/api/distmarker/OnlyIn".getBytes(StandardCharsets.UTF_8);
    private static final String LAMBDA_FACTORY = "java/lang/invoke/LambdaMetafactory";

    private final String dist;

    /** @param dist 当前端在 {@code Dist} 枚举里的名字：{@code CLIENT} 或 {@code DEDICATED_SERVER} */
    public OnlyInStripper(String dist) {
        this.dist = dist;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (!contains(bytes, MARKER)) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!strip(node)) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** @return 是否有改动 */
    boolean strip(ClassNode node) {
        if (isForOtherDist(node.visibleAnnotations) || isForOtherDist(node.invisibleAnnotations)) {
            throw new RuntimeException("Attempted to load class " + node.name + " for invalid dist " + dist);
        }
        boolean changed = false;
        for (AnnotationNode ann : unpack(node.visibleAnnotations, node.invisibleAnnotations)) {
            Object itf = value(ann, "_interface");
            if (itf instanceof Type t && !dist.equals(distOf(ann)) && node.interfaces.remove(t.getInternalName())) {
                changed = true;
            }
        }
        changed |= removeClassMarkers(node.visibleAnnotations);
        changed |= removeClassMarkers(node.invisibleAnnotations);

        for (Iterator<FieldNode> it = node.fields.iterator(); it.hasNext(); ) {
            FieldNode f = it.next();
            if (isForOtherDist(f.visibleAnnotations) || isForOtherDist(f.invisibleAnnotations)) {
                it.remove();
                changed = true;
            }
        }
        List<Handle> lambdas = new ArrayList<>();
        for (Iterator<MethodNode> it = node.methods.iterator(); it.hasNext(); ) {
            MethodNode m = it.next();
            if (isForOtherDist(m.visibleAnnotations) || isForOtherDist(m.invisibleAnnotations)) {
                it.remove();
                gatherLambdas(node.name, m, lambdas);
                changed = true;
            }
        }
        // 被去掉的方法里的 lambda 体（以及 lambda 里再嵌套的 lambda）也去掉，否则它们引用的另一端的类会出问题
        while (!lambdas.isEmpty()) {
            Set<String> targets = new HashSet<>();
            for (Handle h : lambdas) {
                targets.add(h.getName() + h.getDesc());
            }
            lambdas = new ArrayList<>();
            for (Iterator<MethodNode> it = node.methods.iterator(); it.hasNext(); ) {
                MethodNode m = it.next();
                if ((m.access & Opcodes.ACC_SYNTHETIC) != 0 && targets.contains(m.name + m.desc)) {
                    it.remove();
                    gatherLambdas(node.name, m, lambdas);
                }
            }
        }
        return changed;
    }

    private boolean isForOtherDist(List<AnnotationNode> anns) {
        if (anns == null) {
            return false;
        }
        for (AnnotationNode ann : unpack(anns, null)) {
            if (value(ann, "_interface") == null) {
                String d = distOf(ann);
                if (d != null && !d.equals(dist)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean removeClassMarkers(List<AnnotationNode> anns) {
        return anns != null && anns.removeIf(a -> ONLY_IN.equals(a.desc) || ONLY_INS.equals(a.desc));
    }

    /** 所有 {@code @OnlyIn}，包括 {@code @OnlyIns} 里重复的那些。 */
    private static List<AnnotationNode> unpack(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> out = new ArrayList<>();
        for (List<AnnotationNode> list : Arrays.asList(a, b)) {
            if (list == null) {
                continue;
            }
            for (AnnotationNode ann : list) {
                if (ONLY_IN.equals(ann.desc)) {
                    out.add(ann);
                } else if (ONLY_INS.equals(ann.desc) && value(ann, "value") instanceof List<?> inner) {
                    for (Object o : inner) {
                        if (o instanceof AnnotationNode n && ONLY_IN.equals(n.desc)) {
                            out.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static String distOf(AnnotationNode ann) {
        return value(ann, "value") instanceof String[] e && e.length == 2 ? e[1] : null;
    }

    private static Object value(AnnotationNode ann, String key) {
        if (ann.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < ann.values.size(); i += 2) {
            if (key.equals(ann.values.get(i))) {
                return ann.values.get(i + 1);
            }
        }
        return null;
    }

    private static void gatherLambdas(String owner, MethodNode m, List<Handle> out) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy && LAMBDA_FACTORY.equals(indy.bsm.getOwner())
                    && indy.bsmArgs.length > 1 && indy.bsmArgs[1] instanceof Handle h && owner.equals(h.getOwner())) {
                out.add(h);
            }
        }
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
}
