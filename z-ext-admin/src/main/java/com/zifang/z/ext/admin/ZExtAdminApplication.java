package com.zifang.z.ext.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.zifang.z.ext.admin.auth.AdminAuthProperties;

/**
 * z-ext 管理控制台。
 * <p>
 * 双重身份：自身通过 z-ext-spring-boot-starter 持有一份本地扩展点注册表；
 * 同时聚合各业务实例的嵌入式控制面（{@code /api/ext/instances/**} 代理转发），
 * 是多进程部署下的介入操作入口。
 */
@SpringBootApplication
@EnableConfigurationProperties(AdminAuthProperties.class)
public class ZExtAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZExtAdminApplication.class, args);
    }
}
