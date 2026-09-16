package com.zifang.z.ext.test;

import com.zifang.z.ext.annotation.ExtPoint;

/**
 * 测试用扩展点接口 - 支付服务
 * 用于测试注解标记
 */
@ExtPoint(value = "payment.service", type = com.zifang.z.ext.annotation.ExtType.SYNC, description = "支付服务")
public interface PaymentService {

    /**
     * 支付
     */
    boolean pay(String orderId, double amount);

    /**
     * 退款
     */
    boolean refund(String orderId, double amount);
}