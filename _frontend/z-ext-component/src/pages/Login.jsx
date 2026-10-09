import {useState} from 'react'
import {Button, Card, Form, Input, message} from 'antd'
import {LockOutlined, UserOutlined} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import request from '../services/request'

const Login = () => {
    const [loading, setLoading] = useState(false)
    const navigate = useNavigate()

    const onFinish = async (values) => {
        setLoading(true)
        try {
            const res = await request.post('/api/auth/login', values)
            localStorage.setItem('zext_token', res.token)
            localStorage.setItem('zext_user', JSON.stringify(res.user))
            message.success('登录成功')
            navigate('/')
        } catch (error) {
            message.error('用户名或密码错误')
        } finally {
            setLoading(false)
        }
    }

    return (
        <div
            style={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                minHeight: '100vh',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            }}
        >
            <Card
                style={{
                    width: 400,
                    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.1)',
                }}
            >
                <div style={{textAlign: 'center', marginBottom: 24}}>
                    <h1 style={{fontSize: 28, fontWeight: 'bold', color: '#1890ff'}}>
                        Z-Ext
                    </h1>
                    <p style={{color: '#666'}}>扩展平台管理后台</p>
                </div>

                <Form
                    name="login"
                    onFinish={onFinish}
                    autoComplete="off"
                    size="large"
                >
                    <Form.Item
                        name="username"
                        rules={[{required: true, message: '请输入用户名'}]}
                    >
                        <Input
                            prefix={<UserOutlined/>}
                            placeholder="用户名: admin"
                        />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[{required: true, message: '请输入密码'}]}
                    >
                        <Input.Password
                            prefix={<LockOutlined/>}
                            placeholder="密码: admin"
                        />
                    </Form.Item>

                    <Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            block
                        >
                            登录
                        </Button>
                    </Form.Item>

                    <div style={{textAlign: 'center', color: '#999', fontSize: 12}}>
                        默认账号: admin / admin
                    </div>
                </Form>
            </Card>
        </div>
    )
}

export default Login