package cpw.mods.modlauncher.serviceapi;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.NamedPath;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * ModLauncher 的启动插件：在类加载时直接改 {@link ClassNode}。模组用反射把实现放进
 * {@code Launcher.INSTANCE} 的插件表里；NeFobric 在 ACCESS 阶段（访问变换之后、Mixin 之前）依次调用它们，
 * 先 {@link Phase#BEFORE} 后 {@link Phase#AFTER}。
 */
public interface ILaunchPluginService {
    String name();

    enum Phase {
        BEFORE, AFTER
    }

    EnumSet<Phase> handlesClass(Type classType, boolean isEmpty);

    default EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        return handlesClass(classType, isEmpty);
    }

    default boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        return false;
    }

    default boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        return processClass(phase, classNode, classType);
    }

    default int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        return processClass(phase, classNode, classType, reason) ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
    }

    default void offerResource(Path resource, String name) {
    }

    default void addResources(List<SecureJar> resources) {
    }

    default void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
    }

    default <T> T getExtension() {
        return null;
    }

    default void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
    }

    /** {@link #processClassWithFlags} 的返回值：改完之后怎么重写字节码。 */
    interface ComputeFlags {
        int NO_REWRITE = 0;
        int SIMPLE_REWRITE = 0x100;
        int COMPUTE_MAXS = ClassWriter.COMPUTE_MAXS;
        int COMPUTE_FRAMES = ClassWriter.COMPUTE_FRAMES;
    }

    interface ITransformerLoader {
        byte[] buildTransformedClassNodeFor(String className) throws ClassNotFoundException;
    }
}
