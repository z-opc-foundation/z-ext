import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space} from 'antd'
import {EditOutlined, PlusOutlined, SwapOutlined} from '@ant-design/icons'
import {ErrorState, ListTable} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'
import ExtensionPointTag from './ExtensionPointTag'

const {Option} = Select

const ExtensionList = () => {
    const [dataSource, setDataSource] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalVisible, setModalVisible] = useState(false)
    const [editingRecord, setEditingRecord] = useState(null)
    const [error, setError] = useState(null)
    const [form] = Form.useForm()

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
        },
        {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            width: 100,
            render: (type) => {
                return <ExtensionPointTag type={type} name={record.pointName || record.implName}
                                          bizType={record.bizType}/>
            },
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
        },
        {
            title: '版本',
            dataIndex: 'version',
            key: 'version',
            width: 100,
        },
        {
            title: '实现数量',
            dataIndex: 'implCount',
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
                        type="link"
                        icon={<SwapOutlined/>}
                        onClick={() => handleSwitch(record)}
                    >
                        切换
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
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
            const res = await request.get('/api/ext/points')
            setDataSource(res.data || [])
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

    const handleSwitch = (record) => {
        Modal.confirm({
            title: '切换实现',
            content: `请选择要切换到的实现`,
            okText: '确认',
            cancelText: '取消',
            onOk: async () => {
                message.success('切换成功')
                fetchData()
            },
        })
    }

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields()
            if (editingRecord) {
                await request.put(`/api/ext/points/${editingRecord.id}`, values)
                message.success('更新成功')
            } else {
                await request.post('/api/ext/points', values)
                message.success('创建成功')
            }
            setModalVisible(false)
            fetchData()
        } catch (error) {
            message.error('操作失败')
        }
    }

    const handleDelete = async (record) => {
        Modal.confirm({
            title: '确认删除',
            content: `确定要删除扩展点 ${record.point} 吗？`,
            onOk: async () => {
                try {
                    await request.delete(`/api/ext/points/${record.id}`)
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
                        <Input placeholder="例如: order.create"/>
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
        </div>
    )
}

export default ExtensionList