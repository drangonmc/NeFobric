package net.minecraftforge.accesstransformer;

import dev.nefobric.compat.forge.transform.ActiveAccessTransformers;
import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Forge 的访问变换引擎。真正改字节码的是 NeFobric 类变换流水线的 ACCESS 阶段；这里是同一份数据的 Forge 视图：
 * 启动时读进模组 jar 里的 AT 文件，{@link #addResource} 追加的文件同时交给 ACCESS 阶段（对之后才加载的类生效）。
 *
 * <p>{@code masterList} 字段名和 Forge 的一致，有模组用反射把它换成自己的子类（例如 ModernFix）。</p>
 */
public enum AccessTransformerEngine {
    INSTANCE;

    private static final Logger LOGGER = LogManager.getLogger("AXFORM");

    private AccessTransformerList masterList = new AccessTransformerList();

    AccessTransformerEngine() {
        for (ActiveAccessTransformers.Source s : ActiveAccessTransformers.sources()) {
            masterList.loadFromText(s.text(), s.origin());
        }
    }

    /** 把这个类上的变换应用到节点上。 */
    public boolean transform(ClassNode clazzNode, Type classType) {
        Map<TargetType, Map<String, AccessTransformer>> ats = masterList.getTransformersForTarget(classType);
        if (ats.isEmpty()) {
            return false;
        }
        Set<String> privateChanged = new HashSet<>();
        AccessTransformer self = ats.getOrDefault(TargetType.CLASS, Map.of()).get(clazzNode.name.replace('/', '.'));
        if (self != null) {
            ((ClassTarget) self.getTarget()).apply(clazzNode, self.getTargetAccess(), self.getTargetFinalState(),
                    privateChanged);
        }
        Map<String, AccessTransformer> fields = ats.getOrDefault(TargetType.FIELD, Map.of());
        AccessTransformer allFields = fields.get("*");
        for (FieldNode f : clazzNode.fields) {
            apply(fields.get(f.name), f, privateChanged);
            apply(allFields, f, privateChanged);
        }
        Map<String, AccessTransformer> methods = ats.getOrDefault(TargetType.METHOD, Map.of());
        AccessTransformer allMethods = methods.get("*()");
        for (MethodNode m : clazzNode.methods) {
            apply(methods.get(m.name + m.desc), m, privateChanged);
            if (!m.name.equals("<clinit>")) {
                apply(allMethods, m, privateChanged);
            }
        }
        if (!privateChanged.isEmpty()) {
            boolean itf = (clazzNode.access & Opcodes.ACC_INTERFACE) != 0;
            for (MethodNode m : clazzNode.methods) {
                for (AbstractInsnNode insn : m.instructions) {
                    if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                            && call.owner.equals(clazzNode.name) && privateChanged.contains(call.name + call.desc)) {
                        call.setOpcode(itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL);
                    }
                }
            }
        }
        return true;
    }

    private static void apply(AccessTransformer at, FieldNode f, Set<String> privateChanged) {
        if (at != null) {
            new FieldTarget(at.getTarget().getClassName(), f.name).apply(f, at.getTargetAccess(),
                    at.getTargetFinalState(), privateChanged);
        }
    }

    private static void apply(AccessTransformer at, MethodNode m, Set<String> privateChanged) {
        if (at != null) {
            new MethodTarget(at.getTarget().getClassName(), m.name, m.desc).apply(m, at.getTargetAccess(),
                    at.getTargetFinalState(), privateChanged);
        }
    }

    /** 追加一个 AT 文件。只影响之后才加载的类。 */
    public void addResource(Path path, String resourceName) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read access transformer " + path, e);
        }
        masterList.loadFromText(text, resourceName);
        ActiveAccessTransformers.add(text, resourceName);
        LOGGER.debug("Added access transformer {} ({})", resourceName, path);
    }

    public boolean handlesClass(Type className) {
        return masterList.containsClassTarget(className);
    }

    public void acceptNameHandler(INameHandler nameHandler) {
        masterList.setNameHandler(nameHandler);
    }
}
