package com.zifang.z.ext.core.router;

/**
 * 路由条件求值契约。
 * <p>
 * {@code ExtImpl.condition} 声明了表达式但求值方式留空，这里把它抽成契约，
 * 让路由策略与具体表达式引擎解耦：默认实现用 Spring SpEL，
 * 需要灰度标签、租户规则等更复杂的判定时可以换掉。
 */
public interface ExtConditionEvaluator {

    /**
     * 判定某个实现是否在当前上下文下满足条件。
     * <p>
     * 空表达式视为无条件命中。
     */
    boolean matches(String condition, ExtRouterContext context);
}
