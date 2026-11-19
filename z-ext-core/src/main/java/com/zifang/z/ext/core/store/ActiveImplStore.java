package com.zifang.z.ext.core.store;

import java.util.Map;

/**
 * 激活实现状态的读写契约。
 * <p>
 * 框架里唯一需要跨进程对齐的可变状态就是「哪个实现当前生效」——扩展点与实现的定义
 * 随代码静态确定，而激活选择是运行期决定的。把这一个出口抽成 SPI，控制面就能以
 * 不同形态接入，而不必改框架：
 * <ul>
 *   <li>{@link LocalActiveImplStore}：JVM 内内存，默认形态；</li>
 *   <li>外置形态：读写配置中心/数据库，业务进程订阅变更后刷新本地缓存；</li>
 *   <li>转发形态：控制面把切换指令转发到业务进程，由业务进程自己的 store 落地。</li>
 * </ul>
 * 无论哪种形态，**最终写入的都必须是业务进程持有的那份 store**，否则切换只是界面状态，
 * 不产生真实效果。
 */
public interface ActiveImplStore {

    /**
     * 指定扩展点当前生效的实现。
     *
     * @param point    扩展点标识
     * @param implName 实现名；传 {@code null} 表示取消激活（回落到平台默认实现）
     */
    void setActiveImpl(String point, String implName);

    /**
     * 读取扩展点当前生效的实现名；没有显式激活时返回 {@code null}。
     */
    String getActiveImpl(String point);

    /**
     * 取消扩展点的显式激活。
     */
    default void removeActiveImpl(String point) {
        setActiveImpl(point, null);
    }

    /**
     * 全部显式激活映射，key 为扩展点标识。
     */
    Map<String, String> getAll();

    /**
     * 清空激活映射。
     * <p>
     * 默认空实现：共享型 store（配置中心/数据库）被清空是危险动作，
     * 由具体实现决定是否允许。{@link LocalActiveImplStore} 会真的清。
     */
    default void clear() {
    }
}
