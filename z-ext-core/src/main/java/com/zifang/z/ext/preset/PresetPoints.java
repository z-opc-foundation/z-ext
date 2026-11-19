package com.zifang.z.ext.preset;

/**
 * 预置扩展点的标识常量。
 * <p>
 * 这三个点是 z-ext 自带的「零业务侵入」落点：宿主只要引入 starter，就自动注册，
 * 控制台立刻能看到并切换它们，业务代码一行不改。
 * <ul>
 *   <li>{@link #GATE}：闸门 —— 运行期决定放行还是拒绝（降级/熔断/演练）；</li>
 *   <li>{@link #ADVICE}：环绕 —— 包住既有调用，可改写结果或中断；</li>
 *   <li>{@link #EVENT}：通知 —— 观测型埋点，把运行期事件抛给控制面。</li>
 * </ul>
 */
public final class PresetPoints {

    /** 闸门扩展点标识。 */
    public static final String GATE = "z.ext.preset.gate";

    /** 环绕扩展点标识。 */
    public static final String ADVICE = "z.ext.preset.advice";

    /** 通知扩展点标识。 */
    public static final String EVENT = "z.ext.preset.event";

    private PresetPoints() {
    }
}
