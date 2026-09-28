package com.zifang.z.ext.core.registry;

import com.zifang.z.ext.annotation.ExtImplType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 扩展注册中心
 * 管理所有的扩展点和扩展实现
 */
public class ExtRegistry {

    /**
     * 扩展点定义缓存 key: point标识
     */
    private static final Map<String, ExtPointDefinition> EXT_POINT_REGISTRY = new ConcurrentHashMap<>();

    /**
     * 扩展实现缓存 key: point标识
     */
    private static final Map<String, List<ExtImplDefinition>> EXT_IMPL_REGISTRY = new ConcurrentHashMap<>();

    /**
     * 当前激活的实现 key: point标识
     */
    private static final Map<String, String> ACTIVE_IMPL = new ConcurrentHashMap<>();

    /**
     * 监听器列表
     */
    private static final List<ExtChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

    // ==================== 扩展点注册 ====================

    /**
     * 注册扩展点
     */
    public static void registerPoint(ExtPointDefinition definition) {
        EXT_POINT_REGISTRY.put(definition.getPoint(), definition);
    }

    /**
     * 获取扩展点定义
     */
    public static ExtPointDefinition getPoint(String point) {
        return EXT_POINT_REGISTRY.get(point);
    }

    /**
     * 按扩展点接口类查找扩展点定义
     * <p>
     * 扩展点标识（point key）是业务侧的自由文本，调用方常常只握有接口 Class 本身，
     * 这里按 interfaceClass 反查，找不到返回 null。
     */
    public static ExtPointDefinition getPointByInterface(Class<?> interfaceClass) {
        if (interfaceClass == null) {
            return null;
        }
        for (ExtPointDefinition definition : EXT_POINT_REGISTRY.values()) {
            if (interfaceClass.equals(definition.getInterfaceClass())) {
                return definition;
            }
        }
        return null;
    }

    /**
     * 获取所有扩展点
     */
    public static Map<String, ExtPointDefinition> getAllPoints() {
        return new HashMap<>(EXT_POINT_REGISTRY);
    }

    // ==================== 扩展实现注册 ====================

    /**
     * 注册扩展实现
     */
    public static void registerImpl(ExtImplDefinition definition) {
        EXT_IMPL_REGISTRY
                .computeIfAbsent(definition.getPoint(), k -> new CopyOnWriteArrayList<>())
                .add(definition);

        // 更新扩展点定义中的实现列表
        ExtPointDefinition pointDef = EXT_POINT_REGISTRY.get(definition.getPoint());
        if (pointDef != null) {
            pointDef.addImplementation(definition);
        }

        // 通知监听器
        notifyListeners(listener -> listener.onImplRegistered(definition));
    }

    /**
     * 获取扩展点的所有实现
     * <p>
     * 扩展点没有任何实现时返回空列表，而不是 null（调用方不必为「未注册的 point」额外兜底）。
     */
    public static List<ExtImplDefinition> getImplementations(String point) {
        List<ExtImplDefinition> impls = EXT_IMPL_REGISTRY.get(point);
        return impls == null ? new ArrayList<ExtImplDefinition>() : new ArrayList<>(impls);
    }

    /**
     * 获取扩展点的启用实现
     */
    public static List<ExtImplDefinition> getEnabledImplementations(String point) {
        List<ExtImplDefinition> all = EXT_IMPL_REGISTRY.getOrDefault(point, new ArrayList<>());
        return all.stream()
                .filter(ExtImplDefinition::isEnabled)
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .collect(Collectors.toList());
    }

    // ==================== 激活实现管理 ====================

    /**
     * 设置当前激活的实现
     */
    public static void setActiveImpl(String point, String implName) {
        String oldImpl = ACTIVE_IMPL.put(point, implName);
        notifyListeners(listener -> listener.onActiveImplChanged(point, oldImpl, implName));
    }

    /**
     * 获取当前激活的实现名称
     */
    public static String getActiveImpl(String point) {
        return ACTIVE_IMPL.get(point);
    }

    /**
     * 获取当前激活的实现
     */
    public static ExtImplDefinition getActiveImplementation(String point) {
        String implName = ACTIVE_IMPL.get(point);
        if (implName == null) {
            return null;
        }
        List<ExtImplDefinition> impls = EXT_IMPL_REGISTRY.get(point);
        if (impls == null) {
            return null;
        }
        return impls.stream()
                .filter(i -> i.getName().equals(implName))
                .findFirst()
                .orElse(null);
    }

    // ==================== 查找实现 ====================

    /**
     * 根据类型查找实现
     */
    public static ExtImplDefinition findImplByType(String point, ExtImplType type) {
        List<ExtImplDefinition> impls = getEnabledImplementations(point);
        return impls.stream()
                .filter(i -> i.getType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取平台默认实现
     */
    public static ExtImplDefinition getPlatformImpl(String point) {
        return findImplByType(point, ExtImplType.PLATFORM);
    }

    /**
     * 获取外部实现
     */
    public static ExtImplDefinition getExternalImpl(String point) {
        return findImplByType(point, ExtImplType.EXTERNAL);
    }

    /**
     * 获取自定义实现
     */
    public static ExtImplDefinition getCustomImpl(String point) {
        return findImplByType(point, ExtImplType.CUSTOM);
    }

    // ==================== 监听器 ====================

    /**
     * 添加监听器
     */
    public static void addListener(ExtChangeListener listener) {
        LISTENERS.add(listener);
    }

    /**
     * 移除监听器
     */
    public static void removeListener(ExtChangeListener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(Consumer<ExtChangeListener> action) {
        for (ExtChangeListener listener : LISTENERS) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                // 忽略单个监听器的异常
            }
        }
    }

    // ==================== 清理 ====================

    /**
     * 清空注册表（主要用于测试）
     */
    public static void clear() {
        EXT_POINT_REGISTRY.clear();
        EXT_IMPL_REGISTRY.clear();
        ACTIVE_IMPL.clear();
    }

    /**
     * 监听器接口
     */
    public interface ExtChangeListener {
        /**
         * 当实现被注册时
         */
        default void onImplRegistered(ExtImplDefinition definition) {
        }

        /**
         * 当激活实现变更时
         */
        default void onActiveImplChanged(String point, String oldImpl, String newImpl) {
        }
    }
}