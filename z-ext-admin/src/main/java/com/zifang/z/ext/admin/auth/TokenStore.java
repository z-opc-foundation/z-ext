package com.zifang.z.ext.admin.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录令牌（内存态，重启即失效）。
 * <p>
 * 控制台是运营入口，令牌只做会话识别，权限粒度留给部署侧（网关 / 内网隔离）。
 */
@Component
public class TokenStore {

    private final Set<String> tokens = ConcurrentHashMap.newKeySet();

    public String issue() {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.add(token);
        return token;
    }

    public boolean isValid(String token) {
        return token != null && !token.isBlank() && tokens.contains(token);
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /**
     * 从 Authorization 头解析 Bearer 令牌。
     */
    public static String bearerOf(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
