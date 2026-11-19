package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.mock.MockExtImplFactory;
import com.zifang.z.ext.mock.MockSpec;
import com.zifang.z.ext.preset.PresetAdviceExtPoint;
import com.zifang.z.ext.preset.PresetEventExtPoint;
import com.zifang.z.ext.preset.PresetGateExtPoint;
import com.zifang.z.ext.preset.PresetPoints;
import com.zifang.z.ext.spring.ExtProxyBuilder;
import com.zifang.z.ext.spring.ZExtAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预置埋点自动装配的行为测试 —— 「引依赖即有可被操控的点」的兑现验证。
 */
class PresetGateAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZExtAutoConfiguration.class,
                    PresetGateAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @Test
    void presetPointsRegisteredWithoutAnyHostCode() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PresetGateExtPoint.class);
            assertThat(context).hasSingleBean(PresetAdviceExtPoint.class);
            assertThat(context).hasSingleBean(PresetEventExtPoint.class);

            assertThat(ExtRegistry.getPoint(PresetPoints.GATE)).isNotNull();
            assertThat(ExtRegistry.getPoint(PresetPoints.ADVICE)).isNotNull();
            assertThat(ExtRegistry.getPoint(PresetPoints.EVENT)).isNotNull();
        });
    }

    @Test
    void gateAllowsByDefault() {
        runner.run(context -> {
            PresetGateExtPoint gate = context.getBean(PresetGateExtPoint.class);
            assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isTrue();
        });
    }

    @Test
    void activatedMockTurnsGateIntoDenier() {
        runner.run(context -> {
            MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                    MockSpec.denying("mock-deny", "演练封禁"));
            ExtRegistry.setActiveImpl(PresetPoints.GATE, "mock-deny");

            PresetGateExtPoint gate = context.getBean(PresetGateExtPoint.class);
            assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isFalse();
            assertThat(gate.denyMessage("http.GET./x", Collections.emptyMap())).isEqualTo("演练封禁");
        });
    }

    @Test
    void presetCanBeDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ZExtAutoConfiguration.class,
                        PresetGateAutoConfiguration.class))
                .withPropertyValues("z.ext.preset.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PresetGateExtPoint.class);
                    assertThat(ExtRegistry.getPoint(PresetPoints.GATE)).isNull();
                });
    }

    @Test
    void proxyBuilderIsUsableForCustomPoints() {
        runner.run(context -> {
            ExtProxyBuilder builder = context.getBean(ExtProxyBuilder.class);
            Object proxy = builder.create(PresetPoints.GATE);
            assertThat(proxy).isInstanceOf(PresetGateExtPoint.class);
        });
    }
}
