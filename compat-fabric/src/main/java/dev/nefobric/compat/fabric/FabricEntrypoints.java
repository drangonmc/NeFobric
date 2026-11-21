package dev.nefobric.compat.fabric;

import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.util.Messages;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 Fabric 的规则创建入口实例。支持三种写法：
 * <ul>
 *   <li>{@code pkg.Class}：用无参构造创建实例；同一个类在多个入口键下只创建一次（与 Fabric 一致）；</li>
 *   <li>{@code pkg.Class::FIELD}：读取字段（静态或实例字段）；</li>
 *   <li>{@code pkg.Class::method}：把方法包装成入口接口的实现。</li>
 * </ul>
 */
final class FabricEntrypoints {
    private final Map<String, Object> instances = new ConcurrentHashMap<>();

    <T> T create(ModCandidate mod, String spec, Class<T> type, ClassLoader loader) throws ReflectiveOperationException {
        int sep = spec.indexOf("::");
        if (sep < 0) {
            Object instance = instances.get(spec);
            if (instance == null) {
                Class<?> c = Class.forName(spec, true, loader);
                if (!type.isAssignableFrom(c)) {
                    throw new ClassCastException(Messages.tr("fabric.badEntrypoint", spec, mod.id(), type.getName()));
                }
                instance = c.getDeclaredConstructor().newInstance();
                instances.put(spec, instance);
            }
            if (!type.isInstance(instance)) {
                throw new ClassCastException(Messages.tr("fabric.badEntrypoint", spec, mod.id(), type.getName()));
            }
            return type.cast(instance);
        }
        String className = spec.substring(0, sep);
        String member = spec.substring(sep + 2);
        Class<?> c = Class.forName(className, true, loader);

        Field field = findField(c, member);
        if (field != null) {
            field.setAccessible(true);
            Object owner = Modifier.isStatic(field.getModifiers()) ? null : instanceOf(className, c);
            Object value = field.get(owner);
            if (!type.isInstance(value)) {
                throw new ClassCastException(Messages.tr("fabric.badEntrypoint", spec, mod.id(), type.getName()));
            }
            return type.cast(value);
        }

        List<Method> methods = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(member)) {
                methods.add(m);
            }
        }
        if (methods.size() != 1 || !type.isInterface()) {
            throw new NoSuchMethodException(spec);
        }
        Method target = methods.get(0);
        target.setAccessible(true);
        Object owner = Modifier.isStatic(target.getModifiers()) ? null : instanceOf(className, c);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString":
                        return "Entrypoint[" + spec + "]";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return method.invoke(proxy, args);
                }
            }
            try {
                return target.invoke(owner, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return type.cast(Proxy.newProxyInstance(loader, new Class<?>[]{type}, handler));
    }

    private Object instanceOf(String className, Class<?> c) throws ReflectiveOperationException {
        Object existing = instances.get(className);
        if (existing == null) {
            existing = c.getDeclaredConstructor().newInstance();
            instances.put(className, existing);
        }
        return existing;
    }

    private static Field findField(Class<?> c, String name) {
        try {
            return c.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
