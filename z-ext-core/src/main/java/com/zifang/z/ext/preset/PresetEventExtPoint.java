package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtPoint;
import com.zifang.z.ext.annotation.ExtType;

import java.util.Map;

/**
 * 预置通知扩展点 —— 观测型埋点。
 * <p>
 * 与闸门/环绕不同，它不改行为，只把运行期事件抛给实现：
 * 默认实现是空实现（不产生任何开销与噪音），
 * 控制面可以给它挂上真实实现（转发到 z-observer、发告警、写审计）。
 */
@ExtPoint(value = PresetPoints.EVENT,
        description = "预置通知：运行期事件出口（观测/告警/审计）",
        version = "1.0.0",
        type = ExtType.SYNC)
public interface PresetEventExtPoint {

    /**
     * 通知一个运行期事件。
     *
     * @param event 事件标识
     * @param attrs 事件上下文
     */
    void publish(String event, Map<String, Object> attrs);
}
