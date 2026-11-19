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

    /**
     * 实例自登记令牌（环境变量 ZEXT_CONSOLE_REG_TOKEN）。
     * <p>
     * 实例侧持同款令牌才能写登记端点；未配置 ⇒ 登记一律拒绝（fail-closed）——
     * 否则攻击者可登记一个自己控制的 baseUrl，再借控制台的代理透传拿走操作员令牌。
     */
    private String regToken;

    public String getRegToken() {
        return regToken;
    }

    public void setRegToken(String regToken) {
        this.regToken = regToken;
    }

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
