import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space, Tag} from 'antd'
import {PlusOutlined, SwapOutlined} from '@ant-design/icons';
import {ErrorState, ListTable} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'
import {extPath, InstanceSelect, unwrapList} from '../../extension/InstanceSelect'
import ExtensionPointTag from './ExtensionPointTag'

const {Option} = Select

const typeColors = {
    SYNC: 'blue',
    ASYNC: 'purple',
    CHAIN: 'geekblue',
}

const ExtensionList = () => {
    const [dataSource, setDataSource] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalVisible, setModalVisible] = useState(false)
    const [editingRecord, setEditingRecord] = useState(null)
    const [error, setError] = useState(null)
    const [form] = Form.useForm()
    const [switchTarget, setSwitchTarget] = useState(null)
    const [switchImpl, setSwitchImpl] = useState(undefined)

    const columns = [
        {
            title: '扩展点标识',
            dataIndex: 'point',
            key: 'point',
            width: 200,
        },
        {
            title: '接口类',
            dataIndex: 'interfaceClass',
            key: 'interfaceClass',
            width: 250,
            ellipsis: true,
        },
        {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            width: 100,
            render: (type) => <Tag color={typeColors[type] || 'default'}>{type}</Tag>,
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            ellipsis: true,
        },
        {
            title: '当前激活',
            dataIndex: 'activeImpl',
            key: 'activeImpl',
            width: 160,
            render: (activeImpl) => activeImpl
                ? <ExtensionPointTag type="IMPL" name={activeImpl}/>
                : <Tag>默认路由</Tag>,
        },
        {
            title: '实现数量',
            key: 'implCount',
            width: 100,
            render: (_, record) => record.implementations?.length || 0,
        },
        {
            title: '操作',
            key: 'action',
            width: 200,
            render: (_, record) => (
                <Space>
                    <Button
                        type="link" size="small" icon={<SwapOutlined/>}
                        onClick={() => handleSwitch(record)}
                    >
                        切换
                    </Button>
                    <Button
                        type="link" size="small"
                        onClick={() => handleEdit(record)}
                    >
                        编辑
                    </Button>
                </Space>
            ),
        },
    ]

    const fetchData = async () => {
        setLoading(true)
        try {
            const res = await request.get(extPath('/ext/points'))
            setDataSource(unwrapList(res))
            setError(null)
        } catch (e) {
            // 保留原始 error 对象：ErrorState 会优先取后端 response.data.message，
            // 压成字符串就只剩 axios 的 "Request failed with status code 500"。
            setError(e)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchData()
    }, [])

    const handleAdd = () => {
        setEditingRecord(null)
        form.resetFields()
        setModalVisible(true)
    }

    const handleEdit = (record) => {
        setEditingRecord(record)
        form.setFieldsValue(record)
        setModalVisible(true)
    }

    // 切换激活实现：POST activate（选"恢复默认路由"则 DELETE 取消激活）。
    // 切换落在目标实例自己的 ActiveImplStore 上 —— 这就是介入动作本身。
    // 弹窗用受控 Modal（confirm 的 content 不响应 state），见 render 尾部。
    const handleSwitch = (record) => {
        setSwitchImpl(record.activeImpl || undefined)
        setSwitchTarget(record)
    }

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields()
            if (editingRecord) {
                await request.put(extPath(`/ext/points/${editingRecord.id}`), values)
                message.success('更新成功')
            } else {
                await request.post(extPath('/ext/points'), values)
                message.success('创建成功')
            }
            setModalVisible(false)
            fetchData()
        } catch (error) {
            message.error(error?.message || '操作失败')
        }
    }

    const handleDelete = async (record) => {
        Modal.confirm({
            title: '确认删除',
            content: `确定要删除扩展点 ${record.point} 吗？`,
            onOk: async () => {
                try {
                    await request.delete(extPath(`/ext/points/${record.id}`))
                    message.success('删除成功')
                    fetchData()
                } catch (error) {
                    message.error('删除失败')
                }
            },
        })
    }

    return (
        <div>
            <div style={{marginBottom: 16}}>
                <InstanceSelect onChange={() => fetchData()}/>
            </div>

            {error && (
                <div style={{marginBottom: 16}}>
                    <ErrorState error={error} title="获取扩展点列表失败" onRetry={() => fetchData()}/>
                </div>
            )}

            {/* 列表页三件套（分页 + 查询 + EmptyState）由基座 ListTable 一次带齐；
                「新建扩展点」放 cardExtra，与搜索框并排（lead 008 §2）。 */}
            <ListTable
                cardExtra={
                    <Button
                        type="primary"
                        icon={<PlusOutlined/>}
                        onClick={handleAdd}
                    >
                        新建扩展点
                    </Button>
                }
                rows={dataSource}
                columns={columns}
                loading={loading}
                rowKey="id"
                searchKeys={['point', 'interfaceClass', 'description']}
                searchPlaceholder="按扩展点 / 接口类 / 描述过滤"
                emptyTitle="暂无扩展点"
                emptyDescription="点「新建扩展点」登记第一个扩展点"
            />

            <Modal
                title={editingRecord ? '编辑扩展点' : '新建扩展点'}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                width={600}
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="point"
                        label="扩展点标识"
                        rules={[{required: true, message: '请输入扩展点标识'}]}
                    >
                        <Input placeholder="例如: order.create" disabled={!!editingRecord}/>
                    </Form.Item>

                    <Form.Item
                        name="interfaceClass"
                        label="接口类"
                        rules={[{required: true, message: '请输入接口类全限定名'}]}
                    >
                        <Input placeholder="例如: com.example.OrderService"/>
                    </Form.Item>

                    <Form.Item
                        name="type"
                        label="扩展点类型"
                        rules={[{required: true, message: '请选择扩展点类型'}]}
                    >
                        <Select placeholder="请选择">
                            <Option value="SYNC">SYNC - 同步执行</Option>
                            <Option value="ASYNC">ASYNC - 异步执行</Option>
                            <Option value="CHAIN">CHAIN - 链式执行</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <Input.TextArea rows={3}/>
                    </Form.Item>

                    <Form.Item
                        name="version"
                        label="版本"
                    >
                        <Input placeholder="1.0.0"/>
                    </Form.Item>
                </Form>
            </Modal>

            {/* 切换实现弹窗：受控组件而非 Modal.confirm —— confirm 的 content 不会响应 state */}
            <Modal
                open={!!switchTarget}
                title={`切换激活实现：${switchTarget?.point || ''}`}
                okText="确认切换"
                cancelText="取消"
                onCancel={() => setSwitchTarget(null)}
                onOk={async () => {
                    try {
                        if (switchImpl) {
                            await request.post(
                                extPath(`/ext/points/${encodeURIComponent(switchTarget.point)}/activate`),
                                {implName: switchImpl})
                            message.success(`已切换到 ${switchImpl}`)
                        } else {
                            await request.delete(
                                extPath(`/ext/points/${encodeURIComponent(switchTarget.point)}/activate`))
                            message.success('已取消激活，回退默认路由')
                        }
                        setSwitchTarget(null)
                        fetchData()
                    } catch (e) {
                        message.error(e?.message || '切换失败')
                    }
                }}
            >
                <Select
                    style={{width: '100%'}}
                    placeholder="选择要激活的实现（不选 = 恢复默认路由）"
                    allowClear
                    value={switchImpl}
                    onChange={setSwitchImpl}
                >
                    {(switchTarget?.implementations || [])
                        .filter((i) => i.enabled)
                        .map((i) => (
                            <Option key={i.name} value={i.name}>
                                {i.name}（{i.type}）{i.active ? ' · 当前激活' : ''}
                            </Option>
                        ))}
                </Select>
            </Modal>
        </div>
    )
}

export default ExtensionList
