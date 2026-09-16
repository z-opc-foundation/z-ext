package com.zifang.z.ext.test;

import com.zifang.z.ext.annotation.ExtImpl;
import com.zifang.z.ext.annotation.ExtImplType;

/**
 * 平台默认实现
 */
@ExtImpl(point = "order.service", name = "default", type = ExtImplType.PLATFORM)
public class DefaultOrderService implements OrderService {

    @Override
    public String createOrder(String productId, int quantity) {
        return "PLATFORM: Order created for product " + productId + ", quantity " + quantity;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        System.out.println("PLATFORM: Cancel order " + orderId);
        return true;
    }

    @Override
    public String getOrder(String orderId) {
        return "PLATFORM: Order details for " + orderId;
    }
}