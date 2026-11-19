package com.zifang.z.ext.core.router;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Spring SpEL 的条件求值。
 * <p>
 * 表达式有两种写法，都支持：
 * <ul>
 *   <li>直接 SpEL：{@code #tenantId == 'vip'}；</li>
 *   <li>模板写法：{@code #{environment.getProperty('app.env') == 'prod'}}
 *       —— 与 {@code @ExtImpl#condition} 的注释口径一致，外层 {@code #{}} 会被剥掉再求值。</li>
 * </ul>
 * 求值上下文里可用的变量：
 * <ul>
 *   <li>{@code #methodName} / {@code #args}：当前调用信息；</li>
 *   <li>{@code #environment}：Spring {@link Environment}；</li>
 *   <li>路由上下文属性里的每一个 key，都以同名变量暴露（如 {@code #tenantId}）。</li>
 * </ul>
 * 根对象是 {@link ExtRouterContext} 本身，所以表达式里也可以直接写 {@code methodName}。
 */
public class SpelConditionEvaluator implements ExtConditionEvaluator {

    private static final Logger log = LogManager.getLogger(SpelConditionEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Environment environment;
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    public SpelConditionEvaluator() {
        this(new StandardEnvironment());
    }

    public SpelConditionEvaluator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean matches(String condition, ExtRouterContext context) {
        if (condition == null) {
            return true;
        }
        String expression = condition.trim();
        if (expression.isEmpty()) {
            return true;
        }
        if (expression.startsWith("#{") && expression.endsWith("}")) {
            expression = expression.substring(2, expression.length() - 1).trim();
            if (expression.isEmpty()) {
                return true;
            }
        }
        try {
            Expression parsed = cache.computeIfAbsent(expression, parser::parseExpression);
            StandardEvaluationContext evaluationContext = new StandardEvaluationContext(context);
            evaluationContext.setVariable("methodName", context.getMethodName());
            evaluationContext.setVariable("args", context.getArgs());
            evaluationContext.setVariable("environment", environment);
            for (Map.Entry<String, Object> attribute : context.getAttributes().entrySet()) {
                evaluationContext.setVariable(attribute.getKey(), attribute.getValue());
            }
            return Boolean.TRUE.equals(parsed.getValue(evaluationContext, Boolean.class));
        } catch (RuntimeException e) {
            // 求值失败一律判为不命中：条件的意图是"限制生效范围"，无法确认时不该默认放行。
            log.error("[z-ext] 扩展点路由条件求值失败，按不命中处理: condition={}", expression, e);
            return false;
        }
    }
}
