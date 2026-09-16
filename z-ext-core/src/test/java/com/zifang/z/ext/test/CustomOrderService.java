package com.zifang.z.ext.test;

import com.zifang.z.ext.annotation.ExtImpl;
import com.zifang.z.ext.annotation.ExtImplType;

/**
 * 自定义实现
 */
@ExtImpl(point = "order.service", name = "custom", type = ExtImplType.CUSTOM, order = 1)
public class CustomOrderService implements OrderService {

    @Override
    public String createOrder(String productId, int quantity) {
        return "CUSTOM: Order created for product " + productId + ", quantity " + quantity;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        System.out.println("CUSTOM: Cancel order " + orderId);
        return true;
    }

    @Override
    public String getOrder(String orderId) {
        return "CUSTOM: Order details for " + orderId;
    }
}