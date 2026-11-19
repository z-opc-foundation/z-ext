package com.zifang.z.ext.adapter;

import java.lang.annotation.*;

/**
 * 「把既有 Bean 变成扩展实现」的标记。
 * <p>
 * 这一档侵入方式解决的是：业务里已经有写好的实现（正常流程 / 降级流程 / 影子流程），
 * 不想为了接入扩展点再写一个 {@code @ExtImpl} 包装类 ——
 * 直接在原类上标这个注解，它就成了某个扩展点的一个可选实现，
 * 控制面可以在「正常实现」与「降级实现」之间一键切换。
 * <p>
 * 前提：该 Bean 实现了目标扩展点的接口。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ExtAdapter {

    /**
     * 目标扩展点标识。
     */
    String point();

    /**
     * 实现名（控制面按这个名字切换）。
     * <p>
     * 留空则取类名的首字母小写形式。
     */
    String name() default "";

    /**
     * 描述。
     */
    String description() default "";

    /**
     * 执行顺序，数字越小越优先。
     */
    int order() default 0;

    /**
     * 是否启用。
     */
    boolean enabled() default true;
}
