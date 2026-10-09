import {
    ApiOutlined, DashboardOutlined, ExperimentOutlined, SettingOutlined,
} from '@ant-design/icons'
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
export const menuItems = [
    {key: '/', icon: <DashboardOutlined/>, label: '概览'},
    {key: '/extension/list', icon: <ApiOutlined/>, label: '扩展点管理'},
    {key: '/implementation/list', icon: <ExperimentOutlined/>, label: '实现管理'},
    {key: '/route', icon: <SettingOutlined/>, label: '路由配置'},
]

const routeTable = [
    {path: 'dashboard', Component: Dashboard},
    {path: 'extension/list', Component: ExtensionList},
    {path: 'implementation/list', Component: ImplementationList},
]
export {routeTable}
export {default as Dashboard} from './pages/Dashboard'
export {default as Login} from './pages/Login'
