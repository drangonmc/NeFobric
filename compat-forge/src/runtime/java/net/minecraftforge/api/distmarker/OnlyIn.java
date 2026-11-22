package net.minecraftforge.api.distmarker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 只在某个物理端存在的类、字段或方法。NeFobric 在另一端加载这些类时会去掉它们。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PACKAGE})
@Repeatable(OnlyIns.class)
public @interface OnlyIn {
    Dist value();

    Class<?> _interface() default Object.class;
}
