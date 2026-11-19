package com.zifang.z.ext.adapter;

import com.zifang.z.ext.core.proxy.ExtProxyFactory;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.preset.PresetGateExtPoint;
import com.zifang.z.ext.preset.PresetPoints;
import com.zifang.z.ext.preset.PresetRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 既有实现适配为扩展点的行为测试。
 */
class ExtAdapterRegistrarTest {

    /**
     * 既有业务实现：正常流程。
     */
    static class NormalFlow implements PresetGateExtPoint {

        @Override
        public boolean allow(String event, Map<String, Object> attrs) {
            return true;
        }

        @Override
        public String denyMessage(String event, Map<String, Object> attrs) {
            return null;
        }
    }

    /**
     * 既有业务实现：降级流程。
     */
    static class DegradedFlow implements PresetGateExtPoint {

        @Override
        public boolean allow(String event, Map<String, Object> attrs) {
            return false;
        }

        @Override
        public String denyMessage(String event, Map<String, Object> attrs) {
            return "服务降级中";
        }
    }

    @BeforeEach
    void setUp() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
        PresetRegistrar.ensureRegistered();
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @Test
    void existingBeanBecomesSwitchableImplementation() {
        assertThat(ExtAdapterRegistrar.register(PresetPoints.GATE, new DegradedFlow(), "degraded")).isTrue();

        PresetGateExtPoint gate = ExtProxyFactory.createProxy(PresetPoints.GATE);
        assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isTrue();

        ExtRegistry.setActiveImpl(PresetPoints.GATE, "degraded");

        assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isFalse();
        assertThat(gate.denyMessage("http.GET./x", Collections.emptyMap())).isEqualTo("服务降级中");
    }

    @Test
    void defaultNameIsClassNameWithLowercaseFirstLetter() {
        assertThat(ExtAdapterRegistrar.register(PresetPoints.GATE, new NormalFlow())).isTrue();

        assertThat(ExtRegistry.getImplementations(PresetPoints.GATE))
                .extracting(impl -> impl.getName())
                .contains("normalFlow");
    }

    @Test
    void beanNotImplementingPointInterfaceIsRejected() {
        assertThatThrownBy(() -> ExtAdapterRegistrar.register(PresetPoints.GATE, "not-a-bean"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownPointIsRejected() {
        assertThatThrownBy(() -> ExtAdapterRegistrar.register("no.such.point", new NormalFlow()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扩展点不存在");
    }
}
