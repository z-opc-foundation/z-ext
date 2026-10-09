import React from 'react'
import ReactDOM from 'react-dom/client'
import {BrowserRouter} from 'react-router-dom'
import {ConfigProvider} from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import './index.css'

// suit 只做壳（lead 005 §9.1）：入口组装。默认 apiBase='/api'（component request
// 已设默认，与原行为一致）。
ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <ConfigProvider locale={zhCN}>
            <BrowserRouter>
                <App/>
            </BrowserRouter>
        </ConfigProvider>
    </React.StrictMode>,
)
