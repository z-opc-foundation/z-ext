package com.zifang.z.ext.annotation;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * 扩展注册器
 * 扫描并注册扩展点和扩展实现
 */
public class ExtRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        // 获取@EnableExt注解的属性
        Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableExt.class.getName());

        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        String[] basePackages = (String[]) attributes.get("basePackages");

        if (basePackages == null || basePackages.length == 0) {
            // 默认使用当前包
            String basePackage = importingClassMetadata.getClassName();
            basePackages = new String[]{basePackage.substring(0, basePackage.lastIndexOf('.'))};
        }

        // 注册扩展扫描器
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(ExtScanner.class);
        builder.addPropertyValue("basePackages", basePackages);

        registry.registerBeanDefinition(
                "extScanner",
                builder.getBeanDefinition()
        );
    }
}