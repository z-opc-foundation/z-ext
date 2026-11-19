package com.zifang.z.ext.spring.adapter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 既有实现适配的自动装配。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "z.ext.adapter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExtAdapterAutoConfiguration {

    /**
     * 静态 Bean：BeanPostProcessor 尽早注册，避免容器过早初始化这些 Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public static ExtAdapterBeanPostProcessor extAdapterBeanPostProcessor() {
        return new ExtAdapterBeanPostProcessor();
    }
}
