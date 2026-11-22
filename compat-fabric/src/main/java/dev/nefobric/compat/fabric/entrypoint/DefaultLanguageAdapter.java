package dev.nefobric.compat.fabric.entrypoint;

import dev.nefobric.loader.impl.NeFobricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Java 入口（adapter “default”），三种写法与 Fabric Loader 相同：
 * {@code pkg.Class}（无参构造）、{@code pkg.Class::FIELD}（静态字段）、{@code pkg.Class::method}（包装成入口接口）。
 * 错误信息也照抄 Fabric Loader，方便对照模组作者在 Fabric 上见过的报错。
 */
public final class DefaultLanguageAdapter implements LanguageAdapter {
    public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

    private DefaultLanguageAdapter() {
    }

    static ClassLoader targetClassLoader() {
        if (NeFobricLoader.isInitialized()) {
            return NeFobricLoader.get().classLoader();
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        return ctx != null ? ctx : DefaultLanguageAdapter.class.getClassLoader();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        String[] methodSplit = value.split("::");
        if (methodSplit.length >= 3) {
            throw new LanguageAdapterException("Invalid handle format: " + value);
        }
        Class<?> c;
        try {
            c = Class.forName(methodSplit[0], true, targetClassLoader());
        } catch (ClassNotFoundException e) {
            throw new LanguageAdapterException(e);
        }
        if (methodSplit.length == 1) {
            if (type.isAssignableFrom(c)) {
                try {
                    return (T) c.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new LanguageAdapterException(e);
                }
            }
            throw new LanguageAdapterException("Class " + c.getName() + " cannot be cast to " + type.getName() + "!");
        }

        List<Method> methodList = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(methodSplit[1])) {
                methodList.add(m);
            }
        }
        try {
            Field field = c.getDeclaredField(methodSplit[1]);
            Class<?> fType = field.getType();
            if ((field.getModifiers() & Modifier.STATIC) == 0) {
                throw new LanguageAdapterException("Field " + value + " must be static!");
            }
            if (!methodList.isEmpty()) {
                throw new LanguageAdapterException("Ambiguous " + value + " - refers to both field and method!");
            }
            if (!type.isAssignableFrom(fType)) {
                throw new LanguageAdapterException("Field " + value + " cannot be cast to " + type.getName() + "!");
            }
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (NoSuchFieldException e) {
            // 不是字段，按方法处理
        } catch (IllegalAccessException | RuntimeException e) {
            throw new LanguageAdapterException("Field " + value + " cannot be accessed!", e);
        }

        if (!type.isInterface()) {
            throw new LanguageAdapterException("Cannot proxy method " + value + " to non-interface type " + type.getName() + "!");
        }
        if (methodList.isEmpty()) {
            throw new LanguageAdapterException("Could not find " + value + "!");
        } else if (methodList.size() >= 2) {
            throw new LanguageAdapterException("Found multiple method entries of name " + value + "!");
        }
        Method targetMethod = methodList.get(0);
        Object object = null;
        if ((targetMethod.getModifiers() & Modifier.STATIC) == 0) {
            try {
                object = c.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new LanguageAdapterException(e);
            }
        }
        MethodHandle handle;
        try {
            targetMethod.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(targetMethod);
        } catch (Exception ex) {
            throw new LanguageAdapterException(ex);
        }
        if (object != null) {
            handle = handle.bindTo(object);
        }
        try {
            return MethodHandleProxies.asInterfaceInstance(type, handle);
        } catch (Exception ex) {
            throw new LanguageAdapterException(ex);
        }
    }
}
