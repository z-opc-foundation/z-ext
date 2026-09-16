import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space, Table} from 'antd'
import {EditOutlined, PlusOutlined, SwapOutlined} from '@ant-design/icons'
import request from '../../utils/request'
import {ExtensionPointTag} from '@yuku123/z-ext-frontend-component'

const {Option} = Select

const ExtensionList = () => {
    const [dataSource, setDataSource] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalVisible, setModalVisible] = useState(false)
    const [editingRecord, setEditingRecord] = useState(null)
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
        } catch (error) {
            message.error('获取扩展点列表失败')
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
            <div style={{marginBottom: 16}}>
                <Button
                    type="primary"
                    icon={<PlusOutlined/>}
                    onClick={handleAdd}
                >
                    新建扩展点
                </Button>
            </div>

            <Table
                columns={columns}
                dataSource={dataSource}
                loading={loading}
                rowKey="id"
                pagination={{
                    showSizeChanger: true,
                    showQuickJumper: true,
                    showTotal: (total) => `共 ${total} 条`,
                }}
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