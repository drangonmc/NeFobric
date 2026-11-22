package dev.nefobric.loader.mapping;

import java.util.List;

/**
 * 名字在不同命名空间之间的映射。运行时命名空间是 {@value #SRG}：Mojang 官方类名加 MCPConfig 的 SRG 成员名，
 * 和 Forge 1.20.1 生产环境一致。Fabric 模组用的是 {@value #INTERMEDIARY}，原版 jar 里是 {@value #OFFICIAL}。
 *
 * <p>类名用点分形式（{@code net.minecraft.client.Minecraft}），方法和字段描述符用内部形式
 * （{@code (Lnet/minecraft/world/item/Item;)V}），都写在来源命名空间里。找不到映射时原样返回。
 */
public interface Mappings {
    String OFFICIAL = "official";
    String INTERMEDIARY = "intermediary";
    String SRG = "srg";

    /** 游戏运行时用的命名空间。没有映射（例如未混淆的测试游戏）时等于 {@value #OFFICIAL}。 */
    String runtimeNamespace();

    /** 可用的命名空间，第一个是 {@value #OFFICIAL}。 */
    List<String> namespaces();

    /** 是否所有命名空间都相同（没有加载任何映射）。 */
    boolean isIdentity();

    String mapClass(String fromNs, String toNs, String className);

    String mapField(String fromNs, String toNs, String owner, String name, String desc);

    String mapMethod(String fromNs, String toNs, String owner, String name, String desc);

    /** 没有映射时用的恒等映射。 */
    static Mappings identity() {
        return IdentityMappings.INSTANCE;
    }
}
