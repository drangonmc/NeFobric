package dev.nefobric.compat.fabric.aw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessWidenerReaderTest {

    @Test
    void readsV2File() throws Exception {
        AccessWidener aw = AccessWidenerReader.read("""
                accessWidener   v2  intermediary
                # comment line

                accessible class net/minecraft/class_1234 # trailing comment
                extendable  method net/minecraft/class_1 method_2 (Lnet/minecraft/class_3;I)V
                transitive-mutable field net/minecraft/class_1 field_4 Ljava/util/List;
                transitive-accessible field net/minecraft/class_1 field_5 I
                """);
        assertEquals("intermediary", aw.namespace());
        assertEquals(2, aw.version());
        assertEquals(4, aw.entries().size());
        AccessWidener.Entry cls = aw.entries().get(0);
        assertEquals(AccessWidener.Target.CLASS, cls.target());
        assertEquals(AccessWidener.Access.ACCESSIBLE, cls.access());
        assertEquals("net/minecraft/class_1234", cls.owner());
        AccessWidener.Entry method = aw.entries().get(1);
        assertEquals(AccessWidener.Target.METHOD, method.target());
        assertEquals(AccessWidener.Access.EXTENDABLE, method.access());
        assertEquals("method_2", method.name());
        assertEquals("(Lnet/minecraft/class_3;I)V", method.desc());
        assertFalse(method.transitive());
        AccessWidener.Entry field = aw.entries().get(2);
        assertEquals(AccessWidener.Access.MUTABLE, field.access());
        assertTrue(field.transitive());
        assertEquals("intermediary", AccessWidenerReader.readNamespace("accessWidener v1 intermediary\n"));
    }

    @Test
    void rejectsInvalidFiles() {
        assertThrows(AccessWidenerReader.FormatException.class, () -> AccessWidenerReader.read("accessible class a/B"));
        assertThrows(AccessWidenerReader.FormatException.class, () -> AccessWidenerReader.read("accessWidener v3 named"));
        assertThrows(AccessWidenerReader.FormatException.class,
                () -> AccessWidenerReader.read("accessWidener v1 named\ntransitive-accessible class a/B"));
        assertThrows(AccessWidenerReader.FormatException.class,
                () -> AccessWidenerReader.read("accessWidener v2 named\nmutable class a/B"));
        assertThrows(AccessWidenerReader.FormatException.class,
                () -> AccessWidenerReader.read("accessWidener v2 named\nextendable field a/B c I"));
        assertThrows(AccessWidenerReader.FormatException.class,
                () -> AccessWidenerReader.read("accessWidener v2 named\naccessible class a.B"));
        assertThrows(AccessWidenerReader.FormatException.class,
                () -> AccessWidenerReader.read("accessWidener v2 named\naccessible method a/B c"));
    }

    @Test
    void remapsDescriptors() {
        assertEquals("(Lnet/minecraft/class_3;[Ljava/lang/String;I)V",
                AccessWidenerRemapper.mapDescriptor("srg", "srg", "(Lnet/minecraft/class_3;[Ljava/lang/String;I)V"));
    }
}
