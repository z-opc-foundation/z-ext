package com.zifang.z.ext.rpc.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC调用器工厂
 * 管理RPC客户端连接
 */
public class ExtRpcInvokerFactory {

    /**
     * RPC客户端缓存 key: host:port
     */
    private static final Map<String, ExtRpcInvoker> INVOKER_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取或创建RPC调用器
     *
     * @param host RPC主机地址
     * @param port RPC端口
     * @return RPC调用器
     */
    public static ExtRpcInvoker getInvoker(String host, int port) {
        String key = host + ":" + port;
        return INVOKER_CACHE.computeIfAbsent(key, k -> new ZRpcExtInvoker(host, port));
    }

    /**
     * 移除RPC调用器
     */
    public static void removeInvoker(String host, int port) {
        String key = host + ":" + port;
        ExtRpcInvoker invoker = INVOKER_CACHE.remove(key);
        if (invoker != null) {
            invoker.close();
        }
    }

    /**
     * 关闭所有调用器
     */
    public static void closeAll() {
        for (ExtRpcInvoker invoker : INVOKER_CACHE.values()) {
            invoker.close();
        }
        INVOKER_CACHE.clear();
    }

    /**
     * 获取缓存的调用器数量
     */
    public static int getInvokerCount() {
        return INVOKER_CACHE.size();
    }
}