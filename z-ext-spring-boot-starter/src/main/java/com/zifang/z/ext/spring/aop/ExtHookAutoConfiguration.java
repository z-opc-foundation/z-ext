package com.zifang.z.ext.spring.aop;

import com.zifang.z.ext.aop.ExtHook;
import com.zifang.z.ext.spring.ExtProxyBuilder;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * {@link ExtHook} 织入的自动装配。
 * <p>
 * <b>与宿主自身 AOP 的关系</b>：本件只提供 Advisor，织入由容器里的自动代理器完成。
 * 宿主若已有 {@code @EnableAspectJAutoProxy}，那个织入器优先级更高、会被使用，
 * 而它同样会拾取本件提供的 Advisor —— 两种情况都生效，宿主不必二选一。
 * 宿主若一个自动代理器都没有，本件补一个 {@link DefaultAdvisorAutoProxyCreator}。
 */
@AutoConfiguration
@ConditionalOnClass({Advice.class, DefaultAdvisorAutoProxyCreator.class})
@ConditionalOnProperty(prefix = "z.ext.hook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExtHookAutoConfiguration {

    /**
     * 宿主没有任何自动代理器时补一个。
     */
    @Bean
    @ConditionalOnMissingBean(DefaultAdvisorAutoProxyCreator.class)
    public static DefaultAdvisorAutoProxyCreator extHookAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setOrder(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
        return creator;
    }

    /**
     * 织入 {@link ExtHook} 标注的方法。
     */
    @Bean
    @ConditionalOnMissingBean(name = "extHookAdvisor")
    public Advisor extHookAdvisor(ExtProxyBuilder builder) {
        // 三参构造：第一个是「类注解」、第二个是「方法注解」。
        // @ExtHook 打在方法上 ⇒ 必须走 (null, ExtHook.class, true)，
        // 用两参构造会匹配成「类上有 @ExtHook」，一个方法都命中不了。
        Pointcut pointcut = new AnnotationMatchingPointcut(null, ExtHook.class, true);
        return new DefaultPointcutAdvisor(pointcut, new ExtHookInterceptor(builder));
    }
}
