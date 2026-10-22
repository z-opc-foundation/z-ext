import {useEffect, useState} from 'react'
import {Card, Col, Row, Statistic, Table, Tag} from 'antd'
import {ApiOutlined, CheckCircleOutlined, ExperimentOutlined, SwapOutlined} from '@ant-design/icons'
import {EmptyState, ErrorState} from '@yuku123/z-frontend-common/ui'
import request from '../services/request'

const Dashboard = () => {
    const [stats, setStats] = useState({
        extPointCount: 0,
        implCount: 0,
        activeImplCount: 0,
        switchCount: 0,
    })
    const [recentActivities, setRecentActivities] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        fetchDashboardData()
    }, [])

    const fetchDashboardData = async () => {
        setLoading(true)
        try {
            const [pointsRes, implsRes] = await Promise.all([
                request.get('/api/ext/points'),
                request.get('/api/ext/implementations'),
            ])

            const points = pointsRes.data || []
            const impls = implsRes.data || []

            setStats({
                extPointCount: points.length,
                implCount: impls.length,
                activeImplCount: impls.filter(i => i.enabled).length,
                switchCount: 0,
            })

            // 模拟最近活动
            setRecentActivities([
                {id: 1, action: '注册实现', target: 'order.create -> alipay', time: '2分钟前'},
                {id: 2, action: '切换实现', target: 'payment.process -> wechat', time: '1小时前'},
                {id: 3, action: '新增扩展点', target: 'user.validate', time: '3小时前'},
            ])
            setError(null)
        } catch (e) {
            // 保留原始 error 对象：ErrorState 会优先取后端 response.data.message，
            // 压成字符串就只剩 axios 的 "Request failed with status code 500"。
            setError(e)
        } finally {
            setLoading(false)
        }
    }

    const activityColumns = [
        {
            title: '操作',
            dataIndex: 'action',
            key: 'action',
            render: (action) => {
                const colorMap = {
                    '注册实现': 'green',
                    '切换实现': 'blue',
                    '新增扩展点': 'purple',
                }
                return <Tag color={colorMap[action]}>{action}</Tag>
            },
        },
        {
            title: '目标',
            dataIndex: 'target',
            key: 'target',
        },
        {
            title: '时间',
            dataIndex: 'time',
            key: 'time',
            width: 120,
        },
    ]

    return (
        <div>
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
                            value={stats.extPointCount}
                            prefix={<ApiOutlined/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="实现数量"
                            value={stats.implCount}
                            prefix={<ExperimentOutlined/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="激活实现"
                            value={stats.activeImplCount}
                            prefix={<CheckCircleOutlined/>}
                            valueStyle={{color: '#3f8600'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="切换次数"
                            value={stats.switchCount}
                            prefix={<SwapOutlined/>}
                            valueStyle={{color: '#cf1322'}}
                        />
                    </Card>
                </Col>
            </Row>

            <div style={{marginTop: 24}}>
                <Card title="最近活动">
                    <Table
                        columns={activityColumns}
                        dataSource={recentActivities}
                        loading={loading}
                        rowKey="id"
                        pagination={false}
                        locale={{emptyText: <EmptyState title="暂无最近活动" description="接口返回空列表" size="sm"/>}}
                    />
                </Card>
            </div>
        </div>
    )
}

export default Dashboard