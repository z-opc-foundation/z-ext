import {useEffect, useState} from 'react'
import {Card, Col, Row, Statistic, Table, Tag} from 'antd'
import {ApiOutlined, CheckCircleOutlined, ExperimentOutlined, SwapOutlined} from '@ant-design/icons'
import {DEFAULT_PAGINATION, EmptyState, ErrorState} from '@yuku123/z-frontend-common/ui'
import request from '../services/request'
import {extPath, InstanceSelect, unwrapList} from '../extension/InstanceSelect'

const Dashboard = () => {
    const [points, setPoints] = useState([])
    const [impls, setImpls] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        fetchDashboardData()
    }, [])

    const fetchDashboardData = async () => {
        setLoading(true)
        try {
            const [pointsRes, implsRes] = await Promise.all([
                request.get(extPath('/ext/points')),
                request.get(extPath('/ext/implementations')),
            ])
            setPoints(unwrapList(pointsRes))
            setImpls(unwrapList(implsRes))
            setError(null)
        } catch (e) {
            // 保留原始 error 对象：ErrorState 会优先取后端 response.data.message，
            // 压成字符串就只剩 axios 的 "Request failed with status code 500"。
            setError(e)
        } finally {
            setLoading(false)
        }
    }

    // 激活中的扩展点：有显式激活实现（activeImpl）的，即介入生效中的点
    const activated = points.filter((p) => p.activeImpl)

    const pointColumns = [
        {
            title: '扩展点',
            dataIndex: 'point',
            key: 'point',
        },
        {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            width: 100,
            render: (type) => <Tag>{type}</Tag>,
        },
        {
            title: '激活实现',
            dataIndex: 'activeImpl',
            key: 'activeImpl',
            width: 180,
            render: (v) => <Tag color="green">{v}</Tag>,
        },
        {
            title: '实现数',
            key: 'implCount',
            width: 90,
            render: (_, record) => record.implementations?.length || 0,
        },
    ]

    return (
        <div>
            <div style={{marginBottom: 16}}>
                <InstanceSelect onChange={() => fetchDashboardData()}/>
            </div>

            {error && (
                <div style={{marginBottom: 16}}>
                    <ErrorState error={error} title="获取扩展点概览失败" onRetry={() => fetchDashboardData()}/>
                </div>
            )}
            <Row gutter={16}>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="扩展点数量"
                            value={points.length}
                            prefix={<ApiOutlined/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="实现数量"
                            value={impls.length}
                            prefix={<ExperimentOutlined/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="启用实现"
                            value={impls.filter((i) => i.enabled).length}
                            prefix={<CheckCircleOutlined/>}
                            valueStyle={{color: '#3f8600'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="激活中的扩展点"
                            value={activated.length}
                            prefix={<SwapOutlined/>}
                            valueStyle={{color: '#cf1322'}}
                        />
                    </Card>
                </Col>
            </Row>

            <div style={{marginTop: 24}}>
                {/* 「最近活动」假数据已撤：控制台不存审计日志，只展示真实在激活中的扩展点。
                    操作审计靠部署侧网关日志（见 README「已知边界」）。 */}
                <Card title="激活中的扩展点（介入生效中）">
                    <Table
                        columns={pointColumns}
                        dataSource={activated}
                        loading={loading}
                        rowKey="id"
                        pagination={DEFAULT_PAGINATION}
                        locale={{emptyText: <EmptyState title="没有显式激活的扩展点" description="全部按默认路由（order / 类型优先）执行" size="sm"/>}}
                    />
                </Card>
            </div>
        </div>
    )
}

export default Dashboard
