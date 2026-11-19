package com.zifang.z.ext.spring.aop;

import com.zifang.z.ext.aop.ExtHook;
import com.zifang.z.ext.spring.ExtProxyBuilder;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * {@link ExtHook} 织入的自动装配。
 * <p>
 * <b>与宿主自身 AOP 的关系</b>：本件只提供 Advisor，织入由容器里的自动代理器完成。
 * 宿主若已有任何自动代理器（Boot 默认的 {@code AopAutoConfiguration}、
 * {@code @EnableAspectJAutoProxy} 等），本件**不再补**，由它拾取本件提供的 Advisor；
 * 宿主若一个自动代理器都没有，本件补一个 {@link DefaultAdvisorAutoProxyCreator}。
 * <p>
 * <b>为什么条件宽到 {@link AbstractAutoProxyCreator} 且要排在 {@link AopAutoConfiguration} 之后</b>：
 * 2026-11-20 aio 实测，窄条件 {@code @ConditionalOnMissingBean(DefaultAdvisorAutoProxyCreator.class)}
 * 认不出宿主的 {@code AnnotationAwareAspectJAutoProxyCreator}（非其子类），叠加出
 * 「CGLIB 外再包一层 JDK 代理」的双重代理 —— JDK 代理不继承具体类，按具体类型
 * {@code @Resource} 注入直接 {@code BeanNotOfRequiredTypeException}。
 * auto-config 默认按类名字母序评估，{@code com.zifang...} 在 {@code org.springframework...}
 * 之前，条件判断时 Boot 的 creator 还没注册 ⇒ 必须显式 {@code @AutoConfigureAfter}。
 */
@AutoConfiguration
@AutoConfigureAfter(AopAutoConfiguration.class)
@ConditionalOnClass({Advice.class, DefaultAdvisorAutoProxyCreator.class})
@ConditionalOnProperty(prefix = "z.ext.hook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExtHookAutoConfiguration {

    /**
     * 宿主没有任何自动代理器时才补一个。
     */
    @Bean
    @ConditionalOnMissingBean(AbstractAutoProxyCreator.class)
    public static DefaultAdvisorAutoProxyCreator extHookAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
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
