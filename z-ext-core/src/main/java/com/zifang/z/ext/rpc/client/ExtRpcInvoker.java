package com.zifang.z.ext.rpc.client;

/**
 * RPC调用器接口
 * 用于执行远程RPC调用
 */
public interface ExtRpcInvoker {

    /**
     * 执行RPC调用
     *
     * @param serviceInterface 服务接口
     * @param methodName       方法名
     * @param parameterTypes   参数类型
     * @param args             参数
     * @return 调用结果
     */
    Object invoke(Class<?> serviceInterface,
                  String methodName,
                  Class<?>[] parameterTypes,
                  Object[] args) throws Throwable;

    /**
     * 检查invoker是否可用
     */
    boolean isAvailable();

    /**
     * 关闭invoker
     */
    void close();
}