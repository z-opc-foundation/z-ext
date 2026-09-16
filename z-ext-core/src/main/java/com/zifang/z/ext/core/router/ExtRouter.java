package com.zifang.z.ext.core.router;

import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;

import java.util.List;

/**
 * 扩展路由接口
 * 决定在运行时选择哪个扩展实现
 */
public interface ExtRouter {

    /**
     * 路由选择
     *
     * @param pointDef 扩展点定义
     * @param impls    可用的实现列表
     * @param context  路由上下文（包含方法参数等）
     * @return 选中的实现列表（CHAIN类型可能返回多个）
     */
    List<ExtImplDefinition> route(ExtPointDefinition pointDef,
                                  List<ExtImplDefinition> impls,
                                  ExtRouterContext context);

    /**
     * 路由类型名称
     */
    String getName();
}