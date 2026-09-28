package com.zifang.z.ext.core.router;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 默认路由实现
 * <p>
 * 策略：
 * - SYNC: 优先使用激活的实现（显式 setActiveImpl，其次注册中心里的平台默认实现），
 * 没有可激活项时按 EXTERNAL &gt; CUSTOM &gt; PLATFORM 选择
 * - ASYNC: 同上
 * - CHAIN: 返回所有启用的实现，按 order 排序执行
 */
public class DefaultExtRouter implements ExtRouter {

    public static final String NAME = "default";

    @Override
    public List<ExtImplDefinition> route(ExtPointDefinition pointDef,
                                         List<ExtImplDefinition> impls,
                                         ExtRouterContext context) {
        if (impls == null || impls.isEmpty()) {
            return Collections.emptyList();
        }

        // CHAIN 类型返回所有实现
        if (pointDef.getType() == ExtType.CHAIN) {
            return impls;
        }

        // SYNC/ASYNC 类型只返回一个实现
        ExtImplDefinition selected = selectOne(pointDef, impls, context);
        return selected != null ? Arrays.asList(selected) : Collections.emptyList();
    }

    private ExtImplDefinition selectOne(ExtPointDefinition pointDef,
                                        List<ExtImplDefinition> impls,
                                        ExtRouterContext context) {
        // 1. 优先使用「当前激活」的实现：显式 setActiveImpl 指定的那个；没有显式激活时，
        //    回退到注册中心里该扩展点的平台默认实现（基线实现）。
        //    仅当它确实出现在本次候选列表中（同一对象）时才采用，避免其它扩展点残留的注册项
        //    干扰完全由调用方自备候选的路由（例如自定义 ExtRouter 的单测）。
        ExtImplDefinition activated = findActivated(pointDef);
        if (activated != null && activated.isEnabled() && containsSame(impls, activated)) {
            return activated;
        }

        // 2. 按类型优先级选择：EXTERNAL > CUSTOM > PLATFORM
        ExtImplDefinition external = findByType(impls, ExtImplType.EXTERNAL);
        if (external != null) {
            return external;
        }

        ExtImplDefinition custom = findByType(impls, ExtImplType.CUSTOM);
        if (custom != null) {
            return custom;
        }

        return findByType(impls, ExtImplType.PLATFORM);
    }

    /**
     * 取出该扩展点当前激活的实现：显式激活优先，其次注册中心里的平台默认实现
     */
    private ExtImplDefinition findActivated(ExtPointDefinition pointDef) {
        String point = pointDef.getPoint();
        if (point == null) {
            return null;
        }
        ExtImplDefinition active = ExtRegistry.getActiveImplementation(point);
        return active != null ? active : ExtRegistry.getPlatformImpl(point);
    }

    /**
     * 按对象身份判断候选列表里是否含有该实现
     */
    private static boolean containsSame(List<ExtImplDefinition> impls, ExtImplDefinition target) {
        for (ExtImplDefinition impl : impls) {
            if (impl == target) {
                return true;
            }
        }
        return false;
    }

    private ExtImplDefinition findByType(List<ExtImplDefinition> impls, ExtImplType type) {
        return impls.stream()
                .filter(i -> i.getType() == type && i.isEnabled())
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getName() {
        return NAME;
    }
}