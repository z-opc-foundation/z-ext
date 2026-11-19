package com.zifang.z.ext.spring.aop;

import com.zifang.z.ext.aop.ExtHook;
import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.preset.PresetAdviceExtPoint;
import com.zifang.z.ext.preset.PresetPoints;
import com.zifang.z.ext.preset.PresetRegistrar;
import com.zifang.z.ext.spring.ZExtAutoConfiguration;
import com.zifang.z.ext.spring.preset.PresetGateAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExtHook} 织入的行为测试 —— 「不改调用点」那一档的兑现验证。
 */
class ExtHookAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZExtAutoConfiguration.class,
                    PresetGateAutoConfiguration.class,
                    ExtHookAutoConfiguration.class))
            .withUserConfiguration(BusinessConfig.class);

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @Test
    void methodRunsNormallyWithoutControlPlane() {
        runner.run(context -> {
            assertThat(context.getBean(BusinessService.class).compute("a")).isEqualTo("原:a");
        });
    }

    @Test
    void activatedAdviceCanRewriteResultWithoutTouchingCallSite() {
        runner.run(context -> {
            registerTakeoverAdvice();

            // 调用点一行没改，结果被扩展点接管
            assertThat(context.getBean(BusinessService.class).compute("a")).isEqualTo("被接管");
        });
    }

    @Test
    void disabledHookIsNotIntercepted() {
        runner.run(context -> {
            registerTakeoverAdvice();

            assertThat(context.getBean(BusinessService.class).untouched("b")).isEqualTo("原:b");
        });
    }

    /**
     * 注册一个「不调用 proceed 直接给结果」的环绕实现。
     */
    private void registerTakeoverAdvice() {
        PresetAdviceExtPoint takeover = new PresetAdviceExtPoint() {
            @Override
            public Object around(String event, Map<String, Object> attrs, ProceedAction action) {
                return "被接管";
            }
        };
        ExtImplDefinition definition = new ExtImplDefinition(
                PresetPoints.ADVICE, "takeover", takeover.getClass());
        definition.setInstance(takeover);
        definition.setType(ExtImplType.CUSTOM);
        definition.setEnabled(true);
        ExtRegistry.registerImpl(definition);
        ExtRegistry.setActiveImpl(PresetPoints.ADVICE, "takeover");
    }

    /**
     * 被织入的业务 Bean。
     */
    static class BusinessService {

        @ExtHook(event = "biz.compute")
        public String compute(String input) {
            return "原:" + input;
        }

        @ExtHook(enabled = false)
        public String untouched(String input) {
            return "原:" + input;
        }
    }

    /**
     * 业务 Bean 的装配。
     */
    @Configuration(proxyBeanMethods = false)
    static class BusinessConfig {

        @Bean
        BusinessService businessService() {
            return new BusinessService();
        }
    }
}
