package com.zifang.z.ext.core.router;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由上下文
 * 封装路由时需要的上下文信息
 */
public class ExtRouterContext {

    /**
     * 附加属性
     */
    private final Map<String, Object> attributes = new HashMap<>();
    /**
     * 方法名
     */
    private String methodName;
    /**
     * 方法参数
     */
    private Object[] args;

    public ExtRouterContext() {
    }

    public ExtRouterContext(String methodName, Object[] args) {
        this.methodName = methodName;
        this.args = args;
    }

    // Getters and Setters

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Object getAttribute(String key, Object defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }
}