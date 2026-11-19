package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.preset.PresetEventExtPoint;
import com.zifang.z.ext.preset.PresetGateExtPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 闸门落点 —— 把预置闸门接到每个请求上。
 * <p>
 * 这是「零业务侵入」真正兑现的地方：控制面把闸门实现切走，
 * 服务立刻开始拒绝（503），切回来立刻恢复 —— <b>不用重新发布</b>。
 * <p>
 * <b>失败放行（fail-open）</b>：闸门实现自身抛错时一律放行。
 * 控制面是运维手段，不是业务依赖；它挂了不该把业务一起带下去。
 * <p>
 * <b>为什么用动态代理而不是直接实现 {@code HandlerInterceptor}</b>：
 * 后者的方法签名带 servlet 请求/响应类型，按 javax 还是 jakarta 编译会各炸一半宿主
 * （Boot 2 用 javax、Boot 3 用 jakarta）。这里改成运行时生成代理 ——
 * 代理实现的是宿主自己的 {@code HandlerInterceptor}，拿到的请求/响应对象就是宿主那一套，
 * 本件对 servlet 类型零编译期依赖，两个命名空间都能跑。
 */
public class PresetGateInterceptor {

    private static final Logger log = LogManager.getLogger(PresetGateInterceptor.class);

    /** 请求开始时间在请求属性里的键。 */
    private static final String START_ATTR = "z.ext.preset.start";

    /** 闸门点。 */
    private final PresetGateExtPoint gate;

    /** 通知点。 */
    private final PresetEventExtPoint events;

    /** 应用名（写进事件上下文，便于控制面按服务过滤）。 */
    private final String appName;

    /** 拒绝时返回的状态码。 */
    private final int denyStatus;

    /** 闸门异常时是否放行。 */
    private final boolean failOpen;

    public PresetGateInterceptor(PresetGateExtPoint gate, PresetEventExtPoint events,
                                 String appName, int denyStatus, boolean failOpen) {
        this.gate = gate;
        this.events = events;
        this.appName = appName;
        this.denyStatus = denyStatus;
        this.failOpen = failOpen;
    }

    /**
     * 包成容器能接收的 {@link HandlerInterceptor}。
     *
     * @param delegate 闸门落点逻辑
     * @return 拦截器代理
     */
    public static HandlerInterceptor asHandlerInterceptor(PresetGateInterceptor delegate) {
        return (HandlerInterceptor) Proxy.newProxyInstance(
                HandlerInterceptor.class.getClassLoader(),
                new Class<?>[]{HandlerInterceptor.class},
                new HandlerInvocation(delegate));
    }

    /**
     * 闸门判定：放行返回 true，拒绝返回 false（并已写出拒绝响应）。
     */
    boolean preHandle(Object request, Object response, Object handler) {
        ServletNativeAccess.setAttribute(request, START_ATTR, System.currentTimeMillis());
        String event = eventOf(request);
        Map<String, Object> attrs = attrsOf(request, event);
        try {
            if (gate.allow(event, attrs)) {
                return true;
            }
            String message = gate.denyMessage(event, attrs);
            writeDenied(response, message);
            publish("http.denied", attrs);
            log.warn("预置闸门拒绝请求：event={} remote={} message={}", event, attrs.get("remoteAddr"), message);
            return false;
        } catch (Exception e) {
            if (!failOpen) {
                log.error("预置闸门异常且未开失败放行，请求被拒：event={}", event, e);
                writeDenied(response, "闸门异常，请求被拒");
                return false;
            }
            log.warn("预置闸门异常，按失败放行处理：event={} err={}", event, e.toString());
            return true;
        }
    }

    /**
     * 请求结束：发完成事件（带耗时）。
     */
    void afterCompletion(Object request, Object response, Object handler, Exception ex) {
        Object start = ServletNativeAccess.getAttribute(request, START_ATTR);
        long cost = start instanceof Long ? System.currentTimeMillis() - (Long) start : -1L;
        Map<String, Object> attrs = attrsOf(request, eventOf(request));
        attrs.put("costMs", cost);
        attrs.put("error", ex == null ? null : ex.toString());
        publish("http.completed", attrs);
    }

    /**
     * 事件标识：{@code http.<METHOD>.<路径>}。
     */
    private String eventOf(Object request) {
        Object pattern = ServletNativeAccess.getAttribute(request,
                "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
        String path = pattern instanceof String ? (String) pattern : ServletNativeAccess.requestUri(request);
        return "http." + ServletNativeAccess.requestMethod(request) + "." + path;
    }

    /**
     * 事件上下文。
     */
    private Map<String, Object> attrsOf(Object request, String event) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("event", event);
        attrs.put("app", appName);
        attrs.put("method", ServletNativeAccess.requestMethod(request));
        attrs.put("path", ServletNativeAccess.requestUri(request));
        Object pattern = ServletNativeAccess.getAttribute(request,
                "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
        attrs.put("pattern", pattern instanceof String ? pattern : ServletNativeAccess.requestUri(request));
        attrs.put("remoteAddr", ServletNativeAccess.remoteAddress(request));
        attrs.put("params", ServletNativeAccess.parameterMap(request));
        return attrs;
    }

    /**
     * 写拒绝响应。
     */
    private void writeDenied(Object response, String message) {
        String body = "{\"code\":" + denyStatus + ",\"message\":"
                + (message == null ? "\"请求被预置闸门拒绝\"" : "\"" + escape(message) + "\"") + "}";
        ServletNativeAccess.write(response, denyStatus, "application/json;charset=UTF-8", body);
    }

    /**
     * 通知事件（通知点异常不影响请求）。
     */
    private void publish(String event, Map<String, Object> attrs) {
        try {
            events.publish(event, attrs);
        } catch (Exception e) {
            log.debug("预置通知点异常，忽略：event={} err={}", event, e.toString());
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 拦截器代理的调用处理：把三个回调转给落点逻辑。
     */
    private static class HandlerInvocation implements InvocationHandler {

        private final PresetGateInterceptor delegate;

        HandlerInvocation(PresetGateInterceptor delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("preHandle".equals(name)) {
                return delegate.preHandle(args[0], args[1], args.length > 2 ? args[2] : null);
            }
            if ("afterCompletion".equals(name)) {
                Object ex = args != null && args.length > 3 ? args[3] : null;
                delegate.afterCompletion(args[0], args[1], args.length > 2 ? args[2] : null,
                        ex instanceof Exception ? (Exception) ex : null);
                return null;
            }
            if ("postHandle".equals(name)) {
                return null;
            }
            if ("toString".equals(name)) {
                return "PresetGateInterceptor";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == (args == null ? null : args[0]);
            }
            return null;
        }
    }
}
