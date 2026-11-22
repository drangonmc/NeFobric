package dev.nefobric.compat.fabric.transform;

import net.fabricmc.api.EnvType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentStripperTest {

    private static byte[] bytes(Class<?> c) throws Exception {
        try (InputStream in = c.getResourceAsStream(c.getSimpleName() + ".class")) {
            return in.readAllBytes();
        }
    }

    private static ClassNode node(byte[] b) {
        ClassNode n = new ClassNode();
        new ClassReader(b).accept(n, 0);
        return n;
    }

    @Test
    void stripsMembersAndInterfacesOfTheOtherSide() throws Exception {
        String name = EnvMixed.class.getName();
        byte[] original = bytes(EnvMixed.class);

        ClassNode server = node(new EnvironmentStripper(EnvType.SERVER).transform(name, original));
        assertEquals(List.of("java/lang/AutoCloseable", "java/io/Serializable"), server.interfaces);
        assertEquals(List.of("commonField"), server.fields.stream().map(f -> f.name).collect(Collectors.toList()));
        List<String> serverMethods = server.methods.stream().map(m -> m.name).collect(Collectors.toList());
        assertTrue(serverMethods.contains("close"));
        assertTrue(serverMethods.contains("common"));
        assertTrue(!serverMethods.contains("run"));

        ClassNode client = node(new EnvironmentStripper(EnvType.CLIENT).transform(name, original));
        assertEquals(List.of("java/lang/Runnable", "java/io/Serializable"), client.interfaces);
        assertEquals(2, client.fields.size());
        assertTrue(client.methods.stream().noneMatch(m -> m.name.equals("close")));
    }

    @Test
    void refusesWholeClassOfTheOtherSide() throws Exception {
        byte[] b = bytes(EnvClientOnly.class);
        assertSame(b, new EnvironmentStripper(EnvType.CLIENT).transform(EnvClientOnly.class.getName(), b));
        assertThrows(RuntimeException.class,
                () -> new EnvironmentStripper(EnvType.SERVER).transform(EnvClientOnly.class.getName(), b));
    }

    @Test
    void leavesGameAndUnannotatedClassesAlone() throws Exception {
        byte[] b = bytes(EnvironmentStripperTest.class);
        assertSame(b, new EnvironmentStripper(EnvType.SERVER).transform(EnvironmentStripperTest.class.getName(), b));
        byte[] annotated = bytes(EnvClientOnly.class);
        assertSame(annotated, new EnvironmentStripper(EnvType.SERVER).transform("net.minecraft.client.Minecraft", annotated));
    }
}
