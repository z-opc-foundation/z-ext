package com.zifang.z.ext.core.proxy;

import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

import java.lang.reflect.Proxy;

/**
 * 扩展代理工厂
 * 创建扩展点的动态代理
 */
public class ExtProxyFactory {

    /**
     * 创建代理对象
     *
     * @param interfaceClass 扩展点接口类
     * @param handler        调用处理器
     * @param <T>            接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceClass, ExtInvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler
        );
    }

    /**
     * 根据扩展点定义创建代理
     *
     * @param pointDef 扩展点定义
     * @param <T>      接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(ExtPointDefinition pointDef) {
        Class<T> interfaceClass = (Class<T>) pointDef.getInterfaceClass();
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);
        return createProxy(interfaceClass, handler);
    }

    /**
     * 根据扩展点标识创建代理
     *
     * @param point 扩展点标识
     * @param <T>   接口类型
     * @return 代理对象
     */
    public static <T> T createProxy(String point) {
        ExtPointDefinition pointDef = ExtRegistry.getPoint(point);
        if (pointDef == null) {
            throw new IllegalArgumentException("Extension point not found: " + point);
        }
        return createProxy(pointDef);
    }
}