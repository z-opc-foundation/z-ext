package com.zifang.z.ext.admin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminAuthProperties auth;
    private final TokenStore tokenStore;

    public AuthController(AdminAuthProperties auth, TokenStore tokenStore) {
        this.auth = auth;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        if (!auth.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", 503,
                    "data", "",
                    "message", "控制台未配置账号（环境变量 ZEXT_ADMIN_USERNAME / ZEXT_ADMIN_PASSWORD）"));
        }
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (!auth.getUsername().equals(username) || !auth.getPassword().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", 401,
                    "data", "",
                    "message", "用户名或密码错误"));
        }
        return ResponseEntity.ok(Map.of(
                "token", tokenStore.issue(),
                "user", Map.of("username", username)));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(java.security.Principal ignored,
                                      jakarta.servlet.http.HttpServletRequest request) {
        tokenStore.revoke(TokenStore.bearerOf(request.getHeader("Authorization")));
        return Map.of("code", 200, "data", "", "message", "ok");
    }
}
