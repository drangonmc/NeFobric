package dev.nefobric.compat.fabric.impl;

import net.fabricmc.loader.api.MappingResolver;

import java.util.Collection;
import java.util.List;

/**
 * 映射查询。与 Fabric Loader 一样：类名必须是点分形式，未知命名空间抛 {@link IllegalArgumentException}，
 * 查不到的名字原样返回。
 */
final class MappingResolverImpl implements MappingResolver {

    @Override
    public Collection<String> getNamespaces() {
        return List.copyOf(CoreBridge.namespaces());
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return CoreBridge.runtimeNamespace();
    }

    @Override
    public String mapClassName(String namespace, String className) {
        checkDotted(className);
        checkNamespace(namespace);
        String runtime = CoreBridge.runtimeNamespace();
        if (namespace.equals(runtime)) {
            return className;
        }
        String mapped = CoreBridge.mapClass(namespace, runtime, className);
        return mapped != null ? mapped : className;
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        checkDotted(className);
        checkNamespace(targetNamespace);
        String runtime = CoreBridge.runtimeNamespace();
        if (targetNamespace.equals(runtime)) {
            return className;
        }
        String mapped = CoreBridge.mapClass(runtime, targetNamespace, className);
        return mapped != null ? mapped : className;
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        checkDotted(owner);
        checkNamespace(namespace);
        String runtime = CoreBridge.runtimeNamespace();
        if (namespace.equals(runtime)) {
            return name;
        }
        String mapped = CoreBridge.mapField(namespace, runtime, owner, name, descriptor);
        return mapped != null ? mapped : name;
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        checkDotted(owner);
        checkNamespace(namespace);
        String runtime = CoreBridge.runtimeNamespace();
        if (namespace.equals(runtime)) {
            return name;
        }
        String mapped = CoreBridge.mapMethod(namespace, runtime, owner, name, descriptor);
        return mapped != null ? mapped : name;
    }

    private static void checkDotted(String className) {
        if (className.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
        }
    }

    private static void checkNamespace(String namespace) {
        if (!CoreBridge.namespaces().contains(namespace)) {
            throw new IllegalArgumentException("Unknown namespace: " + namespace);
        }
    }
}
