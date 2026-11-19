package com.zifang.z.ext.spring;

import com.zifang.z.ext.annotation.ExtImpl;
import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.ActiveImplStore;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.spring.admin.ExtAdminController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配行为测试。
 */
class ZExtAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ZExtAutoConfiguration.class));

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner(
            AnnotationConfigServletWebApplicationContext::new)
            .withConfiguration(AutoConfigurations.of(ZExtAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @Test
    void registersDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(LocalActiveImplStore.class);
            assertThat(context).hasSingleBean(com.zifang.z.ext.core.router.ExtRouter.class);
            assertThat(context).hasSingleBean(com.zifang.z.ext.core.admin.ExtAdminProvider.class);
            assertThat(context).hasSingleBean(ExtProxyBuilder.class);
            assertThat(context).hasSingleBean(ExtImplInstanceBinder.class);

            // 容器里的 store 必须已装进注册表——这是「切换真生效」的前提
            assertThat(ExtRegistry.getActiveImplStore())
                    .isSameAs(context.getBean(ActiveImplStore.class));
        });
    }

    @Test
    void instanceNamePrefersSpringApplicationName() {
        runner.withPropertyValues("spring.application.name=demo-app")
                .run(context -> {
                    com.zifang.z.ext.core.admin.ExtAdminProvider provider =
                            context.getBean(com.zifang.z.ext.core.admin.ExtAdminProvider.class);
                    assertThat(provider.snapshot().getInstance()).isEqualTo("demo-app");
                });
    }

    @Test
    void customStoreWinsAndIsInstalled() {
        runner.withUserConfiguration(CustomStoreConfig.class).run(context -> {
            ActiveImplStore store = context.getBean(ActiveImplStore.class);
            assertThat(store).isNotInstanceOf(LocalActiveImplStore.class);
            assertThat(ExtRegistry.getActiveImplStore()).isSameAs(store);
        });
    }

    @Test
    void adminControllerOnlyInServletWebApp() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean(ExtAdminController.class));
        webRunner.run(context ->
                assertThat(context).hasSingleBean(ExtAdminController.class));
    }

    @Test
    void adminCanBeDisabled() {
        webRunner.withPropertyValues("z.ext.admin.enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(ExtAdminController.class));
    }

    @Test
    void asyncExecutorOnlyWhenEnabled() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean("zExtAsyncExecutor"));
        runner.withPropertyValues("z.ext.async.enabled=true")
                .run(context ->
                        assertThat(context).hasBean("zExtAsyncExecutor"));
    }

    /**
     * 回归：宿主容器里 Executor 有多个候选（Boot 的 applicationTaskExecutor +
     * {@code @EnableScheduling} 的 taskScheduler）时，extProxyBuilder 装配不得拖挂启动。
     * 曾用 {@code getIfAvailable()} 撞 NoUniqueBeanDefinitionException —— 歧义时应退 null
     * （不配异步执行器 = ASYNC 点退化同步）。
     */
    @Test
    void proxyBuilderSurvivesAmbiguousExecutorCandidates() {
        runner.withUserConfiguration(TwoExecutorsConfig.class).run(context -> {
            assertThat(context).hasSingleBean(ExtProxyBuilder.class);
            assertThat(context.getBean(ExtProxyBuilder.class).getAsyncExecutor()).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoExecutorsConfig {
        @Bean
        java.util.concurrent.Executor applicationTaskExecutor() {
            return Runnable::run;
        }

        @Bean
        java.util.concurrent.Executor taskScheduler() {
            return Runnable::run;
        }
    }

    @Test
    void instanceBinderBackfillsSpringBeanIntoRegistry() {
        runner.withUserConfiguration(ExtImplBeanConfig.class).run(context -> {
            ExtImplDefinition definition = ExtRegistry.getImplementations("order.service").stream()
                    .filter(d -> "inlined".equals(d.getName()))
                    .findFirst().orElseThrow();
            assertThat(definition.getInstance()).isInstanceOf(InlinedOrderService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStoreConfig {
        @Bean
        public ActiveImplStore customActiveImplStore() {
            return new ActiveImplStore() {
                private final Map<String, String> state = new ConcurrentHashMap<>();

                @Override
                public void setActiveImpl(String point, String implName) {
                    if (implName == null) {
                        state.remove(point);
                    } else {
                        state.put(point, implName);
                    }
                }

                @Override
                public String getActiveImpl(String point) {
                    return state.get(point);
                }

                @Override
                public Map<String, String> getAll() {
                    return new ConcurrentHashMap<>(state);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExtImplBeanConfig {

        @Bean
        public ExtRegistrarBridge registrarBridge() {
            // 模拟 @EnableExt 扫描产物：先把定义登记进注册表
            ExtImplDefinition definition = new ExtImplDefinition();
            definition.setPoint("order.service");
            definition.setName("inlined");
            definition.setType(ExtImplType.CUSTOM);
            definition.setImplClass(InlinedOrderService.class);
            definition.setEnabled(true);
            ExtRegistry.registerImpl(definition);
            return new ExtRegistrarBridge();
        }

        @Bean
        public InlinedOrderService inlinedOrderService() {
            return new InlinedOrderService();
        }
    }

    /**
     * 占位：仅为了让配置类被加载。
     */
    static class ExtRegistrarBridge {
    }

    @ExtImpl(point = "order.service", name = "inlined", type = ExtImplType.CUSTOM)
    static class InlinedOrderService {
    }
}
