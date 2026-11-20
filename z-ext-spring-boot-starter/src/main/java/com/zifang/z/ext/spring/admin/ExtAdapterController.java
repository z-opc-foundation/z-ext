package com.zifang.z.ext.spring.admin;

import com.zifang.z.ext.adapter.ExtAdapter;
import com.zifang.z.ext.adapter.ExtAdapterRegistrar;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制面：把宿主里既有的 Bean 现场适配成扩展点实现。
 * <p>
 * 这是「复用现成实现」那一档的操作口：控制面指一个已有的 Bean 名，
 * 本件把它挂到某个扩展点的名册上，此后控制面就能在它与其他实现之间切换。
 */
@RestController
@RequestMapping("${z.ext.admin.base-path:/api/ext}")
public class ExtAdapterController {

    /** 宿主容器。 */
    private final ApplicationContext applicationContext;

    public ExtAdapterController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 适配一个既有 Bean。
     * <p>
     * 请求体：{@code {point, bean, name}}；{@code bean} 是容器里的 Bean 名，
     * {@code name} 是适配后的实现名（留空取类名首字母小写）。
     */
    @PostMapping("/adapters")
    @BizApi(summary = "适配一个既有 Bean", description = "适配一个既有 Bean。 请求体：{point, bean, name}；bean 是容器里的 Bean 名， name 是适配后的实现名（留空取类名首字母小写）。", tags = { "z-ext" })
    public Map<String, Object> adapt(@RequestBody @BizParam(name = "body", type = BizField.FieldType.JSON, description = "", position = BizParam.Position.BODY) Map<String, Object> body) {
        String point = str(body, "point");
        String beanName = str(body, "bean");
        if (point == null || point.isBlank() || beanName == null || beanName.isBlank()) {
            return fail(400, "point / bean 不能为空");
        }
        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (NoSuchBeanDefinitionException e) {
            return fail(404, "宿主里没有这个 Bean: " + beanName);
        }
        try {
            boolean registered = ExtAdapterRegistrar.register(point, bean, str(body, "name"));
            return ok(registered ? "adapted" : "already-registered");
        } catch (IllegalArgumentException e) {
            return fail(400, e.getMessage());
        }
    }

    /**
     * 列出宿主里所有带 {@link ExtAdapter} 的 Bean（还没被适配的也能看到，方便控制台指认）。
     */
    @GetMapping("/adapters")
    @BizApi(summary = "列出宿主里所有带 ExtAdapter 的 Bean（还没被适配的也能看到，方便控制台指认）", description = "列出宿主里所有带 ExtAdapter 的 Bean（还没被适配的也能看到，方便控制台指认）。", tags = { "z-ext" })
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String name : applicationContext.getBeanDefinitionNames()) {
            try {
                Class<?> type = applicationContext.getType(name);
                if (type == null) {
                    continue;
                }
                ExtAdapter annotation = AnnotatedElementUtils.findMergedAnnotation(type, ExtAdapter.class);
                if (annotation == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bean", name);
                item.put("point", annotation.point());
                item.put("name", annotation.name());
                item.put("registered", isRegistered(annotation.point(),
                        annotation.name().isBlank()
                                ? ExtAdapterRegistrar.defaultNameOf(type)
                                : annotation.name()));
                items.add(item);
            } catch (Exception e) {
                // 取某个 Bean 的类型失败（初始化异常等）不影响其余条目
            }
        }
        return ok(items);
    }

    /**
     * 该点下是否已有同名实现。
     */
    private boolean isRegistered(String point, String implName) {
        if (point == null || implName == null) {
            return false;
        }
        return ExtRegistry.getImplementations(point).stream()
                .anyMatch(impl -> implName.equals(impl.getName()));
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









