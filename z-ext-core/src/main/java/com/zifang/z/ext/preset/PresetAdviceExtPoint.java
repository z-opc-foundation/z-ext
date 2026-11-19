package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtPoint;
import com.zifang.z.ext.annotation.ExtType;

import java.util.Map;

/**
 * 预置环绕扩展点 —— 「织入既有调用」那一档的落点。
 * <p>
 * 与 {@link PresetGateExtPoint} 的差别：闸门只能在「放行 / 拒绝」之间选，
 * 环绕可以包住原调用 —— 实现先拿到 {@link ProceedAction}，
 * 可以放行后改写返回值、可以替换结果、也可以直接抛错中断。
 * <p>
 * 落点由宿主决定（starter 的 AOP 织入、拦截器、作业包装等），
 * 框架侧只保证「同一个点可以被任何一种落点复用」。
 */
@ExtPoint(value = PresetPoints.ADVICE,
        description = "预置环绕：包住既有调用，可改写结果或中断",
        version = "1.0.0",
        type = ExtType.SYNC)
public interface PresetAdviceExtPoint {

    /**
     * 环绕执行。
     *
     * @param event  事件标识
     * @param attrs  事件上下文
     * @param action 继续执行的回调（实现不调用它即视为短路）
     * @return 原调用的返回值，或实现自己给出的结果
     * @throws Throwable 实现抛出的异常直接中断原调用
     */
    Object around(String event, Map<String, Object> attrs, ProceedAction action) throws Throwable;

    /**
     * 继续原调用的回调。
     */
    @FunctionalInterface
    interface ProceedAction {

        /**
         * 执行原调用。
         *
         * @return 原调用返回值
         * @throws Throwable 原调用抛出的异常
         */
        Object proceed() throws Throwable;
    }
}
