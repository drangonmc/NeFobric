package dev.nefobric.forge.enums;

import org.apache.logging.log4j.LogManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * 运行时追加枚举常量用到的几个小工具。只由 {@code EnumExtender} 改写出来的 {@code create} 方法调用，
 * 方法名和描述符写死在变换器里，改动时两边一起改。
 */
public final class ExtensibleEnums {
    /** {@code Class} 里缓存枚举常量的两个字段：{@code getEnumConstantsShared()} 和 {@code enumConstantDirectory()} 用。 */
    private static final String[] CACHE_FIELDS = {"enumConstants", "enumConstantDirectory"};

    private static final Cleaner CLEANER = Cleaner.create();

    private ExtensibleEnums() {
    }

    /** 同名（不分大小写，和 Forge 一致）的已有常量；没有返回 null。 */
    public static Object existing(String name, Object[] values) {
        for (Object value : values) {
            if (((Enum<?>) value).name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    /** 返回在末尾加上 {@code added} 的新数组，元素类型和原数组相同。 */
    public static Object[] append(Object[] values, Object added) {
        Object[] grown = Arrays.copyOf(values, values.length + 1);
        grown[values.length] = added;
        return grown;
    }

    /**
     * 清掉 JDK 对这个枚举的常量缓存，之后的 {@code valueOf}、{@code EnumSet.allOf}、{@code getEnumConstants}
     * 会重新调用 {@code values()}，看到新加的常量。已经建好的 {@code EnumSet}/{@code EnumMap} 不会变（和 Forge 一样）。
     */
    public static void reset(Class<?> enumClass) {
        CLEANER.clear(enumClass);
    }

    /** 写 {@code java.lang.Class} 的私有字段：优先用 sun.misc.Unsafe（不需要 --add-opens），不行再试反射。 */
    private interface Cleaner {
        void clear(Class<?> enumClass);

        static Cleaner create() {
            Field[] fields = new Field[CACHE_FIELDS.length];
            for (int i = 0; i < fields.length; i++) {
                try {
                    fields[i] = Class.class.getDeclaredField(CACHE_FIELDS[i]);
                } catch (NoSuchFieldException e) {
                    return unavailable("java.lang.Class has no field " + CACHE_FIELDS[i]);
                }
            }
            try {
                // 不在源码里直接写 sun.misc.Unsafe：免得编译警告，也不在编译期依赖 jdk.unsupported
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                Object unsafe = theUnsafe.get(null);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle offsetOf = lookup.findVirtual(unsafeClass, "objectFieldOffset",
                        MethodType.methodType(long.class, Field.class)).bindTo(unsafe);
                MethodHandle put = lookup.findVirtual(unsafeClass, "putObjectVolatile",
                        MethodType.methodType(void.class, Object.class, long.class, Object.class)).bindTo(unsafe);
                long[] offsets = new long[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    offsets[i] = (long) offsetOf.invokeExact(fields[i]);
                }
                return enumClass -> {
                    for (long offset : offsets) {
                        try {
                            put.invokeExact((Object) enumClass, offset, (Object) null);
                        } catch (Throwable t) {
                            throw new IllegalStateException(t);
                        }
                    }
                };
            } catch (Throwable unsafeFailed) {
                try {
                    for (Field field : fields) {
                        field.setAccessible(true);
                    }
                } catch (RuntimeException reflectionFailed) {
                    return unavailable(unsafeFailed + "; " + reflectionFailed);
                }
                return enumClass -> {
                    try {
                        for (Field field : fields) {
                            field.set(enumClass, null);
                        }
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
        }

        private static Cleaner unavailable(String why) {
            LogManager.getLogger().warn("Cannot reset enum constant caches ({}); valueOf may not see enum constants "
                    + "added at runtime", why);
            return enumClass -> {
            };
        }
    }
}
