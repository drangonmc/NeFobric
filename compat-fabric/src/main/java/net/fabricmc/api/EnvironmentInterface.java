package net.fabricmc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 只在某一端实现的接口；在另一端加载时从类的接口列表里去掉。 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Repeatable(EnvironmentInterfaces.class)
@Documented
public @interface EnvironmentInterface {
    EnvType value();

    Class<?> itf();
}
