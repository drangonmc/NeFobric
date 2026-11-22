package dev.nefobric.forge.loading;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.nefobric.compat.forge.transform.LaunchPluginBridge;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class LaunchPluginTest {
    private static final String FIXTURE = "dev.nefobric.forge.loading.PluginFixture";

    /** ModernFix 的做法：从 Launcher.INSTANCE 里取出插件表，复制一份加上自己的再放回去。 */
    @Test
    @SuppressWarnings("unchecked")
    void pluginsAddedByReflectionRunOnClassLoad() throws Exception {
        Field lp = Launcher.class.getDeclaredField("launchPlugins");
        lp.setAccessible(true);
        Object handler = lp.get(Launcher.INSTANCE);
        Field pf = LaunchPluginHandler.class.getDeclaredField("plugins");
        pf.setAccessible(true);
        Map<String, ILaunchPluginService> old = (Map<String, ILaunchPluginService>) pf.get(handler);
        Map<String, ILaunchPluginService> copy = new LinkedHashMap<>(old);
        Patcher plugin = new Patcher();
        copy.put(plugin.name(), plugin);
        pf.set(handler, copy);
        try {
            LaunchPluginBridge bridge = new LaunchPluginBridge();
            byte[] in = bytes(FIXTURE);
            byte[] out = bridge.transform(FIXTURE, in);
            assertNotSame(in, out);
            assertEquals(List.of(ILaunchPluginService.Phase.BEFORE, ILaunchPluginService.Phase.AFTER), plugin.phases);
            Class<?> c = define(FIXTURE, out);
            assertEquals("patched", c.getMethod("value").invoke(null));
            // 重算的栈帧要能通过校验
            assertEquals(List.of("patched"), c.getMethod("pick", boolean.class).invoke(null, true));
            assertEquals(List.of("patched"), c.getMethod("pick", boolean.class).invoke(null, false));

            String other = "dev.nefobric.forge.loading.EngineFixture";
            byte[] otherBytes = bytes(other);
            assertSame(otherBytes, bridge.transform(other, otherBytes));
        } finally {
            pf.set(handler, old);
        }
    }

    private static final class Patcher implements ILaunchPluginService {
        final List<Phase> phases = new ArrayList<>();

        @Override
        public String name() {
            return "nefobric_test";
        }

        @Override
        public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
            return classType.getClassName().equals(FIXTURE) ? EnumSet.of(Phase.BEFORE, Phase.AFTER)
                    : EnumSet.noneOf(Phase.class);
        }

        @Override
        public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
            phases.add(phase);
            if (phase == Phase.BEFORE) {
                return ComputeFlags.NO_REWRITE;
            }
            for (MethodNode m : classNode.methods) {
                for (AbstractInsnNode insn : m.instructions) {
                    if (insn instanceof LdcInsnNode ldc && "original".equals(ldc.cst)) {
                        ldc.cst = "patched";
                    }
                }
            }
            return ComputeFlags.COMPUTE_FRAMES;
        }
    }

    private static byte[] bytes(String className) throws IOException {
        try (InputStream in = LaunchPluginTest.class.getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }

    private static Class<?> define(String name, byte[] bytes) {
        return new ClassLoader(LaunchPluginTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, bytes, 0, bytes.length);
            }
        }.define();
    }
}
