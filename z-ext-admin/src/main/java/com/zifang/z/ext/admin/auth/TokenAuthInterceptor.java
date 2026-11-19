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

    /** 实例自登记令牌（可空 ⇒ 登记端点一律 401）。 */
    private final java.util.function.Predicate<String> regTokenChecker;

    private final TokenStore tokenStore;

    public TokenAuthInterceptor(TokenStore tokenStore) {
        this(tokenStore, null);
    }

    public TokenAuthInterceptor(TokenStore tokenStore, String regToken) {
        this.tokenStore = tokenStore;
        this.regTokenChecker = regToken == null || regToken.isBlank()
                ? null
                : candidate -> java.security.MessageDigest.isEqual(
                        candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        regToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = TokenStore.bearerOf(request.getHeader("Authorization"));
        if (tokenStore.isValid(token)) {
            return true;
        }
        if (isRegistrationCall(request)
                && regTokenChecker != null
                && regTokenChecker.test(nullToEmpty(request.getHeader("X-Reg-Token")))) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":401,\"message\":\"未登录或Token无效\",\"data\":null}");
        return false;
    }

    /**
     * 是否实例登记端点：仅 POST /api/ext/instances 与 DELETE /api/ext/instances/{name}。
     * 其余（含代理透传面）不接受登记令牌 —— 登记令牌权限只有「登记/注销自己」。
     */
    private boolean isRegistrationCall(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if ("POST".equals(method)) {
            return uri.endsWith("/api/ext/instances");
        }
        if ("DELETE".equals(method)) {
            String after = uri.substring(uri.lastIndexOf("/api/ext/instances/")
                    + "/api/ext/instances/".length());
            return uri.contains("/api/ext/instances/") && !after.contains("/");
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
