package com.zifang.z.ext.test;

/**
 * 测试用扩展点接口 - 订单服务
 */
public interface OrderService {

    /**
     * 创建订单
     */
    String createOrder(String productId, int quantity);

    /**
     * 取消订单
     */
    boolean cancelOrder(String orderId);

    /**
     * 查询订单
     */
    String getOrder(String orderId);
}