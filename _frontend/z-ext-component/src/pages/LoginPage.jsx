import { useEffect, useRef, useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import request from '../services/request'

/**
 * 登录页 —— 走 z-ext-admin 控制台的真实会话（POST /api/auth/login，Bearer 令牌）。
 *
 * 控制台凭据不落仓：部署侧用环境变量 ZEXT_ADMIN_USERNAME / ZEXT_ADMIN_PASSWORD 注入，
 * 未配置时后端对任何登录返回 503（页面如实提示，不做本地 mock —— mock 口令禁令口径）。
 */
export default function LoginPage() {
    const [loading, setLoading] = useState(false)
    const submitting = useRef(false)
    const active = useRef(true)

    useEffect(() => {
        active.current = true
        return () => { active.current = false }
    }, [])

    const handleSubmit = async (values) => {
        if (submitting.current) return
        submitting.current = true
        setLoading(true)
        try {
            const res = await request.post('/auth/login', {
                username: values.username,
                password: values.password,
            })
            // 后端返回裸 {token, user:{username}}（无包装），拦截器原样透传；
            // AppLayout 读 user.name，归一化成 {name}
            if (!active.current) return
            if (typeof res?.token !== 'string' || !res.token.trim()) {
                throw new Error('登录响应缺少有效令牌')
            }
            const user = res.user || {}
            localStorage.setItem('zext_token', res.token)
            localStorage.setItem('zext_user', JSON.stringify(
                { ...user, name: user.name || user.username || values.username }))
            message.success('登录成功')
            window.location.href = '/z-ext/home'
        } catch (e) {
            const msg = e?.response?.data?.message || e?.message || '登录失败'
            if (active.current) message.error(msg)
        } finally {
            submitting.current = false
            if (active.current) setLoading(false)
        }
    }

    return (
        <div style={{
            minHeight: '100dvh', padding: 16, boxSizing: 'border-box', display: 'flex', justifyContent: 'center', alignItems: 'center',
            background: 'linear-gradient(135deg, #eef2ff 0%, #f5f3ff 50%, #eff6ff 100%)',
        }}>
            <Card style={{ width: '100%', maxWidth: 400, boxShadow: '0 10px 40px rgba(124,58,237,0.12)' }}>
                <div style={{ textAlign: 'center', marginBottom: 20 }}>
                    <div style={{
                        width: 48, height: 48, borderRadius: 12, margin: '0 auto 12px',
                        background: 'linear-gradient(135deg, #7c3aed, #6d28d9)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: '#fff', fontSize: 24, fontWeight: 700,
                    }}>z</div>
                    <div style={{ fontSize: 18, fontWeight: 600, color: '#0f172a' }}>z-ext 管理台</div>
                </div>
                <Form onFinish={handleSubmit} size="large" disabled={loading}>
                    <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
                        <Input prefix={<UserOutlined />} placeholder="用户名" />
                    </Form.Item>
                    <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                        <Input.Password prefix={<LockOutlined />} placeholder="密码" />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 8 }}>
                        <Button type="primary" htmlType="submit" loading={loading} block
                                style={{ background: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)', border: 'none' }}>
                            登 录
                        </Button>
                    </Form.Item>
                </Form>
            </Card>
        </div>
    )
}
