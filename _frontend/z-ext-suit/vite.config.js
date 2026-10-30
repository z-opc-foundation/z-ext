import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import {safeEmptyOutDir} from '../../build-tools/safe-vite-plugin.mjs'

// z-ext 独立运行壳。dev 3003；proxy /api → z-ext server（本地 8888 或后端约定端口）。
export default defineConfig({
    plugins: [safeEmptyOutDir(), react()],
  resolve: { dedupe: ['react', 'react-dom', 'react-router-dom', 'antd', '@ant-design/icons', 'axios'] ,
        alias: process.env.LOCAL_SIBLINGS === '1' ? { '@yuku123/z-ext-component': '../z-ext-component/src' } : {}},
    server: {
        port: 3003,
        fs: {allow: ['..']},
        proxy: {'/api': {target: 'http://localhost:8888', changeOrigin: true}},
    },
})
