package com.zifang.z.ext.spring;

import com.zifang.z.ext.annotation.ExtImpl;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;

/**
 * 把 Spring 托管的实例回填进扩展实现定义。
 * <p>
 * 框架的 {@code invokeLocal} 在定义上没有实例时会 {@code newInstance()} 造一个裸对象，
 * 实现类里的 {@code @Autowired} 全部为 null。本处理器在被扫到的实现类恰好也是
 * Spring Bean 时（典型场景：实现类带依赖注入、由容器装配），把容器实例回填进注册表，
 * 让实现拿到完整的依赖注入。
 */
public class ExtImplInstanceBinder implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // CGLIB 代理类上读不到注解，取用户类
        Class<?> targetClass = ClassUtils.getUserClass(bean.getClass());
        ExtImpl annotation = targetClass.getAnnotation(ExtImpl.class);
        if (annotation == null) {
            return bean;
        }
        for (ExtImplDefinition definition : ExtRegistry.getImplementations(annotation.point())) {
            if (annotation.name().equals(definition.getName())) {
                definition.setInstance(bean);
            }
        }
        return bean;
    }
}
