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
     * <p>
     * 入参既可以是扩展点标识（如 {@code "order.service"}），也可以是扩展点接口的类型描述符
     * （{@code Class.toString()} 形态，如 {@code "interface com.zifang.z.ext.test.OrderService"}），
     * 后者按接口类反查已注册的扩展点。两者都查不到时抛 {@link IllegalArgumentException}。
     *
     * @param point 扩展点标识，或扩展点接口的类型描述符
     * @param <T>   接口类型
     * @return 代理对象
     */
    public static <T> T createProxy(String point) {
        ExtPointDefinition pointDef = resolvePoint(point);
        if (pointDef == null) {
            throw new IllegalArgumentException("Extension point not found: " + point);
        }
        return createProxy(pointDef);
    }

    /**
     * 解析扩展点定义：先按 point 标识精确查，查不到再退化为按接口类反查
     */
    private static ExtPointDefinition resolvePoint(String point) {
        if (point == null) {
            return null;
        }
        ExtPointDefinition pointDef = ExtRegistry.getPoint(point);
        if (pointDef != null) {
            return pointDef;
        }
        String className = interfaceNameOf(point);
        try {
            Class<?> interfaceClass = Class.forName(className, false, ExtProxyFactory.class.getClassLoader());
            return ExtRegistry.getPointByInterface(interfaceClass);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 从类型描述符取出接口全限定名：{@code "interface com.x.Y"} / {@code "class com.x.Y"} 去掉前缀，
     * 其余形态原样返回（允许直接传接口全限定名）。
     */
    private static String interfaceNameOf(String point) {
        if (point.startsWith("interface ") || point.startsWith("class ")) {
            return point.substring(point.indexOf(' ') + 1);
        }
        return point;
    }
}