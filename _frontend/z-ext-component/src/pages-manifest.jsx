import { ApiOutlined, DashboardOutlined, ExperimentOutlined, HomeOutlined, SettingOutlined } from '@ant-design/icons'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import ExtensionList from './pages/extension/ExtensionList'
import ImplementationList from './pages/implementation/ImplementationList'

// 简单的登录检查
export const isAuthenticated = () => {
    return localStorage.getItem('zext_token') || sessionStorage.getItem('zext_token')
}

// 受保护的路由
/** 菜单 + 路由清单（lead 005 §8.2 manifest）。App 壳在 suit/宿主侧组装。 */

export {default as Dashboard} from './pages/Dashboard'
export {default as Login} from './pages/Login'
import HomePage from './pages/HomePage'

/** 菜单 + 路由清单（lead 008 §10/§14/§16 批量落地）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-ext 扩展管理', short: 'z-ext' }

export const menuItems = [
    { key: '/z-ext/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-ext/overview', label: '概览', icon: <DashboardOutlined /> },
    { key: '/z-ext/extension/list', label: '扩展点管理', icon: <ApiOutlined /> },
    { key: '/z-ext/implementation/list', label: '实现管理', icon: <ExperimentOutlined /> },
    { key: '/z-ext/route', label: '路由配置', icon: <SettingOutlined /> },
]

export const routes = [
    { path: '/z-ext/home', Component: HomePage },
    { path: '/z-ext/dashboard', Component: Dashboard },
    { path: '/z-ext/extension/list', Component: ExtensionList },
    { path: '/z-ext/implementation/list', Component: ImplementationList },
]

export { default as HomePage } from './pages/HomePage'
export { default as LoginPage } from './pages/LoginPage'
