package com.zifang.z.ext.annotation;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用扩展平台SDK
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(ExtRegistrar.class)
public @interface EnableExt {

    /**
     * 扫描的基础包路径
     */
    String[] basePackages() default {};

    /**
     * 是否启用自动代理
     */
    boolean proxyTargetClass() default true;
}