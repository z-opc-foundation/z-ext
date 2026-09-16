package com.zifang.z.ext.core.registry;

import com.zifang.z.ext.annotation.ExtType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 扩展点定义
 */
public class ExtPointDefinition {

    /**
     * 该扩展点的所有实现
     */
    private final List<ExtImplDefinition> implementations = new ArrayList<>();
    /**
     * 扩展点标识
     */
    private String point;
    /**
     * 接口类
     */
    private Class<?> interfaceClass;
    /**
     * 扩展点类型
     */
    private ExtType type;
    /**
     * 描述
     */
    private String description;
    /**
     * 版本
     */
    private String version;
    /**
     * 执行顺序
     */
    private int order;

    public ExtPointDefinition() {
    }

    public ExtPointDefinition(String point, Class<?> interfaceClass) {
        this.point = point;
        this.interfaceClass = interfaceClass;
    }

    // Getters and Setters

    public String getPoint() {
        return point;
    }

    public void setPoint(String point) {
        this.point = point;
    }

    public Class<?> getInterfaceClass() {
        return interfaceClass;
    }

    public void setInterfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public ExtType getType() {
        return type;
    }

    public void setType(ExtType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<ExtImplDefinition> getImplementations() {
        return implementations;
    }

    public void addImplementation(ExtImplDefinition impl) {
        this.implementations.add(impl);
    }

    /**
     * 获取启用的实现列表
     */
    public List<ExtImplDefinition> getEnabledImplementations() {
        return implementations.stream()
                .filter(ExtImplDefinition::isEnabled)
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .collect(Collectors.toList());
    }
}