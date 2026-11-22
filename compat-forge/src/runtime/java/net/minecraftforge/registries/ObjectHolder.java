package net.minecraftforge.registries;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标在静态字段（或类）上：注册表内容就绪后，把指定名字的注册项填进字段。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface ObjectHolder {
    /** 注册表名，例如 {@code minecraft:item}。 */
    String registryName();

    /** 注册项名，例如 {@code minecraft:diamond}。 */
    String value();
}
