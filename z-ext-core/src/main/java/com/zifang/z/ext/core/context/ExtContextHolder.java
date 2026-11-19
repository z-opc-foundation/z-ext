package com.zifang.z.ext.core.context;

import java.util.HashMap;
import java.util.Map;

/**
 * 调用线程上的扩展点上下文。
 * <p>
 * 路由条件要按租户/用户/灰度标签判定时，这些值来自业务请求而不是扩展点调用参数。
 * 业务侧（拦截器、过滤器、领域服务）把值放进这里，
 * {@code ExtInvocationHandler} 在路由前把它们并进 {@code ExtRouterContext}，
 * 于是条件表达式可以写成 {@code #tenantId == 'vip'}。
 * <p>
 * 线程隔离：值只在本线程可见，线程池复用前需自行清理（{@link #clear()}）。
 */
public final class ExtContextHolder {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    private ExtContextHolder() {
    }

    public static void set(String key, Object value) {
        if (key == null) {
            return;
        }
        Map<String, Object> attributes = CONTEXT.get();
        if (attributes == null) {
            attributes = new HashMap<>();
            CONTEXT.set(attributes);
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public static Object get(String key) {
        Map<String, Object> attributes = CONTEXT.get();
        return attributes == null ? null : attributes.get(key);
    }

    /**
     * 当前线程的全部上下文属性；无属性时返回空 Map。
     */
    public static Map<String, Object> getAll() {
        Map<String, Object> attributes = CONTEXT.get();
        return attributes == null ? new HashMap<>() : new HashMap<>(attributes);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 在指定上下文下执行一段逻辑，执行完（含异常）自动清理。
     */
    public static void runWith(Map<String, Object> attributes, Runnable action) {
        Map<String, Object> previous = CONTEXT.get();
        if (attributes == null || attributes.isEmpty()) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(new HashMap<>(attributes));
        }
        try {
            action.run();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
