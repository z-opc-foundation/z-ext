package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.preset.PresetEventExtPoint;
import com.zifang.z.ext.preset.PresetGateExtPoint;
import com.zifang.z.ext.spring.ZExtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HTTP 闸门落点的装配 —— 只在 servlet web 宿主里生效。
 * <p>
 * 条件里不出现 servlet 类型（字符串形式判存在）：Boot 2（javax）与 Boot 3（jakarta）
 * 两套命名空间里都能跑，落点实现本身也不引用任何一方（见 {@link PresetGateInterceptor}）。
 */
@AutoConfiguration(after = PresetGateAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "z.ext.preset.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PresetWebAutoConfiguration implements WebMvcConfigurer {

    /** 闸门点。 */
    private final PresetGateExtPoint gate;

    /** 通知点。 */
    private final PresetEventExtPoint events;

    /** 应用名。 */
    private final String appName;

    /** 拒绝状态码。 */
    private final int denyStatus;

    /** 闸门异常是否放行。 */
    private final boolean failOpen;

    /** 内嵌控制面根路径。 */
    private final String adminBasePath;

    public PresetWebAutoConfiguration(PresetGateExtPoint gate, PresetEventExtPoint events,
                                      ZExtProperties properties, Environment environment) {
        this.gate = gate;
        this.events = events;
        this.appName = resolveAppName(environment, properties);
        ZExtProperties.Preset.Http http = properties.getPreset().getHttp();
        this.denyStatus = http.getDenyStatus();
        this.failOpen = http.isFailOpen();
        this.adminBasePath = properties.getAdminBasePath();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用运行时代理包成 HandlerInterceptor：不按 javax/jakarta 任何一种命名空间编译，
        // 两类宿主都能挂上这个闸门。
        // 控制面路径必须豁免：闸门一旦被切成 DENY，唯一能远程回切的通道就是控制面本身，
        // 拦了它 = 运维把自己锁在门外（1.2.4 端到端演练实测踩中）。
        registry.addInterceptor(PresetGateInterceptor.asHandlerInterceptor(
                        new PresetGateInterceptor(gate, events, appName, denyStatus, failOpen)))
                .addPathPatterns("/**")
                .excludePathPatterns(adminBasePath + "/**");
    }

    private static String resolveAppName(Environment environment, ZExtProperties properties) {
        String name = environment.getProperty("spring.application.name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (properties.getInstanceName() != null && !properties.getInstanceName().isBlank()) {
            return properties.getInstanceName();
        }
        return "unknown";
    }
}
