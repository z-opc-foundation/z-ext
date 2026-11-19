package com.zifang.z.ext.aop;

import com.zifang.z.ext.preset.PresetPoints;

import java.lang.annotation.*;

/**
 * 「把既有方法织入扩展点」的标记 —— 侵入最轻的一档。
 * <p>
 * 在任意 Spring Bean 的方法上标这个注解，调用该方法时会自动经过扩展点，
 * <b>调用点代码一行都不用改</b>：原来调 {@code service.doX()}，标完之后
 * 调的还是 {@code service.doX()}，但控制面可以在中途放行、改写结果或中断。
 * <p>
 * 与 {@code @ExtPoint} 的分工：这个注解不新增扩展点，
 * 它把方法挂到某个已存在的扩展点上（默认是预置环绕点）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ExtHook {

    /**
     * 目标扩展点标识。
     * <p>
     * 默认挂到预置环绕点 {@link PresetPoints#ADVICE}：环绕语义最通用，
     * 既能放行改写，也能中断。
     */
    String point() default PresetPoints.ADVICE;

    /**
     * 事件标识（控制面据此做条件匹配）。
     * <p>
     * 留空则取「类名.方法名」。
     */
    String event() default "";

    /**
     * 是否启用。
     */
    boolean enabled() default true;
}
