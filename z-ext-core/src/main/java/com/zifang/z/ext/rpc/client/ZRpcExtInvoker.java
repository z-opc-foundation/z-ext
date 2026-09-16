package com.zifang.z.ext.rpc.client;

import com.zifang.z.rpc.RpcClient;
import com.zifang.z.rpc.RpcRequest;
import com.zifang.z.rpc.RpcResponse;

import java.util.UUID;
import com.zifang.util.core.lang.RandomUtil;

/**
 * 基于z-rpc的调用器实现
 */
public class ZRpcExtInvoker implements ExtRpcInvoker {

    private final String host;
    private final int port;
    private final RpcClient rpcClient;

    public ZRpcExtInvoker(String host, int port) {
        this.host = host;
        this.port = port;
        this.rpcClient = new RpcClient(host, port);
    }

    @Override
    public Object invoke(Class<?> serviceInterface,
                         String methodName,
                         Class<?>[] parameterTypes,
                         Object[] args) throws Throwable {
        RpcRequest request = new RpcRequest();
        request.setRequestId(RandomUtil.uuid());
        request.setInterfaceName(serviceInterface.getName());
        request.setMethodName(methodName);
        request.setParameterTypes(parameterTypes);
        request.setParameters(args);

        RpcResponse response = (RpcResponse) rpcClient.sendRequest(request);

        if (response.getException() != null) {
            throw response.getException();
        }

        return response.getResult();
    }

    @Override
    public boolean isAvailable() {
        return rpcClient != null;
    }

    @Override
    public void close() {
        if (rpcClient != null) {
            rpcClient.close();
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}