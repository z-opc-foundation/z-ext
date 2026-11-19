package com.zifang.z.ext.core.router;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 默认路由实现
 * <p>
 * 策略：
 * - SYNC: 优先使用激活的实现（显式 setActiveImpl，其次注册中心里的平台默认实现），
 * 没有可激活项时按 EXTERNAL &gt; CUSTOM &gt; PLATFORM 选择
 * - ASYNC: 同上
 * - CHAIN: 返回所有满足条件的实现，按 order 排序执行
 * <p>
 * 候选集先过一道条件过滤（{@code ExtImpl.condition}），再按上述优先级选：
 * <ul>
 *   <li>同类型内先按 {@code order} 取最小的那一档，{@code order} 相同才由 {@code weight} 按比例分流；</li>
 *   <li>条件不满足或已停用的实现不参与选择。</li>
 * </ul>
 */
public class DefaultExtRouter implements ExtRouter {

    public static final String NAME = "default";

    /**
     * 实现类型的选取优先级。
     */
    private static final List<ExtImplType> TYPE_PRIORITY =
            Collections.unmodifiableList(Arrays.asList(
                    ExtImplType.EXTERNAL, ExtImplType.CUSTOM, ExtImplType.PLATFORM));

    private volatile ExtConditionEvaluator conditionEvaluator = new SpelConditionEvaluator();

    public DefaultExtRouter() {
    }

    public DefaultExtRouter(ExtConditionEvaluator conditionEvaluator) {
        if (conditionEvaluator != null) {
            this.conditionEvaluator = conditionEvaluator;
        }
    }

    public ExtConditionEvaluator getConditionEvaluator() {
        return conditionEvaluator;
    }

    public void setConditionEvaluator(ExtConditionEvaluator conditionEvaluator) {
        if (conditionEvaluator != null) {
            this.conditionEvaluator = conditionEvaluator;
        }
    }

    @Override
    public List<ExtImplDefinition> route(ExtPointDefinition pointDef,
                                         List<ExtImplDefinition> impls,
                                         ExtRouterContext context) {
        if (impls == null || impls.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExtImplDefinition> candidates = filterByCondition(impls, context);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // CHAIN 类型返回所有实现
        if (pointDef.getType() == ExtType.CHAIN) {
            return candidates;
        }

        // SYNC/ASYNC 类型只返回一个实现
        ExtImplDefinition selected = selectOne(pointDef, candidates, context);
        return selected != null ? Arrays.asList(selected) : Collections.emptyList();
    }

    /**
     * 过滤掉条件不满足的实现。
     */
    private List<ExtImplDefinition> filterByCondition(List<ExtImplDefinition> impls,
                                                       ExtRouterContext context) {
        ExtConditionEvaluator evaluator = this.conditionEvaluator;
        List<ExtImplDefinition> candidates = new ArrayList<>(impls.size());
        for (ExtImplDefinition impl : impls) {
            if (evaluator.matches(impl.getCondition(), context)) {
                candidates.add(impl);
            }
        }
        return candidates;
    }

    private ExtImplDefinition selectOne(ExtPointDefinition pointDef,
                                        List<ExtImplDefinition> impls,
                                        ExtRouterContext context) {
        // 1. 优先使用「当前激活」的实现：显式激活优先，其次注册中心里的平台默认实现。
        //    仅当它确实出现在本次候选列表中（同一对象）时才采用，避免其它扩展点残留的注册项
        //    干扰完全由调用方自备候选的路由（例如自定义 ExtRouter 的单测）。
        ExtImplDefinition activated = findActivated(pointDef);
        if (activated != null && activated.isEnabled() && containsSame(impls, activated)) {
            return activated;
        }

        // 2. 按类型优先级选择：EXTERNAL > CUSTOM > PLATFORM；同类型内再按 order、weight 收敛。
        for (ExtImplType type : TYPE_PRIORITY) {
            List<ExtImplDefinition> group = findByType(impls, type);
            if (!group.isEmpty()) {
                return selectByWeight(group);
            }
        }
        return null;
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

    private List<ExtImplDefinition> findByType(List<ExtImplDefinition> impls, ExtImplType type) {
        List<ExtImplDefinition> group = new ArrayList<>();
        for (ExtImplDefinition impl : impls) {
            if (impl.getType() == type && impl.isEnabled()) {
                group.add(impl);
            }
        }
        return group;
    }

    /**
     * 同类型内选出唯一实现：order 最小的那一档优先；order 相同则按 weight 比例分流。
     * <p>
     * 候选列表来自 {@code getEnabledImplementations}，已按 order 升序。
     */
    private ExtImplDefinition selectByWeight(List<ExtImplDefinition> group) {
        int minOrder = group.get(0).getOrder();
        List<ExtImplDefinition> sameOrder = new ArrayList<>();
        for (ExtImplDefinition impl : group) {
            if (impl.getOrder() == minOrder) {
                sameOrder.add(impl);
            }
        }
        if (sameOrder.size() == 1) {
            return sameOrder.get(0);
        }

        int total = 0;
        for (ExtImplDefinition impl : sameOrder) {
            total += weightOf(impl);
        }
        int hit = ThreadLocalRandom.current().nextInt(total);
        int accumulated = 0;
        for (ExtImplDefinition impl : sameOrder) {
            accumulated += weightOf(impl);
            if (hit < accumulated) {
                return impl;
            }
        }
        return sameOrder.get(sameOrder.size() - 1);
    }

    private static int weightOf(ExtImplDefinition impl) {
        return impl.getWeight() > 0 ? impl.getWeight() : 1;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
