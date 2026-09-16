package com.zifang.z.ext.core.registry;

import com.zifang.z.ext.annotation.ExtImplType;

/**
 * 扩展实现定义
 */
public class ExtImplDefinition {

    /**
     * 对应的扩展点标识
     */
    private String point;

    /**
     * 实现名称
     */
    private String name;

    /**
     * 实现类
     */
    private Class<?> implClass;

    /**
     * 实现实例
     */
    private Object instance;

    /**
     * 实现类型
     */
    private ExtImplType type;

    /**
     * 描述
     */
    private String description;

    /**
     * 执行顺序
     */
    private int order;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 路由条件（SpEL表达式）
     */
    private String condition;

    /**
     * 权重
     */
    private int weight;

    /**
     * RPC地址（如果是外部实现）
     */
    private String rpcAddress;

    /**
     * RPC端口
     */
    private int rpcPort;

    public ExtImplDefinition() {
    }

    public ExtImplDefinition(String point, String name, Class<?> implClass) {
        this.point = point;
        this.name = name;
        this.implClass = implClass;
    }

    // Getters and Setters

    public String getPoint() {
        return point;
    }

    public void setPoint(String point) {
        this.point = point;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getImplClass() {
        return implClass;
    }

    public void setImplClass(Class<?> implClass) {
        this.implClass = implClass;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public ExtImplType getType() {
        return type;
    }

    public void setType(ExtImplType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getRpcAddress() {
        return rpcAddress;
    }

    public void setRpcAddress(String rpcAddress) {
        this.rpcAddress = rpcAddress;
    }

    public int getRpcPort() {
        return rpcPort;
    }

    public void setRpcPort(int rpcPort) {
        this.rpcPort = rpcPort;
    }
}