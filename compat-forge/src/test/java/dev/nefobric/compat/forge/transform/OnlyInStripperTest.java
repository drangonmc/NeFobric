package dev.nefobric.compat.forge.transform;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.nefobric.compat.forge.transform.AccessTransformerTest.bytes;
import static dev.nefobric.compat.forge.transform.AccessTransformerTest.read;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlyInStripperTest {
    private static final String FIXTURE = "dev.nefobric.compat.forge.transform.OnlyInFixture";
    private static final String CLIENT_ONLY = FIXTURE + "$ClientOnly";

    @Test
    void stripsClientMembersOnServer() throws IOException {
        ClassNode out = read(new OnlyInStripper("DEDICATED_SERVER").transform(FIXTURE, bytes(FIXTURE)));
        Set<String> fields = out.fields.stream().map(f -> f.name).collect(Collectors.toSet());
        Set<String> methods = out.methods.stream().map(m -> m.name).collect(Collectors.toSet());
        assertEquals(Set.of("commonField"), fields);
        assertFalse(methods.contains("clientMethod"));
        assertTrue(methods.contains("serverMethod"));
        assertTrue(methods.contains("commonMethod"));
        // clientMethod 里的两层 lambda 也去掉了，commonMethod 的 lambda 留着
        List<MethodNode> lambdas = out.methods.stream().filter(m -> m.name.startsWith("lambda$")).toList();
        assertEquals(1, lambdas.size(), lambdas.stream().map(m -> m.name).toList().toString());
        assertTrue(lambdas.get(0).name.contains("commonMethod"));
        // _interface 指定的接口去掉，其他接口保留；类上的 @OnlyIn 注解本身也去掉
        assertEquals(List.of("java/lang/Comparable"), out.interfaces);
        assertTrue(out.visibleAnnotations == null || out.visibleAnnotations.stream()
                .noneMatch(a -> a.desc.equals(OnlyInStripper.ONLY_IN)));
    }

    @Test
    void stripsServerMembersOnClient() throws IOException {
        ClassNode out = read(new OnlyInStripper("CLIENT").transform(FIXTURE, bytes(FIXTURE)));
        Set<String> methods = out.methods.stream().map(m -> m.name).collect(Collectors.toSet());
        assertTrue(methods.contains("clientMethod"));
        assertFalse(methods.contains("serverMethod"));
        assertEquals(List.of("java/lang/Runnable", "java/lang/Comparable"), out.interfaces);
        assertEquals(2, out.fields.size());
    }

    @Test
    void refusesClassForOtherDist() throws IOException {
        byte[] in = bytes(CLIENT_ONLY);
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> new OnlyInStripper("DEDICATED_SERVER").transform(CLIENT_ONLY, in));
        assertTrue(e.getMessage().contains("invalid dist DEDICATED_SERVER"), e.getMessage());
        assertDoesNotThrow(() -> new OnlyInStripper("CLIENT").transform(CLIENT_ONLY, in));
    }

    @Test
    void skipsClassesWithoutMarker() throws IOException {
        byte[] in = bytes("dev.nefobric.compat.forge.transform.AtFixture");
        assertSame(in, new OnlyInStripper("CLIENT").transform("x", in));
    }
}
