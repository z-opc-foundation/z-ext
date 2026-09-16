import {Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '@yuku123/z-frontend-common'
import {ApiOutlined, DashboardOutlined, ExperimentOutlined, SettingOutlined} from '@ant-design/icons'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import ExtensionList from './pages/extension/ExtensionList'
import ImplementationList from './pages/implementation/ImplementationList'

// 简单的登录检查
const isAuthenticated = () => {
    return localStorage.getItem('zext_token') || sessionStorage.getItem('zext_token')
}

// 受保护的路由
const PrivateRoute = ({children}) => {
    return isAuthenticated() ? children : <Navigate to="/login" replace/>
}

const menuItems = [
    {key: '/', icon: <DashboardOutlined/>, label: '概览'},
    {key: '/extension/list', icon: <ApiOutlined/>, label: '扩展点管理'},
    {key: '/implementation/list', icon: <ExperimentOutlined/>, label: '实现管理'},
    {key: '/route', icon: <SettingOutlined/>, label: '路由配置'},
]

function App() {
    return (
        <div className="app">
            <Routes>
                <Route path="/login" element={<Login/>}/>
                <Route path="/" element={
                    <PrivateRoute>
                        <AppLayout
                            menuItems={menuItems}
                            appTitle="z-ext 扩展管理"
                            appShort="EXT"
                        />
                    </PrivateRoute>
                }>
                    <Route index element={<Dashboard/>}/>
                    <Route path="dashboard" element={<Dashboard/>}/>
                    <Route path="extension/list" element={<ExtensionList/>}/>
                    <Route path="implementation/list" element={<ImplementationList/>}/>
                    <Route path="route" element={<div>路由配置</div>}/>
                </Route>
            </Routes>
        </div>
    )
}

export default App
