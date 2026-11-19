package com.zifang.z.ext.spring.admin;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.admin.ExtAdminProvider;
import com.zifang.z.ext.core.admin.ExtImplView;
import com.zifang.z.ext.core.admin.ExtPointView;
import com.zifang.z.ext.core.admin.ExtSnapshot;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 嵌入式扩展点控制面。
 * <p>
 * 响应统一 {@code {code, data, message}} 包装（z-opc 全家桶口径，前端基座按此解包）。
 * 本控制器工作在宿主进程内，注册表操作直接落到 {@link ExtRegistry}；
 * 独立控制台对多实例的聚合走它暴露的同一组 HTTP 契约。
 * <p>
 * 安全口径：本件不做鉴权，宿主应用按自身口径兜底（网关层 / 拦截器）。
 */
@RestController
@RequestMapping("${z.ext.admin.base-path:/api/ext}")
public class ExtAdminController {

    private final ExtAdminProvider provider;

    public ExtAdminController(ExtAdminProvider provider) {
        this.provider = provider;
    }

    // ==================== 查询 ====================

    @GetMapping("/points")
    public Map<String, Object> listPoints() {
        return ok(snapshot().getPoints());
    }

    @GetMapping("/points/{point}")
    public Map<String, Object> getPoint(@PathVariable String point) {
        for (ExtPointView view : snapshot().getPoints()) {
            if (point.equals(view.getPoint())) {
                return ok(view);
            }
        }
        return fail(404, "扩展点不存在: " + point);
    }

    @GetMapping("/implementations")
    public Map<String, Object> listImplementations() {
        List<ExtImplView> impls = new ArrayList<>();
        for (ExtPointView pointView : snapshot().getPoints()) {
            impls.addAll(pointView.getImplementations());
        }
        return ok(impls);
    }

    // ==================== 扩展点管理 ====================

    @PostMapping("/points")
    public Map<String, Object> registerPoint(@RequestBody Map<String, Object> body) {
        String point = str(body, "point");
        if (point == null || point.isBlank()) {
            return fail(400, "point 不能为空");
        }
        ExtPointDefinition definition = new ExtPointDefinition();
        definition.setPoint(point);
        try {
            definition.setInterfaceClass(classOf(str(body, "interfaceClass")));
        } catch (ClassNotFoundException e) {
            return fail(400, "接口类不存在: " + str(body, "interfaceClass"));
        }
        definition.setType(typeOf(str(body, "type")));
        definition.setDescription(str(body, "description"));
        definition.setVersion(str(body, "version"));
        ExtRegistry.registerPoint(definition);
        return ok(point);
    }

    @PutMapping("/points/{point}")
    public Map<String, Object> updatePoint(@PathVariable String point,
                                           @RequestBody Map<String, Object> body) {
        ExtPointDefinition definition = ExtRegistry.getPoint(point);
        if (definition == null) {
            return fail(404, "扩展点不存在: " + point);
        }
        if (body.containsKey("description")) {
            definition.setDescription(str(body, "description"));
        }
        if (body.containsKey("version")) {
            definition.setVersion(str(body, "version"));
        }
        if (body.containsKey("type")) {
            definition.setType(typeOf(str(body, "type")));
        }
        return ok(point);
    }

    @DeleteMapping("/points/{point}")
    public Map<String, Object> deletePoint(@PathVariable String point) {
        if (!ExtRegistry.unregisterPoint(point)) {
            return fail(404, "扩展点不存在: " + point);
        }
        return ok(point);
    }

    // ==================== 激活切换 ====================

    @PostMapping("/points/{point}/activate")
    public Map<String, Object> activate(@PathVariable String point,
                                        @RequestBody Map<String, Object> body) {
        String implName = str(body, "implName");
        if (implName == null) {
            return fail(400, "implName 不能为空");
        }
        if (!provider.activate(point, implName)) {
            return fail(404, "实现不存在: " + point + " / " + implName);
        }
        return ok(implName);
    }

    @DeleteMapping("/points/{point}/activate")
    public Map<String, Object> deactivate(@PathVariable String point) {
        if (!provider.deactivate(point)) {
            return fail(404, "扩展点不存在: " + point);
        }
        return ok(point);
    }

    // ==================== 实现管理 ====================

    @PostMapping("/implementations")
    public Map<String, Object> registerImplementation(@RequestBody Map<String, Object> body) {
        String point = str(body, "point");
        String name = str(body, "name");
        if (point == null || point.isBlank() || name == null || name.isBlank()) {
            return fail(400, "point / name 不能为空");
        }
        ExtImplDefinition definition = new ExtImplDefinition();
        definition.setPoint(point);
        definition.setName(name);
        try {
            definition.setImplClass(classOf(str(body, "implClass")));
        } catch (ClassNotFoundException e) {
            return fail(400, "实现类不存在: " + str(body, "implClass"));
        }
        definition.setType(implTypeOf(str(body, "type")));
        definition.setDescription(str(body, "description"));
        definition.setCondition(str(body, "condition"));
        definition.setOrder(intOf(body, "order"));
        definition.setWeight(intOf(body, "weight"));
        definition.setEnabled(boolOf(body, "enabled", true));
        definition.setRpcAddress(str(body, "rpcAddress"));
        definition.setRpcPort(intOf(body, "rpcPort"));
        try {
            ExtRegistry.registerImpl(definition);
        } catch (IllegalArgumentException e) {
            return fail(409, e.getMessage());
        }
        return ok(point + ":" + name);
    }

    @PutMapping("/implementations/{id}")
    public Map<String, Object> updateImplementation(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        String point = str(body, "point");
        String name = str(body, "name");
        if (point == null || name == null) {
            int split = id == null ? -1 : id.lastIndexOf(':');
            if (split < 0) {
                return fail(400, "无法从 id 解析 point/name: " + id);
            }
            point = id.substring(0, split);
            name = id.substring(split + 1);
        }
        ExtImplDefinition definition = findImpl(point, name);
        if (definition == null) {
            return fail(404, "实现不存在: " + point + " / " + name);
        }
        if (body.containsKey("enabled")) {
            definition.setEnabled(boolOf(body, "enabled", definition.isEnabled()));
        }
        if (body.containsKey("weight")) {
            definition.setWeight(intOf(body, "weight"));
        }
        if (body.containsKey("order")) {
            definition.setOrder(intOf(body, "order"));
        }
        if (body.containsKey("condition")) {
            definition.setCondition(str(body, "condition"));
        }
        if (body.containsKey("description")) {
            definition.setDescription(str(body, "description"));
        }
        if (body.containsKey("rpcAddress")) {
            definition.setRpcAddress(str(body, "rpcAddress"));
        }
        if (body.containsKey("rpcPort")) {
            definition.setRpcPort(intOf(body, "rpcPort"));
        }
        return ok(id);
    }

    @DeleteMapping("/implementations/{id}")
    public Map<String, Object> deleteImplementation(@PathVariable String id) {
        int split = id.lastIndexOf(':');
        if (split < 0 || !ExtRegistry.unregisterImpl(id.substring(0, split), id.substring(split + 1))) {
            return fail(404, "实现不存在: " + id);
        }
        return ok(id);
    }

    // ==================== 内部 ====================

    private ExtSnapshot snapshot() {
        return provider.snapshot();
    }

    private ExtImplDefinition findImpl(String point, String name) {
        for (ExtImplDefinition definition : ExtRegistry.getImplementations(point)) {
            if (name.equals(definition.getName())) {
                return definition;
            }
        }
        return null;
    }

    private static Class<?> classOf(String className) throws ClassNotFoundException {
        return className == null || className.isBlank() ? null : Class.forName(className);
    }

    private static ExtType typeOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ExtType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ExtImplType implTypeOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ExtImplType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int intOf(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean boolOf(Map<String, Object> body, String key, boolean fallback) {
        Object value = body.get(key);
        return value instanceof Boolean b ? b : fallback;
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
        body.put("data", null);
        body.put("message", message);
        return body;
    }
}
