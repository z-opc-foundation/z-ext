package com.zifang.z.ext.mock;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * mock 实现的行为测试 —— 「零代码接管」的落地验证。
 */
class MockExtImplFactoryTest {

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
    void denyingMockBlocksGateAfterActivation() {
        MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                MockSpec.denying("mock-deny", "演练封禁"));

        PresetGateExtPoint gate = ExtProxyFactory.createProxy(PresetPoints.GATE);
        // 未激活时走平台默认 ⇒ 放行
        assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isTrue();

        ExtRegistry.setActiveImpl(PresetPoints.GATE, "mock-deny");

        assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isFalse();
        assertThat(gate.denyMessage("http.GET./x", Collections.emptyMap())).isEqualTo("演练封禁");
    }

    @Test
    void throwingMockPropagatesException() {
        MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                MockSpec.throwing("mock-throw", "熔断演练"));

        PresetGateExtPoint gate = ExtProxyFactory.createProxy(PresetPoints.GATE);
        ExtRegistry.setActiveImpl(PresetPoints.GATE, "mock-throw");

        assertThatThrownBy(() -> gate.allow("http.GET./x", Collections.emptyMap()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("熔断演练");
    }

    @Test
    void returningMockCoercesToMethodSignature() {
        MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                MockSpec.returning("mock-return", Boolean.TRUE));

        PresetGateExtPoint gate = ExtProxyFactory.createProxy(PresetPoints.GATE);
        ExtRegistry.setActiveImpl(PresetPoints.GATE, "mock-return");

        assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isTrue();
    }

    @Test
    void registeringSameNameTwiceReplacesMock() {
        MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                MockSpec.denying("mock", "第一版"));
        MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                MockSpec.returning("mock", Boolean.TRUE));

        assertThat(ExtRegistry.getImplementations(PresetPoints.GATE))
                .filteredOn(impl -> "mock".equals(impl.getName()))
                .hasSize(1);
    }

    @Test
    void unknownPointIsRejected() {
        assertThatThrownBy(() -> MockExtImplFactory.register("no.such.point",
                PresetGateExtPoint.class, MockSpec.returning("x", Boolean.TRUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扩展点不存在");
    }
}
