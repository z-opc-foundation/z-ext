import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space, Switch, Table, Tag} from 'antd'
import {ApiOutlined, CloudServerOutlined, EditOutlined, LaptopOutlined, PlusOutlined} from '@ant-design/icons'
import request from '../../utils/request'

const {Option} = Select
const {TextArea} = Input

const ImplementationList = () => {
    const [dataSource, setDataSource] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalVisible, setModalVisible] = useState(false)
    const [editingRecord, setEditingRecord] = useState(null)
    const [form] = Form.useForm()
    const [pointOptions, setPointOptions] = useState([])

    const typeIcons = {
        PLATFORM: <LaptopOutlined style={{color: '#1890ff'}}/>,
        EXTERNAL: <CloudServerOutlined style={{color: '#52c41a'}}/>,
        CUSTOM: <ApiOutlined style={{color: '#faad14'}}/>,
    }

    const columns = [
        {
            title: '扩展点',
            dataIndex: 'point',
            key: 'point',
            width: 180,
        },
        {
            title: '实现名称',
            dataIndex: 'name',
            key: 'name',
            width: 150,
        },
        {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            width: 120,
            render: (type) => (
                <Tag icon={typeIcons[type]}>
                    {type}
                </Tag>
            ),
        },
        {
            title: '实现类',
            dataIndex: 'implClass',
            key: 'implClass',
            width: 250,
            ellipsis: true,
        },
        {
            title: 'RPC地址',
            dataIndex: 'rpcAddress',
            key: 'rpcAddress',
            width: 150,
            render: (addr, record) => addr ? `${addr}:${record.rpcPort}` : '-',
        },
        {
            title: '状态',
            dataIndex: 'enabled',
            key: 'enabled',
            width: 80,
            render: (enabled, record) => (
                <Switch
                    checked={enabled}
                    onChange={(checked) => handleToggleEnabled(record, checked)}
                    checkedChildren="启用"
                    unCheckedChildren="禁用"
                />
            ),
        },
        {
            title: '权重',
            dataIndex: 'weight',
            key: 'weight',
            width: 80,
        },
        {
            title: '操作',
            key: 'action',
            width: 150,
            render: (_, record) => (
                <Space>
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
            const res = await request.get('/api/ext/implementations')
            setDataSource(res.data || [])
        } catch (error) {
            message.error('获取实现列表失败')
        } finally {
            setLoading(false)
        }
    }

    const fetchPointOptions = async () => {
        try {
            const res = await request.get('/api/ext/points')
            setPointOptions(res.data || [])
        } catch (error) {
            console.error('获取扩展点失败', error)
        }
    }

    useEffect(() => {
        fetchData()
        fetchPointOptions()
    }, [])

    const handleAdd = () => {
        setEditingRecord(null)
        form.resetFields()
        form.setFieldsValue({enabled: true, weight: 100})
        setModalVisible(true)
    }

    const handleEdit = (record) => {
        setEditingRecord(record)
        form.setFieldsValue(record)
        setModalVisible(true)
    }

    const handleToggleEnabled = async (record, checked) => {
        try {
            await request.put(`/api/ext/implementations/${record.id}`, {
                ...record,
                enabled: checked,
            })
            message.success(checked ? '已启用' : '已禁用')
            fetchData()
        } catch (error) {
            message.error('操作失败')
        }
    }

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields()
            if (editingRecord) {
                await request.put(`/api/ext/implementations/${editingRecord.id}`, values)
                message.success('更新成功')
            } else {
                await request.post('/api/ext/implementations', values)
                message.success('创建成功')
            }
            setModalVisible(false)
            fetchData()
        } catch (error) {
            message.error('操作失败')
        }
    }

    return (
        <div>
            <div style={{marginBottom: 16}}>
                <Button
                    type="primary"
                    icon={<PlusOutlined/>}
                    onClick={handleAdd}
                >
                    新建实现
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
                title={editingRecord ? '编辑实现' : '新建实现'}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                width={700}
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="point"
                        label="所属扩展点"
                        rules={[{required: true, message: '请选择扩展点'}]}
                    >
                        <Select placeholder="请选择扩展点">
                            {pointOptions.map(p => (
                                <Option key={p.point} value={p.point}>
                                    {p.point}
                                </Option>
                            ))}
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="name"
                        label="实现名称"
                        rules={[{required: true, message: '请输入实现名称'}]}
                    >
                        <Input placeholder="例如: default, alipay, custom"/>
                    </Form.Item>

                    <Form.Item
                        name="type"
                        label="实现类型"
                        rules={[{required: true, message: '请选择实现类型'}]}
                    >
                        <Select placeholder="请选择实现类型">
                            <Option value="PLATFORM">PLATFORM - 平台实现</Option>
                            <Option value="EXTERNAL">EXTERNAL - 外部实现</Option>
                            <Option value="CUSTOM">CUSTOM - 自定义实现</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="implClass"
                        label="实现类"
                        rules={[{required: true, message: '请输入实现类全限定名'}]}
                    >
                        <Input placeholder="例如: com.example.impl.DefaultOrderService"/>
                    </Form.Item>

                    <Form.Item
                        name="rpcAddress"
                        label="RPC地址"
                        tooltip="仅外部实现需要配置"
                    >
                        <Input placeholder="例如: 192.168.1.100"/>
                    </Form.Item>

                    <Form.Item
                        name="rpcPort"
                        label="RPC端口"
                        tooltip="仅外部实现需要配置"
                    >
                        <Input type="number" placeholder="8080"/>
                    </Form.Item>

                    <Form.Item
                        name="condition"
                        label="路由条件"
                        tooltip="支持SpEL表达式，如: #{environment.getProperty('app.env') == 'prod'}"
                    >
                        <TextArea rows={2} placeholder="可选的路由条件表达式"/>
                    </Form.Item>

                    <Form.Item
                        name="weight"
                        label="权重"
                    >
                        <Input type="number" placeholder="100"/>
                    </Form.Item>

                    <Form.Item
                        name="enabled"
                        label="是否启用"
                        valuePropName="checked"
                    >
                        <Switch checkedChildren="启用" unCheckedChildren="禁用"/>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <TextArea rows={2}/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}

export default ImplementationList