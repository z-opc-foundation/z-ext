package com.zifang.z.ext.spring;

import com.zifang.z.ext.core.admin.DefaultExtAdminProvider;
import com.zifang.z.ext.core.admin.ExtAdminProvider;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.router.DefaultExtRouter;
import com.zifang.z.ext.core.router.ExtRouter;
import com.zifang.z.ext.core.router.SpelConditionEvaluator;
import com.zifang.z.ext.core.store.ActiveImplStore;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.spring.admin.ExtAdapterController;
import com.zifang.z.ext.spring.admin.ExtAdminController;
import com.zifang.z.ext.spring.admin.ExtMockController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executor;

/**
 * z-ext 自动装配。
 * <p>
 * 提供四件事：
 * <ol>
 *   <li>默认 {@link ActiveImplStore}（可用自有 Bean 替换，换成配置中心/远端实现即接入
 *       外置形态）；</li>
 *   <li>SpEL 条件路由（表达式里的 {@code #environment} 绑定宿主 Spring Environment）；</li>
 *   <li>{@link ExtImplInstanceBinder}：实现类同时是 Spring Bean 时回填容器实例；</li>
 *   <li>嵌入式控制面（servlet web 宿主 + {@code z.ext.admin.enabled=true}，默认开）。</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass(ExtRegistry.class)
@EnableConfigurationProperties(ZExtProperties.class)
public class ZExtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActiveImplStore.class)
    public LocalActiveImplStore localActiveImplStore() {
        return new LocalActiveImplStore();
    }

    @Bean
    public static ExtImplInstanceBinder extImplInstanceBinder() {
        return new ExtImplInstanceBinder();
    }

    @Bean
    @ConditionalOnMissingBean(ExtRouter.class)
    public DefaultExtRouter extRouter(Environment environment) {
        return new DefaultExtRouter(new SpelConditionEvaluator(environment));
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtAdminProvider extAdminProvider(ZExtProperties properties,
                                             ActiveImplStore activeImplStore,
                                             Environment environment) {
        // 激活状态的权威落点：无论默认还是用户提供的 store，都装进注册表
        ExtRegistry.setActiveImplStore(activeImplStore);

        String appName = environment.getProperty("spring.application.name");
        if (appName != null && !appName.isBlank()) {
            return new DefaultExtAdminProvider(appName);
        }
        if (properties.getInstanceName() != null && !properties.getInstanceName().isBlank()) {
            return new DefaultExtAdminProvider(properties.getInstanceName());
        }
        return new DefaultExtAdminProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtProxyBuilder extProxyBuilder(ObjectProvider<ExtRouter> routerProvider,
                                           ObjectProvider<Executor> asyncExecutorProvider) {
        // getIfUnique 而非 getIfAvailable：宿主容器里 Executor 常有多候选
        // （applicationTaskExecutor + @EnableScheduling 的 taskScheduler），
        // getIfAvailable 撞上歧义直接抛 NoUniqueBeanDefinitionException 拖挂启动；
        // 歧义时退 null = 不配异步执行器，ASYNC 点退化为同步，与缺省行为一致。
        return new ExtProxyBuilder(routerProvider.getIfUnique(), asyncExecutorProvider.getIfUnique());
    }

    @Bean
    @ConditionalOnProperty(prefix = "z.ext.async", name = "enabled", havingValue = "true")
    public ThreadPoolTaskExecutor zExtAsyncExecutor(ZExtProperties properties) {
        ZExtProperties.Async async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix(async.getThreadNamePrefix());
        executor.setDaemon(true);
        return executor;
    }

    /**
     * 嵌入式控制面。独立在嵌套配置里：非 web 宿主不加载这个类，
     * 外层类保持零 web 类型引用。
     *
     * <p>条件只查 RestController + servlet web 环境，不查 Servlet 类型本身：
     * Boot 2 宿主（javax.servlet）与 Boot 3 宿主（jakarta.servlet）命名空间不同，
     * 这里一旦引用任何一个，另一个命名空间的宿主会因条件类缺失而
     * <b>静默不装配</b>（2026-11-19 demo 宿主端到端实测踩中：Boot 2.7 宿主
     * /api/ext/** 整体 404）。SERVLET web 环境本身已保证 servlet API 在场。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestController.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "z.ext.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ZExtAdminEndpointConfiguration {

        @Bean
        public ExtAdminController extAdminController(ExtAdminProvider provider) {
            return new ExtAdminController(provider);
        }

        @Bean
        public ExtMockController extMockController() {
            return new ExtMockController();
        }

        @Bean
        public ExtAdapterController extAdapterController(ApplicationContext applicationContext) {
            return new ExtAdapterController(applicationContext);
        }
    }
}



