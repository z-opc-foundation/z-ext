package com.zifang.z.ext.mock;

/**
 * Mock 实现的行为描述 —— 「零代码实现」的载体。
 * <p>
 * 控制面不需要业务方写任何 Java，只要描述清楚「这个扩展点被接管后应该怎么表现」，
 * 框架就能凭空造出一个实现挂上去：
 * <ul>
 *   <li>{@link Behavior#RETURN}：固定返回某个值（用于假数据演练、兜底返回）；</li>
 *   <li>{@link Behavior#THROW}：固定抛错（用于熔断演练、故障注入）；</li>
 *   <li>{@link Behavior#DENY}：恒拒绝（用于一键封禁、限流演练）。</li>
 * </ul>
 */
public class MockSpec {

    /**
     * 行为类型。
     */
    public enum Behavior {
        /** 固定返回。 */
        RETURN,
        /** 固定抛错。 */
        THROW,
        /** 恒拒绝：布尔方法返回 false，字符串方法返回提示，其余返回 null。 */
        DENY
    }

    /** 实现名（控制面按这个名字切换）。 */
    private String name;

    /** 行为。 */
    private Behavior behavior = Behavior.RETURN;

    /** 固定返回值。 */
    private Object result;

    /** 异常消息（{@link Behavior#THROW} 时使用）。 */
    private String errorMessage;

    /** 延迟毫秒数（模拟慢依赖）。 */
    private long delayMs;

    /** 描述。 */
    private String description;

    public MockSpec() {
    }

    public MockSpec(String name) {
        this.name = name;
    }

    /**
     * 固定返回一个值。
     */
    public static MockSpec returning(String name, Object result) {
        MockSpec spec = new MockSpec(name);
        spec.behavior = Behavior.RETURN;
        spec.result = result;
        spec.description = "固定返回: " + result;
        return spec;
    }

    /**
     * 固定抛错。
     */
    public static MockSpec throwing(String name, String errorMessage) {
        MockSpec spec = new MockSpec(name);
        spec.behavior = Behavior.THROW;
        spec.errorMessage = errorMessage;
        spec.description = "固定抛错: " + errorMessage;
        return spec;
    }

    /**
     * 恒拒绝。
     */
    public static MockSpec denying(String name, String reason) {
        MockSpec spec = new MockSpec(name);
        spec.behavior = Behavior.DENY;
        spec.errorMessage = reason;
        spec.description = "恒拒绝: " + reason;
        return spec;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
