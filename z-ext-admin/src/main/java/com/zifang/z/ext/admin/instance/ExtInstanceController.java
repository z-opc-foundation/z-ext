package com.zifang.z.ext.admin.instance;

import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务实例代理：把控制台上的操作转发到目标实例的嵌入式控制面（{@code /api/ext/**}）。
 * <p>
 * 控制台自身不持有实例的注册表 —— 转发时透传调用方的 Authorization，
 * 由目标实例自己的令牌/鉴权体系判定；本控制台对实例是纯转发面。
 */
@RestController
@RequestMapping("/api/ext/instances")
public class ExtInstanceController {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final ExtInstanceRegistry registry;
    private final RestClient restClient;

    public ExtInstanceController(ExtInstanceRegistry registry) {
        this.registry = registry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping
    @BizApi(summary = "listInstances", description = "", tags = { "z-ext" })
    public Map<String, Object> listInstances() {
        return ok(registry.list());
    }

    @PostMapping
    @BizApi(summary = "register", description = "", tags = { "z-ext" })
    public Map<String, Object> register(@RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, String> body) {
        try {
            registry.register(body == null ? null : body.get("name"),
                    body == null ? null : body.get("baseUrl"));
            return ok(registry.list());
        } catch (IllegalArgumentException e) {
            return fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{name}")
    @BizApi(summary = "unregister", description = "", tags = { "z-ext" })
    public Map<String, Object> unregister(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name) {
        registry.unregister(name);
        return ok(registry.list());
    }

    @GetMapping("/{name}/points")
    @BizApi(summary = "points", description = "", tags = { "z-ext" })
    public Map<String, Object> points(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name,
                                      @RequestHeader(value = "Authorization", required = false)
                                      @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.GET, "/api/ext/points", null, auth);
    }

    @GetMapping("/{name}/implementations")
    @BizApi(summary = "implementations", description = "", tags = { "z-ext" })
    public Map<String, Object> implementations(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name,
                                               @RequestHeader(value = "Authorization", required = false)
                                               @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.GET, "/api/ext/implementations", null, auth);
    }

    @PostMapping("/{name}/points/{point}/activate")
    @BizApi(summary = "activate", description = "", tags = { "z-ext" })
    public Map<String, Object> activate(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name, @PathVariable @BizParam(name = "point", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String point,
                                        @RequestBody(required = false)
                                        @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body,
                                        @RequestHeader(value = "Authorization", required = false)
                                        @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.POST, "/api/ext/points/" + point + "/activate", body, auth);
    }

    @DeleteMapping("/{name}/points/{point}/activate")
    @BizApi(summary = "deactivate", description = "", tags = { "z-ext" })
    public Map<String, Object> deactivate(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name, @PathVariable @BizParam(name = "point", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String point,
                                          @RequestHeader(value = "Authorization", required = false)
                                          @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.DELETE, "/api/ext/points/" + point + "/activate", null, auth);
    }

    @PostMapping("/{name}/implementations")
    @BizApi(summary = "registerImpl", description = "", tags = { "z-ext" })
    public Map<String, Object> registerImpl(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name,
                                            @RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body,
                                            @RequestHeader(value = "Authorization", required = false)
                                            @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.POST, "/api/ext/implementations", body, auth);
    }

    /**
     * 给目标实例注入 mock 实现（「零代码接管」的操作口）。
     */
    @PostMapping("/{name}/implementations/mock")
    @BizApi(summary = "给目标实例注入 mock 实现（「零代码接管」的操作口）", description = "给目标实例注入 mock 实现（「零代码接管」的操作口）。", tags = { "z-ext" })
    public Map<String, Object> injectMock(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name,
                                          @RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body,
                                          @RequestHeader(value = "Authorization", required = false)
                                          @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.POST, "/api/ext/implementations/mock", body, auth);
    }

    /**
     * 把目标实例里的既有 Bean 现场适配成扩展点实现。
     */
    @PostMapping("/{name}/adapters")
    @BizApi(summary = "把目标实例里的既有 Bean 现场适配成扩展点实现", description = "把目标实例里的既有 Bean 现场适配成扩展点实现。", tags = { "z-ext" })
    public Map<String, Object> adapt(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name,
                                      @RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body,
                                      @RequestHeader(value = "Authorization", required = false)
                                      @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.POST, "/api/ext/adapters", body, auth);
    }

    /**
     * 列出目标实例里可适配的 Bean。
     */
    @GetMapping("/{name}/adapters")
    @BizApi(summary = "列出目标实例里可适配的 Bean", description = "列出目标实例里可适配的 Bean。", tags = { "z-ext" })
    public Map<String, Object> adapters(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name,
                                        @RequestHeader(value = "Authorization", required = false)
                                        @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.GET, "/api/ext/adapters", null, auth);
    }

    @PutMapping("/{name}/implementations/{id}")
    @BizApi(summary = "updateImpl", description = "", tags = { "z-ext" })
    public Map<String, Object> updateImpl(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name, @PathVariable @BizParam(name = "id", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String id,
                                          @RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body,
                                          @RequestHeader(value = "Authorization", required = false)
                                          @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.PUT, "/api/ext/implementations/" + id, body, auth);
    }

    @DeleteMapping("/{name}/implementations/{id}")
    @BizApi(summary = "deleteImpl", description = "", tags = { "z-ext" })
    public Map<String, Object> deleteImpl(@PathVariable @BizParam(name = "name", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String name, @PathVariable @BizParam(name = "id", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.PATH) String id,
                                          @RequestHeader(value = "Authorization", required = false)
                                          @BizParam(name = "Authorization", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.HEADER) String auth) {
        return forward(name, HttpMethod.DELETE, "/api/ext/implementations/" + id, null, auth);
    }

    private Map<String, Object> forward(String instance, HttpMethod method, String path,
                                        Object body, String authorization) {
        String baseUrl;
        try {
            baseUrl = registry.baseUrlOf(instance);
        } catch (IllegalArgumentException e) {
            return fail(404, e.getMessage());
        }
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(baseUrl + path)
                    .headers(h -> {
                        if (StringUtils.hasText(authorization)) {
                            h.set("Authorization", authorization);
                        }
                    });
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            ResponseEntity<Map> resp = request.retrieve().toEntity(Map.class);
            // 实例回来的已是 {code,data,message} 包装体 → 原样透传（业务码语义保留）；
            // 裸数据才包一层。否则控制台自己会变成双层包装，前端解不到。
            Object inner = resp.getBody();
            if (inner instanceof Map<?, ?> m && m.containsKey("code")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> wrapper = (Map<String, Object>) m;
                return wrapper;
            }
            return ok(inner);
        } catch (Exception e) {
            return fail(502, "实例 " + instance + " 转发失败：" + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("data", data);
        body.put("message", "ok");
        return body;
    }

    private static Map<String, Object> fail(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("data", "");
        body.put("message", message);
        return body;
    }
}









