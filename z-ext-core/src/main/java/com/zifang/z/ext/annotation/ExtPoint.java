package com.zifang.z.ext.annotation;

import java.lang.annotation.*;

/**
 * 扩展点标记注解
 * 标记在接口上，表示这是一个可扩展的接口
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ExtPoint {

    /**
     * 扩展点唯一标识
     */
    String value();

    /**
     * 扩展点描述
     */
    String description() default "";

    /**
     * 扩展点版本
     */
    String version() default "1.0.0";

    /**
     * 扩展点类型
     */
    ExtType type() default ExtType.SYNC;

    /**
     * 执行顺序，数字越小越优先
     */
    int order() default 0;
}