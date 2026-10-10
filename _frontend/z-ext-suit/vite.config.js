import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

// z-ext 独立运行壳。dev 3003；proxy /api → z-ext server（本地 8888 或后端约定端口）。
export default defineConfig({
    plugins: [react()],
  resolve: { dedupe: ['react', 'react-dom', 'react-router-dom', 'antd', '@ant-design/icons', 'axios'] },
    server: {
        port: 3003,
        fs: {allow: ['..']},
        proxy: {'/api': {target: 'http://localhost:8888', changeOrigin: true}},
    },
})
