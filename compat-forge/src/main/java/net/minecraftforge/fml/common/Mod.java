package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Forge 系模组的主类，值是 mods.toml 里的 modId。Forge 47 与 NeoForge 47.1 用的都是这个注解。
 * NeFobric 在扫描 jar 时用 ASM 读取它，并在游戏钩子触发时用无参构造创建主类实例。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}
