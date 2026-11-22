package cpw.mods.modlauncher;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.nefobric.forge.modlauncher.HierarchyClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 启动插件表。字段名 {@code plugins} 和 ModLauncher 的一致：模组会用反射取出它、复制一份加上自己的插件再放回去，
 * 所以每次分派都重新读这个字段。
 */
public class LaunchPluginHandler {
    /** ModLauncher 给插件的加载原因：类加载。 */
    static final String CLASSLOADING_REASON = "classloading";

    private Map<String, ILaunchPluginService> plugins = new LinkedHashMap<>();

    LaunchPluginHandler() {
    }

    public Optional<ILaunchPluginService> get(String name) {
        return Optional.ofNullable(plugins.get(name));
    }

    /** NeFobric：类加载时调用。没有插件要处理这个类时原样返回 {@code bytes}。 */
    byte[] transform(String className, byte[] bytes) {
        Map<String, ILaunchPluginService> current = plugins;
        if (current.isEmpty()) {
            return bytes;
        }
        Type type = Type.getObjectType(className.replace('.', '/'));
        List<ILaunchPluginService> before = new ArrayList<>();
        List<ILaunchPluginService> after = new ArrayList<>();
        for (ILaunchPluginService p : List.copyOf(current.values())) {
            EnumSet<ILaunchPluginService.Phase> phases = p.handlesClass(type, false, CLASSLOADING_REASON);
            if (phases == null) {
                continue;
            }
            if (phases.contains(ILaunchPluginService.Phase.BEFORE)) {
                before.add(p);
            }
            if (phases.contains(ILaunchPluginService.Phase.AFTER)) {
                after.add(p);
            }
        }
        if (before.isEmpty() && after.isEmpty()) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        int flags = 0;
        for (ILaunchPluginService p : before) {
            flags |= p.processClassWithFlags(ILaunchPluginService.Phase.BEFORE, node, type, CLASSLOADING_REASON);
        }
        for (ILaunchPluginService p : after) {
            flags |= p.processClassWithFlags(ILaunchPluginService.Phase.AFTER, node, type, CLASSLOADING_REASON);
        }
        if (flags == ILaunchPluginService.ComputeFlags.NO_REWRITE) {
            return bytes;
        }
        int writerFlags = flags & (ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassWriter writer = new HierarchyClassWriter(writerFlags, LaunchPluginHandler.class.getClassLoader());
        node.accept(writer);
        return writer.toByteArray();
    }
}
