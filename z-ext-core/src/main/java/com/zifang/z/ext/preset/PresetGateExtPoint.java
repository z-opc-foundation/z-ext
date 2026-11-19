package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtPoint;
import com.zifang.z.ext.annotation.ExtType;

import java.util.Map;

/**
 * 预置闸门扩展点 —— 「不改一行业务代码」那一档的落点。
 * <p>
 * 语义：宿主在关键路径上问一句「这次放不放行」，实现由注册中心决定，
 * 控制面可以在运行期把实现切走，从而在**不重新发布**的前提下改行为
 * （降级、限流、演练封禁、灰度拦截）。
 * <p>
 * 默认实现恒放行；控制面注入的 mock 实现可以让它恒拒绝 —— 这就是
 * 「一键熔断」的全部实现量。
 */
@ExtPoint(value = PresetPoints.GATE,
        description = "预置闸门：运行期由控制面决定放行或拒绝",
        version = "1.0.0",
        type = ExtType.SYNC)
public interface PresetGateExtPoint {

    /**
     * 闸门判定。
     *
     * @param event 事件标识（如 {@code http.POST./order}、{@code app.ready}）
     * @param attrs 事件上下文（路径、参数、来源等）
     * @return true 放行；false 拒绝
     */
    boolean allow(String event, Map<String, Object> attrs);

    /**
     * 拒绝时给调用方的提示内容。
     * <p>
     * 拒绝是「静默失败」还是「有话说」由实现决定：返回 null 表示无额外提示。
     *
     * @param event 事件标识
     * @param attrs 事件上下文
     * @return 提示内容；无则 null
     */
    String denyMessage(String event, Map<String, Object> attrs);
}
