package com.zifang.z.ext.annotation;

import java.lang.annotation.*;

/**
 * 扩展实现标记注解
 * 标记在实现类上，表示这是某个扩展点的具体实现
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ExtImpl {

    /**
     * 对应的扩展点标识
     */
    String point();

    /**
     * 实现名称
     */
    String name();

    /**
     * 实现类型
     */
    ExtImplType type() default ExtImplType.PLATFORM;

    /**
     * 实现描述
     */
    String description() default "";

    /**
     * 执行顺序，数字越小越优先
     */
    int order() default 0;

    /**
     * 是否启用
     */
    boolean enabled() default true;

    /**
     * 路由条件，支持 SpEL 表达式
     * 例如: "#{environment.getProperty('app.env') == 'prod'}"
     */
    String condition() default "";

    /**
     * 权重，用于负载均衡
     */
    int weight() default 100;
}