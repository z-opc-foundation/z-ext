package com.zifang.z.ext.annotation;

/**
 * 扩展点执行类型
 */
public enum ExtType {
    /**
     * 同步执行 - 只选择一个实现执行
     */
    SYNC,

    /**
     * 异步执行 - 异步执行，不阻塞主流程
     */
    ASYNC,

    /**
     * 链式执行 - 多个实现按顺序依次执行
     */
    CHAIN
}