package com.zifang.z.ext.admin.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册：除登录与静态资源 / 文档外全部要求 Bearer 令牌。
 * 业务实例侧 {@code /api/ext/instances/**} 也在此范围内（代理时透传实例自己的令牌）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenStore tokenStore;

    public WebMvcConfig(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TokenAuthInterceptor(tokenStore))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        // SPA 前端路由（深链接刷新时由 SpaWebConfig 回 index.html）
                        "/z-ext",
                        "/z-ext/**");
    }
}
