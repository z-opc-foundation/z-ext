package com.zifang.z.ext.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制台登录凭据。
 * <p>
 * 凭据只从环境变量注入，不落仓（明文口令禁令口径）；两个值任一为空即拒绝所有登录。
 */
@ConfigurationProperties(prefix = "z.ext.console")
public class AdminAuthProperties {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isConfigured() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
