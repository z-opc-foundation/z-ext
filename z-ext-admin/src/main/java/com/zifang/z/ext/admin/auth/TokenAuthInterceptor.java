package com.zifang.z.ext.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bearer 令牌校验：控制台侧 {@code /api/ext/instances/**} 与本地控制面都在保护范围。
 * <p>
 * 401 响应体不带 errorCode（平台会话 401 形态），前端拦截器据此跳登录页而非当业务错误。
 */
public class TokenAuthInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;

    public TokenAuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = TokenStore.bearerOf(request.getHeader("Authorization"));
        if (tokenStore.isValid(token)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":401,\"message\":\"未登录或Token无效\",\"data\":null}");
        return false;
    }
}
