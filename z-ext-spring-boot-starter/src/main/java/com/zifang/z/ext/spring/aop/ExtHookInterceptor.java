package com.zifang.z.ext.spring.aop;

import com.zifang.z.ext.aop.ExtHook;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.preset.PresetAdviceExtPoint;
import com.zifang.z.ext.spring.ExtProxyBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ExtHook} 的织入拦截器 —— 「不改调用点」那一档的执行体。
 * <p>
 * 被标注的方法在调用时自动经过扩展点：扩展点实现可以放行后改写返回值、
 * 替换结果，或直接抛错中断。
 */
public class ExtHookInterceptor implements MethodInterceptor {

    private static final Logger log = LogManager.getLogger(ExtHookInterceptor.class);

    /** 代理构建器。 */
    private final ExtProxyBuilder builder;

    /** 点标识 → 代理实例缓存（代理可复用，避免每次调用新建）。 */
    private final Map<String, Object> proxyCache = new ConcurrentHashMap<>();

    public ExtHookInterceptor(ExtProxyBuilder builder) {
        this.builder = builder;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        ExtHook hook = AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(), ExtHook.class);
        if (hook == null || !hook.enabled()) {
            return invocation.proceed();
        }
        Object advice = adviceOf(hook.point());
        if (!(advice instanceof PresetAdviceExtPoint)) {
            // 目标扩展点的接口不是环绕形状（没有 around(String, Map, ProceedAction)）
            // ⇒ 不接管，原样执行；不能因为埋点写错就让业务方法跑不起来。
            return invocation.proceed();
        }
        String event = hook.event().isEmpty() ? defaultEvent(invocation) : hook.event();
        Map<String, Object> attrs = attrsOf(invocation, event);
        return ((PresetAdviceExtPoint) advice).around(event, attrs, invocation::proceed);
    }

    /**
     * 取目标扩展点的代理（按需创建并缓存）。
     */
    private Object adviceOf(String point) {
        Object cached = proxyCache.get(point);
        if (cached != null) {
            return cached;
        }
        ExtPointDefinition pointDef = ExtRegistry.getPoint(point);
        if (pointDef == null) {
            log.warn("@ExtHook 指向的扩展点不存在，织入失效：point={}", point);
            return null;
        }
        Class<?> interfaceClass = pointDef.getInterfaceClass();
        if (interfaceClass != null && !PresetAdviceExtPoint.class.isAssignableFrom(interfaceClass)) {
            log.warn("@ExtHook 指向的扩展点接口不是环绕形状，织入失效：point={} interface={}",
                    point, interfaceClass.getName());
            return null;
        }
        Object proxy = builder.create(point);
        proxyCache.put(point, proxy);
        return proxy;
    }

    /**
     * 默认事件标识：{@code 类名.方法名}。
     */
    private static String defaultEvent(MethodInvocation invocation) {
        Object target = invocation.getThis();
        String type = target == null ? "unknown" : target.getClass().getSimpleName();
        return type + "." + invocation.getMethod().getName();
    }

    /**
     * 事件上下文。
     */
    private static Map<String, Object> attrsOf(MethodInvocation invocation, String event) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("event", event);
        attrs.put("method", invocation.getMethod().getName());
        Object target = invocation.getThis();
        attrs.put("target", target == null ? null : target.getClass().getName());
        List<Object> args = new ArrayList<>();
        Object[] arguments = invocation.getArguments();
        if (arguments != null) {
            for (Object argument : arguments) {
                args.add(argument == null ? null : argument.getClass().getSimpleName());
            }
        }
        attrs.put("argTypes", args);
        return attrs;
    }
}
