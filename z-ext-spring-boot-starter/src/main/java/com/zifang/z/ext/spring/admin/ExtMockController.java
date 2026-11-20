package com.zifang.z.ext.spring.admin;

import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.mock.MockExtImplFactory;
import com.zifang.z.ext.mock.MockSpec;
import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制面：注入 mock 实现 —— 「零代码实现」那一档的操作口。
 * <p>
 * 控制面下发一段行为描述，这里凭空造出一个实现该扩展点接口的代理并挂进注册表，
 * 业务侧一行 Java 都不用写就能被接管（返回假数据、恒拒绝、故障注入）。
 * <p>
 * 响应统一 {@code {code, data, message}} 包装，与其余控制面接口同口径。
 */
@RestController
@RequestMapping("${z.ext.admin.base-path:/api/ext}")
public class ExtMockController {

    /**
     * 注入 mock 实现。
     * <p>
     * 请求体：{@code {point, name, behavior, result, errorMessage, delayMs}}
     * —— {@code behavior} 取 {@code RETURN} / {@code THROW} / {@code DENY}。
     */
    @PostMapping("/implementations/mock")
    @BizApi(summary = "注入 mock 实现", description = "注入 mock 实现。 请求体：{point, name, behavior, result, errorMessage, delayMs} —— behavior 取 RETURN / THROW / DENY。", tags = { "z-ext" })
    public Map<String, Object> injectMock(@RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body) {
        String point = str(body, "point");
        String name = str(body, "name");
        if (point == null || point.isBlank() || name == null || name.isBlank()) {
            return fail(400, "point / name 不能为空");
        }
        ExtPointDefinition pointDef = ExtRegistry.getPoint(point);
        if (pointDef == null) {
            return fail(404, "扩展点不存在: " + point);
        }
        MockSpec spec = new MockSpec(name.trim());
        String behavior = str(body, "behavior");
        if (behavior != null && !behavior.isBlank()) {
            try {
                spec.setBehavior(MockSpec.Behavior.valueOf(behavior.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                return fail(400, "behavior 取值非法: " + behavior + "（可用 RETURN / THROW / DENY）");
            }
        } else {
            spec.setBehavior(MockSpec.Behavior.RETURN);
        }
        if (body.containsKey("result")) {
            spec.setResult(body.get("result"));
        }
        String errorMessage = str(body, "errorMessage");
        if (errorMessage != null) {
            spec.setErrorMessage(errorMessage);
        }
        Object delay = body.get("delayMs");
        if (delay instanceof Number) {
            spec.setDelayMs(((Number) delay).longValue());
        }
        try {
            String implName = MockExtImplFactory.register(point, pointDef.getInterfaceClass(), spec);
            return ok(implName);
        } catch (IllegalArgumentException e) {
            return fail(400, e.getMessage());
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", data);
        result.put("message", "ok");
        return result;
    }

    private static Map<String, Object> fail(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("data", null);
        result.put("message", message);
        return result;
    }
}









