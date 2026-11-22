package net.fabricmc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 只在某一端存在的类或成员。NeFobric 与 Fabric Loader 一样，在另一端加载模组类时把它们剥掉
 * （见 {@code dev.nefobric.compat.fabric.transform.EnvironmentStripper}）。
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PACKAGE})
@Documented
public @interface Environment {
    EnvType value();
}
