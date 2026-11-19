import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space} from 'antd'
import {CloudServerOutlined, DeleteOutlined, PlusOutlined} from '@ant-design/icons'
import request from '../services/request'

/**
 * 实例聚合（Agent 模式）的页面级入口。
 *
 * 控制台是纯转发面：本机控制面 = /api/ext/**；已注册业务实例 = /api/ext/instances/{name}/**。
 * 选中实例后，extPath(prefix) 返回转发前缀，列表页全部经由它拼请求路径。
 * 当前选中实例存 localStorage 'zext_instance'，空串 = 本机。
 */

const INSTANCE_KEY = 'zext_instance'

export function currentInstance() {
    return localStorage.getItem(INSTANCE_KEY) || ''
}

export function setCurrentInstance(name) {
    if (name) {
        localStorage.setItem(INSTANCE_KEY, name)
    } else {
        localStorage.removeItem(INSTANCE_KEY)
    }
}

/** 按当前选中实例拼扩展点控制面路径；空实例 = 本机嵌入式控制面。
 *  实例代理面的段位是 /ext/instances/{name}/points（不重复 /ext），故剥掉前缀里的 /ext 段。 */
export function extPath(prefix) {
    const inst = currentInstance()
    if (!inst) return prefix
    return `/ext/instances/${encodeURIComponent(inst)}${prefix.replace(/^\/ext/, '')}`
}

/** 统一解包：共享拦截器已把 {code,data,message} 解成 data；裸数组/旧口径兜底。 */
export function unwrapList(res) {
    if (Array.isArray(res)) return res
    if (res && Array.isArray(res.data)) return res.data
    return []
}

const InstanceSelect = ({onChange}) => {
    const [instances, setInstances] = useState([])
    const [value, setValue] = useState(currentInstance())
    const [modalOpen, setModalOpen] = useState(false)
    const [form] = Form.useForm()

    const fetchInstances = async () => {
        try {
            const res = await request.get('/ext/instances')
            setInstances(unwrapList(res))
        } catch (e) {
            console.error('获取实例列表失败', e)
        }
    }

    useEffect(() => {
        fetchInstances()
    }, [])

    const handleChange = (name) => {
        setValue(name || '')
        setCurrentInstance(name)
        if (onChange) onChange(name)
    }

    const handleRegister = async () => {
        try {
            const values = await form.validateFields()
            await request.post('/ext/instances', values)
            message.success('实例已登记')
            setModalOpen(false)
            form.resetFields()
            fetchInstances()
        } catch (e) {
            message.error(e?.response?.data?.message || e?.message || '登记失败')
        }
    }

    const handleDelete = (name) => {
        Modal.confirm({
            title: '移除实例',
            content: `从控制台移除实例 ${name}（不影响实例本身）？`,
            onOk: async () => {
                try {
                    await request.delete(`/ext/instances/${name}`)
                    if (currentInstance() === name) handleChange('')
                    fetchInstances()
                } catch (e) {
                    message.error('移除失败')
                }
            },
        })
    }

    return (
        <Space.Compact>
            <Select
                style={{minWidth: 200}}
                value={value || ''}
                onChange={handleChange}
                placeholder="选择目标实例"
            >
                <Select.Option value="">本机控制面</Select.Option>
                {instances.map((it) => (
                    <Select.Option key={it.name} value={it.name}>
                        <CloudServerOutlined/> {it.name}
                    </Select.Option>
                ))}
            </Select>
            <Button icon={<PlusOutlined/>} onClick={() => setModalOpen(true)}/>
            <Button icon={<DeleteOutlined/>} disabled={!value}
                    onClick={() => value && handleDelete(value)}/>
            <Modal
                title="登记业务实例"
                open={modalOpen}
                onOk={handleRegister}
                onCancel={() => setModalOpen(false)}
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="name" label="实例名"
                               rules={[{required: true, pattern: /^[a-zA-Z0-9_-]{1,64}$/,
                                        message: '字母数字下划线中划线，1-64 位'}]}>
                        <Input placeholder="例如: z-biz-web"/>
                    </Form.Item>
                    <Form.Item name="baseUrl" label="实例地址"
                               rules={[{required: true, message: '请输入 http(s) 地址'}]}>
                        <Input placeholder="例如: http://z-biz-web:8080"/>
                    </Form.Item>
                </Form>
            </Modal>
        </Space.Compact>
    )
}

export { InstanceSelect }
export default InstanceSelect
