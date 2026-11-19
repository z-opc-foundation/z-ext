package com.zifang.z.ext.spring.console;

import com.zifang.z.ext.spring.ZExtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 实例自登记的自动装配。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "z.ext.console", name = "enabled", havingValue = "true")
public class ExtConsoleAutoConfiguration {

    /**
     * 登记器：启动就绪时登记，容器关闭时注销。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExtConsoleRegistrar extConsoleRegistrar(ZExtProperties properties, Environment environment) {
        ZExtProperties.Console console = properties.getConsole();
        String instanceName = console.getInstanceName();
        if (instanceName == null || instanceName.isBlank()) {
            instanceName = environment.getProperty("spring.application.name");
        }
        return new ExtConsoleRegistrar(console.getUrl(), instanceName,
                console.getBaseUrl(), console.getTimeoutMs(), console.getRegToken());
    }

    /**
     * 关闭注销监听。
     */
    @Bean
    @ConditionalOnMissingBean
    public org.springframework.context.ApplicationListener<org.springframework.context.event.ContextClosedEvent>
    extConsoleUnregisterListener(ExtConsoleRegistrar registrar) {
        return registrar.closeListener();
    }
}
