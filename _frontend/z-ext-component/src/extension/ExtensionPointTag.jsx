import React from 'react'
import {Tag, Tooltip} from 'antd'
import {ApiOutlined, ExperimentOutlined} from '@ant-design/icons'

/**
 * 扩展点/实现标签
 * <p>
 * 抽自 z-ext/_frontend/src/pages/extension/ExtensionList.jsx
 *
 * @param {string} type - POINT | IMPL
 * @param {string} name - 扩展名
 * @param {string} bizType - 业务类型
 */
export default function ExtensionPointTag({type, name, bizType}) {
    const isPoint = type === 'POINT'
    const color = isPoint ? 'blue' : 'green'
    const icon = isPoint ? <ApiOutlined/> : <ExperimentOutlined/>
    const label = isPoint ? `Point · ${name}` : `Impl · ${name}`

    return (
        <Tooltip title={bizType ? `业务类型: ${bizType}` : ''}>
            <Tag color={color} icon={icon}>
                {label}
            </Tag>
        </Tooltip>
    )
}
