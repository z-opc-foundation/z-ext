package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.preset.PresetAdviceExtPoint;
import com.zifang.z.ext.preset.PresetEventExtPoint;
import com.zifang.z.ext.preset.PresetGateExtPoint;
import com.zifang.z.ext.preset.PresetPoints;
import com.zifang.z.ext.preset.PresetRegistrar;
import com.zifang.z.ext.spring.ExtProxyBuilder;
import com.zifang.z.ext.spring.ZExtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 预置埋点自动装配 —— 「零业务侵入」那一档的入口。
 * <p>
 * 宿主<b>什么都不用写</b>：引依赖即得到三个可被控制面接管的点
 * （闸门 / 环绕 / 通知），控制台上立刻可见、可切换。
 * <p>
 * 这里刻意<b>不</b>依赖 {@code @EnableExt}：那要求业务在启动类上再加一行注解，
 * 与「引入即生效」的承诺不符。预置点由 {@link PresetRegistrar} 直接写注册表。
 * <p>
 * 业务自定义的扩展点仍然走 {@code @ExtPoint/@ExtImpl/@EnableExt} 那一档，
 * 两者共用同一份注册表与同一套控制面。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "z.ext.preset", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PresetGateAutoConfiguration {

    /**
     * 注册预置点。做成静态 Bean 是为了让它先于代理 Bean 创建 ——
     * 代理创建时就要按点标识解析扩展点定义，注册晚一步就会「点不存在」。
     */
    @Bean
    public static PresetRegistrarMarker presetRegistrar() {
        PresetRegistrar.ensureRegistered();
        return new PresetRegistrarMarker();
    }

    /**
     * 闸门点代理。
     */
    @Bean
    @ConditionalOnMissingBean
    public PresetGateExtPoint presetGateExtPoint(PresetRegistrarMarker marker, ExtProxyBuilder builder) {
        return builder.create(PresetPoints.GATE);
    }

    /**
     * 环绕点代理。
     */
    @Bean
    @ConditionalOnMissingBean
    public PresetAdviceExtPoint presetAdviceExtPoint(PresetRegistrarMarker marker, ExtProxyBuilder builder) {
        return builder.create(PresetPoints.ADVICE);
    }

    /**
     * 通知点代理。
     */
    @Bean
    @ConditionalOnMissingBean
    public PresetEventExtPoint presetEventExtPoint(PresetRegistrarMarker marker, ExtProxyBuilder builder) {
        return builder.create(PresetPoints.EVENT);
    }

    /**
     * 启动就绪事件。
     */
    @Bean
    @ConditionalOnMissingBean
    public PresetLifecycleListener presetLifecycleListener(PresetEventExtPoint events,
                                                          Environment environment,
                                                          ZExtProperties properties) {
        return new PresetLifecycleListener(events, appNameOf(environment, properties));
    }

    /**
     * 关闭事件。
     */
    @Bean
    @ConditionalOnMissingBean
    public PresetShutdownListener presetShutdownListener(PresetEventExtPoint events,
                                                        Environment environment,
                                                        ZExtProperties properties) {
        return new PresetShutdownListener(events, appNameOf(environment, properties));
    }

    private static String appNameOf(Environment environment, ZExtProperties properties) {
        String name = environment.getProperty("spring.application.name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (properties.getInstanceName() != null && !properties.getInstanceName().isBlank()) {
            return properties.getInstanceName();
        }
        return "unknown";
    }

    /**
     * 注册顺序标记。宿主可以依赖它来确保「点已入册」。
     */
    public static class PresetRegistrarMarker {

        PresetRegistrarMarker() {
        }
    }

    /**
     * 供落点组件复用：注册表里此刻是否真的有闸门点。
     */
    static boolean gateRegistered() {
        return ExtRegistry.getPoint(PresetPoints.GATE) != null;
    }
}
