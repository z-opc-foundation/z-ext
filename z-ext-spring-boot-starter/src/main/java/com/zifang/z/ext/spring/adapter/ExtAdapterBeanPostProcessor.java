package com.zifang.z.ext.spring.adapter;

import com.zifang.z.ext.adapter.ExtAdapter;
import com.zifang.z.ext.adapter.ExtAdapterRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;

/**
 * 把带 {@link ExtAdapter} 的 Bean 登记为扩展点实现。
 * <p>
 * 业务代码一行不改：现有 Bean 标个注解就成了扩展点的一个可选实现，
 * 控制面可以在「正常实现」与「降级实现」之间切换。
 */
public class ExtAdapterBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LogManager.getLogger(ExtAdapterBeanPostProcessor.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = ClassUtils.getUserClass(bean.getClass());
        ExtAdapter annotation = targetClass.getAnnotation(ExtAdapter.class);
        if (annotation == null) {
            return bean;
        }
        try {
            boolean registered = ExtAdapterRegistrar.register(annotation.point(), bean, annotation.name());
            if (registered) {
                log.info("既有 Bean 适配为扩展点实现：point={} name={} bean={}",
                        annotation.point(), annotation.name(), beanName);
            }
        } catch (Exception e) {
            // 适配失败不该拖挂启动：注解写错（点不存在/接口不匹配）是宿主自己的问题，
            // 但服务仍要能起来，否则一个注解错误会变成整个服务不可用。
            log.error("扩展点适配失败（point={} bean={}）：{}", annotation.point(), beanName, e.getMessage());
        }
        return bean;
    }
}
