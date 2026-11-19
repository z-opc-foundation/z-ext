package com.zifang.z.ext.core.admin;

import com.zifang.z.ext.core.registry.ExtImplDefinition;

/**
 * 扩展点的控制面契约。
 * <p>
 * 框架不内置任何 HTTP 层，但把「谁能看、谁能切」这件事定义成契约，
 * 于是控制面可以用多种形态接入而不必改框架：
 * <ul>
 *   <li>嵌入式：业务进程自己装配一个 controller，实现本接口；</li>
 *   <li>Agent 形态：业务进程暴露本接口，独立控制面远程调用；</li>
 *   <li>纯库形态：调用方直接编程式使用 {@code ExtRegistry}，本接口不参与。</li>
 * </ul>
 * 所有形态的切换最终都落到业务进程持有的 {@code ActiveImplStore} 上，
 * 因此「切换成功」的语义是明确的：本进程下一次路由即生效。
 */
public interface ExtAdminProvider {

    /**
     * 读取本进程全部扩展点与实现状态。
     */
    ExtSnapshot snapshot();

    /**
     * 激活指定扩展点的某个实现。
     *
     * @return 实现不存在时返回 {@code false}
     */
    boolean activate(String point, String implName);

    /**
     * 取消扩展点的显式激活（回落平台默认实现）。
     */
    boolean deactivate(String point);

    /**
     * 启用/停用某个实现。
     *
     * @return 实现不存在时返回 {@code false}
     */
    boolean setEnabled(String point, String implName, boolean enabled);

    /**
     * 登记一个实现（如脚本实现）。
     *
     * @return 实现名冲突等注册失败时返回 {@code false}
     */
    boolean registerImpl(ExtImplDefinition definition);
}
