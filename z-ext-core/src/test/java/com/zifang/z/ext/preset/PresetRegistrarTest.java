package com.zifang.z.ext.preset;

import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预置点注册行为测试。
 */
class PresetRegistrarTest {

    @BeforeEach
    void setUp() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @Test
    void registersThreePresetPointsWithDefaults() {
        PresetRegistrar.ensureRegistered();

        assertThat(ExtRegistry.getPoint(PresetPoints.GATE)).isNotNull();
        assertThat(ExtRegistry.getPoint(PresetPoints.ADVICE)).isNotNull();
        assertThat(ExtRegistry.getPoint(PresetPoints.EVENT)).isNotNull();

        assertThat(ExtRegistry.getImplementations(PresetPoints.GATE))
                .extracting(ExtImplDefinition::getName).contains("platform-default");
        assertThat(ExtRegistry.getImplementations(PresetPoints.ADVICE))
                .extracting(ExtImplDefinition::getName).contains("platform-default");
        assertThat(ExtRegistry.getImplementations(PresetPoints.EVENT))
                .extracting(ExtImplDefinition::getName).contains("platform-default");
    }

    @Test
    void registrationIsIdempotent() {
        PresetRegistrar.ensureRegistered();
        PresetRegistrar.ensureRegistered();

        assertThat(ExtRegistry.getImplementations(PresetPoints.GATE)).hasSize(1);
        assertThat(ExtRegistry.getImplementations(PresetPoints.ADVICE)).hasSize(1);
        assertThat(ExtRegistry.getImplementations(PresetPoints.EVENT)).hasSize(1);
    }

    @Test
    void defaultGateAllowsEverything() {
        PresetRegistrar.ensureRegistered();
        PresetGateExtPoint gate = new PresetGateDefaultExtPoint();

        assertThat(gate.allow("http.GET./x", Collections.emptyMap())).isTrue();
        assertThat(gate.denyMessage("http.GET./x", Collections.emptyMap())).isNull();
    }

    @Test
    void defaultAdvicePassesThrough() throws Throwable {
        PresetRegistrar.ensureRegistered();
        PresetAdviceExtPoint advice = new PresetAdviceDefaultExtPoint();

        Object result = advice.around("evt", Collections.emptyMap(), () -> "原始结果");

        assertThat(result).isEqualTo("原始结果");
    }
}
