package com.zifang.z.ext.core.router;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 默认路由实现
 * <p>
 * 策略：
 * - SYNC: 优先使用激活的实现，如果没有激活的，优先 EXTERNAL > CUSTOM > PLATFORM
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
        // 1. 检查是否有激活的实现
        String activeImplName = impls.stream()
                .filter(i -> i.isEnabled())
                .map(ExtImplDefinition::getName)
                .findFirst()
                .orElse(null);

        if (activeImplName != null) {
            return impls.stream()
                    .filter(i -> i.getName().equals(activeImplName))
                    .findFirst()
                    .orElse(null);
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