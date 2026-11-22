package dev.nefobric.loader.transform;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformPipelineTest {
    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void phasesRunInOrderRegardlessOfRegistrationOrder() {
        TransformPipeline p = new TransformPipeline();
        p.add(TransformerPhase.MIXIN, (n, x) -> b(s(x) + "-mixinPhase"));
        p.add(TransformerPhase.ACCESS, (n, x) -> b(s(x) + "-access"));
        p.add(TransformerPhase.GAME_PATCH, (n, x) -> b(s(x) + "-patch"));
        p.add(TransformerPhase.STRIP, (n, x) -> b(s(x) + "-strip"));
        p.add(TransformerPhase.ACCESS, (n, x) -> b(s(x) + "-access2"));
        p.setMixin((n, x) -> b(s(x) + "-mixin"), n -> null);
        assertEquals("c-patch-strip-access-access2-mixinPhase-mixin", s(p.transform("a.B", b("c"))));
        assertEquals("c-patch-strip-access-access2", s(p.transformBeforeMixin("a.B", b("c"))));
    }

    @Test
    void transformerErrorsNameTheClassAndPhase() {
        TransformPipeline p = new TransformPipeline();
        p.add(TransformerPhase.STRIP, (n, x) -> {
            throw new IllegalStateException("boom");
        });
        ClassTransformException e = assertThrows(ClassTransformException.class, () -> p.transform("a.B", b("c")));
        assertEquals("a.B", e.className());
        assertEquals(TransformerPhase.STRIP, e.phase());
        assertTrue(e.getMessage().contains("boom"));
        p.add(TransformerPhase.ACCESS, (n, x) -> null);
    }

    @Test
    void nullResultIsAnError() {
        TransformPipeline p = new TransformPipeline();
        p.add(TransformerPhase.ACCESS, (n, x) -> null);
        assertThrows(ClassTransformException.class, () -> p.transform("a.B", b("c")));
    }

    @Test
    void generatorIsOnlyAskedWhenSet() {
        TransformPipeline p = new TransformPipeline();
        assertNull(p.generate("x.Y"));
        byte[] out = b("gen");
        p.setMixin((n, x) -> x, n -> n.equals("x.Y") ? out : null);
        assertSame(out, p.generate("x.Y"));
        assertNull(p.generate("x.Z"));
        byte[] in = b("same");
        assertArrayEquals(in, p.transform("x.Y", in));
    }
}
