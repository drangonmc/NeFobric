package dev.nefobric.forge.loading;

import dev.nefobric.compat.forge.transform.AccessTransformerApplier;
import dev.nefobric.compat.forge.transform.AccessTransformers;
import dev.nefobric.compat.forge.transform.ActiveAccessTransformers;
import net.minecraftforge.accesstransformer.AccessTransformer;
import net.minecraftforge.accesstransformer.AccessTransformerEngine;
import net.minecraftforge.accesstransformer.INameHandler;
import net.minecraftforge.accesstransformer.TargetType;
import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTransformerEngineTest {
    private static final String FIXTURE = "dev.nefobric.forge.loading.EngineFixture";
    private static final Type FIXTURE_TYPE = Type.getObjectType(FIXTURE.replace('.', '/'));

    @Test
    void listParsesAndMerges() {
        AccessTransformerList list = new AccessTransformerList();
        list.loadFromText("public a.B f\nprotected a.B f\npublic-f a.B m()V\npublic a.B\npublic a.B *\nnope\n", "one");
        list.loadFromText("public+f a.B m()V\n", "two");
        Type b = Type.getObjectType("a/B");
        assertTrue(list.containsClassTarget(b));
        assertFalse(list.containsClassTarget(Type.getObjectType("a/C")));
        Map<TargetType, Map<String, AccessTransformer>> map = list.getTransformersForTarget(b);
        assertEquals(AccessTransformer.Modifier.PUBLIC, map.get(TargetType.FIELD).get("f").getTargetAccess());
        AccessTransformer m = map.get(TargetType.METHOD).get("m()V");
        assertEquals(AccessTransformer.FinalState.REMOVEFINAL, m.getTargetFinalState());
        assertEquals("one,two", m.getOrigin());
        assertNotNull(map.get(TargetType.FIELD).get("*"));
        assertNotNull(map.get(TargetType.CLASS).get("a.B"));
        assertEquals(Set.of(b), list.getTargets());
        assertEquals(Set.of("a.B"), list.getAccessTransformers().keySet());
    }

    @Test
    void addedResourcesReachTheClassPipeline(@TempDir Path dir) throws IOException {
        AccessTransformerApplier applier = new AccessTransformerApplier(AccessTransformers.empty());
        ActiveAccessTransformers.install(List.of(), applier);
        try {
            Path cfg = dir.resolve("accesstransformer.cfg");
            Files.writeString(cfg, "public " + FIXTURE + " secret()I\npublic-f " + FIXTURE + " value\n");
            AccessTransformerEngine.INSTANCE.addResource(cfg, "fixture.jar");
            assertTrue(AccessTransformerEngine.INSTANCE.handlesClass(FIXTURE_TYPE));

            // Forge 的视图：直接改 ClassNode
            ClassNode node = read(bytes());
            assertTrue(AccessTransformerEngine.INSTANCE.transform(node, FIXTURE_TYPE));
            assertTrue((method(node, "secret").access & Opcodes.ACC_PUBLIC) != 0);
            assertTrue((field(node, "value").access & Opcodes.ACC_PUBLIC) != 0);

            // 真正生效的那份：类变换流水线的 ACCESS 阶段也拿到了
            ClassNode viaPipeline = read(applier.transform(FIXTURE, bytes()));
            assertTrue((method(viaPipeline, "secret").access & Opcodes.ACC_PUBLIC) != 0);
        } finally {
            ActiveAccessTransformers.install(List.of(), null);
        }
    }

    /** ModernFix 的做法：用反射换掉 masterList 和它的 accessTransformers。 */
    @Test
    void modsCanSwapTheMasterList() throws ReflectiveOperationException {
        Field master = AccessTransformerEngine.class.getDeclaredField("masterList");
        master.setAccessible(true);
        AccessTransformerList old = (AccessTransformerList) master.get(AccessTransformerEngine.INSTANCE);
        Field ats = AccessTransformerList.class.getDeclaredField("accessTransformers");
        ats.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) ats.get(old);
        INameHandler names = ObfuscationReflectionHelper.getPrivateValue(AccessTransformerList.class, old, "nameHandler");

        SwappedList mine = new SwappedList();
        mine.setNameHandler(names);
        ObfuscationReflectionHelper.setPrivateValue(AccessTransformerList.class, mine, new HashMap<>(map),
                "accessTransformers");
        master.set(AccessTransformerEngine.INSTANCE, mine);
        try {
            AccessTransformerEngine.INSTANCE.handlesClass(FIXTURE_TYPE);
            assertTrue(mine.asked);
        } finally {
            master.set(AccessTransformerEngine.INSTANCE, old);
        }
    }

    private static final class SwappedList extends AccessTransformerList {
        boolean asked;

        @Override
        public boolean containsClassTarget(Type type) {
            asked = true;
            return super.containsClassTarget(type);
        }
    }

    private static byte[] bytes() throws IOException {
        try (InputStream in = AccessTransformerEngineTest.class.getClassLoader()
                .getResourceAsStream(FIXTURE.replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    private static org.objectweb.asm.tree.MethodNode method(ClassNode n, String name) {
        return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static org.objectweb.asm.tree.FieldNode field(ClassNode n, String name) {
        return n.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
    }
}
