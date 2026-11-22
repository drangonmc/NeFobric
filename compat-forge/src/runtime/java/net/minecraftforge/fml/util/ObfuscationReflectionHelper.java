package net.minecraftforge.fml.util;

import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 按 SRG 名反射访问原版的私有成员。NeFobric 运行时就是 SRG 命名（和 Forge 生产环境一样），所以名字原样使用。
 */
@SuppressWarnings({"serial", "unchecked", "unused", "WeakerAccess"})
public class ObfuscationReflectionHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    @NotNull
    public static String remapName(INameMappingService.Domain domain, String name) {
        return FMLLoader.getNameFunction("srg").map(f -> f.apply(domain, name)).orElse(name);
    }

    @Nullable
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, E instance, String fieldName) {
        try {
            return (T) findField(classToAccess, fieldName).get(instance);
        } catch (UnableToFindFieldException e) {
            LOGGER.error("Unable to locate field {} ({}) on type {}", fieldName,
                    remapName(INameMappingService.Domain.FIELD, fieldName), classToAccess.getName(), e);
            throw e;
        } catch (IllegalAccessException e) {
            LOGGER.error("Unable to access field {} ({}) on type {}", fieldName,
                    remapName(INameMappingService.Domain.FIELD, fieldName), classToAccess.getName(), e);
            throw new UnableToAccessFieldException(e);
        }
    }

    public static <T, E> void setPrivateValue(@NotNull final Class<? super T> classToAccess, @NotNull final T instance,
                                              @Nullable final E value, @NotNull final String fieldName) {
        try {
            findField(classToAccess, fieldName).set(instance, value);
        } catch (UnableToFindFieldException e) {
            LOGGER.error("Unable to locate any field {} on type {}", fieldName, classToAccess.getName(), e);
            throw e;
        } catch (IllegalAccessException e) {
            LOGGER.error("Unable to set any field {} on type {}", fieldName, classToAccess.getName(), e);
            throw new UnableToAccessFieldException(e);
        }
    }

    @NotNull
    public static Method findMethod(@NotNull final Class<?> clazz, @NotNull final String methodName,
                                    @NotNull final Class<?>... parameterTypes) {
        Objects.requireNonNull(clazz, "Class to find method on cannot be null.");
        Objects.requireNonNull(methodName, "Name of method to find cannot be null.");
        if (methodName.isEmpty()) {
            throw new IllegalArgumentException("Name of method to find cannot be empty.");
        }
        Objects.requireNonNull(parameterTypes, "Parameter types of method to find cannot be null.");
        try {
            Method m = clazz.getDeclaredMethod(remapName(INameMappingService.Domain.METHOD, methodName), parameterTypes);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            throw new UnableToFindMethodException(e);
        }
    }

    @NotNull
    public static <T> Constructor<T> findConstructor(@NotNull final Class<T> clazz,
                                                     @NotNull final Class<?>... parameterTypes) {
        Objects.requireNonNull(clazz, "Class to find constructor on cannot be null.");
        Objects.requireNonNull(parameterTypes, "Parameter types of constructor to find cannot be null.");
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (final NoSuchMethodException e) {
            StringJoiner joiner = new StringJoiner(", ", "(", ")");
            for (Class<?> type : parameterTypes) {
                joiner.add(type.getSimpleName());
            }
            throw new UnknownConstructorException("Could not find constructor '" + clazz.getSimpleName() + joiner
                    + "' in " + clazz);
        }
    }

    @NotNull
    public static <T> Field findField(@NotNull final Class<? super T> clazz, @NotNull final String fieldName) {
        Objects.requireNonNull(clazz, "Class to find field on cannot be null.");
        Objects.requireNonNull(fieldName, "Name of field to find cannot be null.");
        if (fieldName.isEmpty()) {
            throw new IllegalArgumentException("Name of field to find cannot be empty.");
        }
        try {
            Field f = clazz.getDeclaredField(remapName(INameMappingService.Domain.FIELD, fieldName));
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            throw new UnableToFindFieldException(e);
        }
    }

    public static class UnableToAccessFieldException extends RuntimeException {
        private UnableToAccessFieldException(Exception e) {
            super(e);
        }
    }

    public static class UnableToFindFieldException extends RuntimeException {
        private UnableToFindFieldException(Exception e) {
            super(e);
        }
    }

    public static class UnableToFindMethodException extends RuntimeException {
        public UnableToFindMethodException(Throwable failed) {
            super(failed);
        }
    }

    public static class UnknownConstructorException extends RuntimeException {
        public UnknownConstructorException(final String message) {
            super(message);
        }
    }
}
