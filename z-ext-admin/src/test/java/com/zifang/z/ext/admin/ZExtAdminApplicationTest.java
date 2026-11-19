package com.zifang.z.ext.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制台链路端到端：登录 → 令牌 → 本机控制面 → 自环实例代理。
 * <p>
 * 自环（把控制台自己注册成实例）是 Agent 模式的最小证明：控制台转发面 + 目标实例
 * 嵌入式控制面在同一进程内真实跑通一整趟 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ZExtAdminApplicationTest {

    @DynamicPropertySource
    static void consoleCredentials(DynamicPropertyRegistry registry) {
        registry.add("z.ext.console.username", () -> "admin");
        registry.add("z.ext.console.password", () -> "admin123");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String token;

    @BeforeEach
    void login() {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "admin123"), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "登录应成功：" + resp.getBody());
        token = (String) resp.getBody().get("token");
    }

    @AfterEach
    void cleanupInstances() {
        for (Map<String, Object> inst : currentInstances()) {
            exchange(HttpMethod.DELETE, "/api/ext/instances/" + inst.get("name"), null);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> currentInstances() {
        ResponseEntity<Map> resp = exchange(HttpMethod.GET, "/api/ext/instances", null);
        return (List<Map<String, Object>>) resp.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    @DisplayName("未带令牌访问控制面 → 401 平台会话形态（不带 errorCode，前端据此跳登录）")
    @SuppressWarnings("unchecked")
    void rejectsAnonymousAccess() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/ext/instances", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertEquals(401, body.get("code"));
        assertTrue(!body.containsKey("errorCode"), "会话 401 不该带 errorCode");
    }

    @Test
    @DisplayName("错误凭据 → 401 用户名或密码错误")
    @SuppressWarnings("unchecked")
    void loginRejectsBadCredentials() {
        ResponseEntity<Map> bad = rest.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "wrong"), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, bad.getStatusCode());
        assertEquals("用户名或密码错误", bad.getBody().get("message"));
    }

    @Test
    @DisplayName("带令牌访问本机控制面 /api/ext/points → 200 包装体")
    void localControlPlaneReachableWithToken() {
        ResponseEntity<Map> resp = exchange(HttpMethod.GET, "/api/ext/points", null);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "本机控制面应可达：" + resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
        assertTrue(resp.getBody().containsKey("data"));
    }

    @Test
    @DisplayName("实例注册与自环代理：把控制台自己注册成实例，经代理面读到本机扩展点快照")
    @SuppressWarnings("unchecked")
    void selfLoopProxying() {
        // 注册自己
        ResponseEntity<Map> reg = exchange(HttpMethod.POST, "/api/ext/instances",
                Map.of("name", "self", "baseUrl", "http://localhost:" + port));
        assertEquals(HttpStatus.OK, reg.getStatusCode(), "注册实例应成功：" + reg.getBody());
        assertEquals(200, reg.getBody().get("code"));

        List<Map<String, Object>> listed = currentInstances();
        assertEquals(1, listed.size(), "实例表应有且只有 self：" + listed);
        assertEquals("self", listed.get(0).get("name"));

        // 经代理面转发到本机嵌入式控制面（转发 Authorization，目标实例自己的拦截器放行）
        ResponseEntity<Map> proxied = exchange(HttpMethod.GET, "/api/ext/instances/self/points", null);
        assertEquals(HttpStatus.OK, proxied.getStatusCode(), "代理转发应成功：" + proxied.getBody());
        assertEquals(200, proxied.getBody().get("code"));
        assertTrue(proxied.getBody().containsKey("data"), "转发回来的应是完整包装体");

        // 未注册实例 → 404 业务码
        ResponseEntity<Map> unknown = exchange(HttpMethod.GET, "/api/ext/instances/nope/points", null);
        assertEquals(HttpStatus.OK, unknown.getStatusCode(), "业务错误走包装体而非 HTTP 错误码");
        assertEquals(404, unknown.getBody().get("code"));
    }

    @Test
    @DisplayName("非法实例名 / 非法 baseUrl → 400 业务码")
    @SuppressWarnings("unchecked")
    void registerRejectsBadInput() {
        ResponseEntity<Map> badName = exchange(HttpMethod.POST, "/api/ext/instances",
                Map.of("name", "bad name!", "baseUrl", "http://localhost:1"));
        assertEquals(400, badName.getBody().get("code"));

        ResponseEntity<Map> badUrl = exchange(HttpMethod.POST, "/api/ext/instances",
                Map.of("name", "ok-name", "baseUrl", "ftp://x"));
        assertEquals(400, badUrl.getBody().get("code"));
    }
}
