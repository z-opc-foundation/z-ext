package com.zifang.z.ext.adapter;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

/**
 * 既有实现适配为扩展点的注册器。
 * <p>
 * 与 {@code @ExtImpl} 的差别：{@code @ExtImpl} 描述的是「我为这个扩展点写了一个实现」，
 * 这里描述的是「这个已经存在的 Bean 就是这个扩展点的一个实现」——
 * 业务代码一行不改，只是把现成实现挂上扩展点的名册。
 */
public final class ExtAdapterRegistrar {

    private ExtAdapterRegistrar() {
    }

    /**
     * 把一个 Bean 注册为扩展点实现（实现名取类名首字母小写）。
     *
     * @param point 扩展点标识
     * @param bean  既有 Bean 实例
     * @return 是否注册成功
     */
    public static boolean register(String point, Object bean) {
        return register(point, bean, defaultNameOf(bean));
    }

    /**
     * 把一个 Bean 注册为扩展点实现。
     *
     * @param point 扩展点标识
     * @param bean  既有 Bean 实例
     * @param name  实现名
     * @return 是否注册成功
     */
    public static boolean register(String point, Object bean, String name) {
        if (point == null || point.trim().isEmpty()) {
            throw new IllegalArgumentException("扩展点标识不能为空");
        }
        if (bean == null) {
            throw new IllegalArgumentException("被适配的 Bean 不能为空");
        }
        ExtPointDefinition pointDef = ExtRegistry.getPoint(point);
        if (pointDef == null) {
            throw new IllegalArgumentException("扩展点不存在: " + point);
        }
        Class<?> interfaceClass = pointDef.getInterfaceClass();
        if (interfaceClass != null && !interfaceClass.isInstance(bean)) {
            throw new IllegalArgumentException(
                    "Bean " + bean.getClass().getName() + " 未实现扩展点接口 "
                            + interfaceClass.getName() + "（point=" + point + "）");
        }
        String implName = (name == null || name.trim().isEmpty()) ? defaultNameOf(bean) : name.trim();
        for (ExtImplDefinition existing : ExtRegistry.getImplementations(point)) {
            if (implName.equals(existing.getName())) {
                if (existing.getImplClass() != null && existing.getImplClass().equals(bean.getClass())) {
                    return false;
                }
                throw new IllegalArgumentException(
                        "扩展实现名冲突: point=" + point + ", name=" + implName);
            }
        }
        ExtImplDefinition definition = new ExtImplDefinition(point, implName, bean.getClass());
        definition.setInstance(bean);
        definition.setType(ExtImplType.PLATFORM);
        definition.setDescription("由既有 Bean 适配而来: " + bean.getClass().getSimpleName());
        definition.setEnabled(true);
        ExtRegistry.registerImpl(definition);
        return true;
    }

    /**
     * 默认实现名：类名首字母小写。
     */
    public static String defaultNameOf(Object bean) {
        return defaultNameOf(bean.getClass());
    }

    /**
     * 默认实现名：类名首字母小写。
     */
    public static String defaultNameOf(Class<?> type) {
        String simple = type.getSimpleName();
        if (simple == null || simple.isEmpty()) {
            return "adapter";
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}
