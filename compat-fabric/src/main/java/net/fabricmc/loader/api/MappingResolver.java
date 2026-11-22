package net.fabricmc.loader.api;

import java.util.Collection;

/**
 * 在映射命名空间之间换算类名和成员名。类名用点分形式（{@code net.minecraft.class_310}）。
 * NeFobric 的运行时命名空间是 Mojang 类名加 SRG 成员名（{@link #getCurrentRuntimeNamespace()} 返回 “srg”），
 * 模组用 intermediary 或 official 名查询时会换算到运行时名。
 */
public interface MappingResolver {
    Collection<String> getNamespaces();

    String getCurrentRuntimeNamespace();

    String mapClassName(String namespace, String className);

    String unmapClassName(String targetNamespace, String className);

    String mapFieldName(String namespace, String owner, String name, String descriptor);

    String mapMethodName(String namespace, String owner, String name, String descriptor);
}
