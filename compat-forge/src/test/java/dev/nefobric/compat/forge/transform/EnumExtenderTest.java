package dev.nefobric.compat.forge.transform;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.nefobric.compat.forge.transform.AccessTransformerTest.bytes;
import static dev.nefobric.compat.forge.transform.AccessTransformerTest.read;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumExtenderTest {
    private static final String EXTENSIBLE = "dev.nefobric.compat.forge.transform.EnumFixtures$Extensible";
    private static final String VANILLA_LIKE = "dev.nefobric.compat.forge.transform.EnumFixtures$VanillaLike";
    private static final String NO_CTOR = "dev.nefobric.compat.forge.transform.EnumFixtures$NoMatchingConstructor";
    private static final String WITH_BODIES = "dev.nefobric.compat.forge.transform.EnumFixtures$WithBodies";

    @Test
    void createAppendsConstantsVisibleToValuesValueOfAndEnumSet() throws Exception {
        Class<?> e = load(EXTENSIBLE, new EnumExtender().transform(EXTENSIBLE, bytes(EXTENSIBLE)));
        // 先把 JDK 的两个缓存都填上，确认 create 之后会被清掉
        assertEquals("ALPHA", valueOf(e, "ALPHA").name());
        assertEquals(2, allOf(e).size());

        Method byText = e.getMethod("create", String.class, int.class, String.class);
        Method byRatio = e.getMethod("create", String.class, long.class, double.class);
        Enum<?> gamma = (Enum<?>) byText.invoke(null, "GAMMA", 3, "c");
        Enum<?> delta = (Enum<?>) byRatio.invoke(null, "DELTA", 1L << 40, 2.25d);

        assertEquals("GAMMA", gamma.name());
        assertEquals(2, gamma.ordinal());
        assertEquals(3, field(e, "number").getInt(gamma));
        assertEquals("c", field(e, "text").get(gamma));
        assertEquals(3, delta.ordinal());
        assertEquals(1L << 40, field(e, "big").getLong(delta));
        assertEquals(2.25d, field(e, "ratio").getDouble(delta));

        assertEquals(List.of("ALPHA", "BETA", "GAMMA", "DELTA"), names(values(e)));
        assertSame(gamma, valueOf(e, "GAMMA"));
        assertEquals(4, allOf(e).size());
        assertEquals(2, field(e, "inits").getInt(null));
    }

    @Test
    void sameNameIgnoringCaseReturnsExistingConstant() throws Exception {
        Class<?> e = load(EXTENSIBLE, new EnumExtender().transform(EXTENSIBLE, bytes(EXTENSIBLE)));
        Method create = e.getMethod("create", String.class, int.class, String.class);
        Object first = create.invoke(null, "GAMMA", 3, "c");
        assertSame(first, create.invoke(null, "gamma", 9, "z"));
        assertSame(valueOf(e, "ALPHA"), create.invoke(null, "Alpha", 9, "z"));
        assertEquals(3, values(e).length);
        assertEquals(1, field(e, "inits").getInt(null));
    }

    @Test
    void valuesFieldLosesFinal() throws IOException {
        ClassNode out = read(new EnumExtender().transform(EXTENSIBLE, bytes(EXTENSIBLE)));
        FieldNode values = out.fields.stream().filter(f -> f.name.equals("$VALUES")).findFirst().orElseThrow();
        assertEquals(0, values.access & Opcodes.ACC_FINAL);
        assertTrue(out.methods.stream().filter(m -> m.name.equals("create"))
                .allMatch(m -> (m.access & Opcodes.ACC_SYNCHRONIZED) != 0));
    }

    @Test
    void vanillaPatchThenExtenderMakesPlainEnumExtensible() throws Exception {
        byte[] patched = new ExtensibleVanillaEnums(Set.of(VANILLA_LIKE)).transform(VANILLA_LIKE, bytes(VANILLA_LIKE));
        ClassNode node = read(patched);
        assertTrue(node.interfaces.contains(EnumExtender.MARKER), node.interfaces.toString());
        assertTrue(node.signature.endsWith("L" + EnumExtender.MARKER + ";"), node.signature);

        Class<?> e = load(VANILLA_LIKE, new EnumExtender().transform(VANILLA_LIKE, patched));
        assertTrue(net.minecraftforge.common.IExtensibleEnum.class.isAssignableFrom(e));
        Method create = e.getMethod("create", String.class, float.class);
        assertTrue(Modifier.isPublic(create.getModifiers()) && Modifier.isStatic(create.getModifiers()));
        Enum<?> three = (Enum<?>) create.invoke(null, "THREE", 3.5f);
        assertEquals(2, three.ordinal());
        assertEquals(3.5f, field(e, "malus").getFloat(three));
        assertEquals(List.of("ONE", "TWO", "THREE"), names(values(e)));
    }

    @Test
    void vanillaPatchStubThrowsWithoutExtender() throws Exception {
        byte[] patched = new ExtensibleVanillaEnums(Set.of(VANILLA_LIKE)).transform(VANILLA_LIKE, bytes(VANILLA_LIKE));
        Class<?> e = load(VANILLA_LIKE, patched);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> e.getMethod("create", String.class, float.class).invoke(null, "THREE", 3.5f));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }

    @Test
    void vanillaPatchLeavesOtherAndAbstractEnumsAlone() throws IOException {
        byte[] plain = bytes(VANILLA_LIKE);
        assertSame(plain, new ExtensibleVanillaEnums(Set.of("some.Other")).transform(VANILLA_LIKE, plain));
        byte[] bodies = bytes(WITH_BODIES);
        assertSame(bodies, new ExtensibleVanillaEnums(Set.of(WITH_BODIES)).transform(WITH_BODIES, bodies));
        assertTrue(ExtensibleVanillaEnums.DEFAULT_TARGETS.contains("net.minecraft.world.item.Rarity"));
    }

    @Test
    void createWithoutMatchingConstructorIsRejected() throws IOException {
        byte[] in = bytes(NO_CTOR);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new EnumExtender().transform(NO_CTOR, in));
        assertTrue(e.getMessage().contains("(Ljava/lang/String;II)V"), e.getMessage());
    }

    @Test
    void classesWithoutMarkerAreUntouched() throws IOException {
        byte[] plain = bytes(VANILLA_LIKE);
        assertSame(plain, new EnumExtender().transform(VANILLA_LIKE, plain));
        byte[] other = bytes("dev.nefobric.compat.forge.transform.AtFixture");
        assertSame(other, new EnumExtender().transform("x", other));
    }

    // ---- 辅助 ----

    /** 用单独的类加载器加载变换后的类，别的类（IExtensibleEnum、ExtensibleEnums）交给测试的类加载器。 */
    private static Class<?> load(String name, byte[] bytes) throws ClassNotFoundException {
        ClassLoader parent = EnumExtenderTest.class.getClassLoader();
        ClassLoader loader = new ClassLoader("enum-test", parent) {
            private final Map<String, byte[]> classes = Map.of(name, bytes);

            @Override
            protected Class<?> loadClass(String n, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(n)) {
                    byte[] b = classes.get(n);
                    if (b == null) {
                        return super.loadClass(n, resolve);
                    }
                    Class<?> c = findLoadedClass(n);
                    return c != null ? c : defineClass(n, b, 0, b.length);
                }
            }
        };
        Class<?> c = Class.forName(name, true, loader);
        assertFalse(c == parentCopy(name), "must not be the original class");
        return c;
    }

    private static Class<?> parentCopy(String name) {
        try {
            return Class.forName(name, false, EnumExtenderTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Enum<?>[] values(Class<?> e) throws ReflectiveOperationException {
        Method values = e.getMethod("values");
        return (Enum<?>[]) values.invoke(null);
    }

    private static Enum<?> valueOf(Class<?> e, String name) throws ReflectiveOperationException {
        return (Enum<?>) e.getMethod("valueOf", String.class).invoke(null, name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EnumSet<?> allOf(Class<?> e) {
        return EnumSet.allOf((Class) e);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
