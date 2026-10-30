import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import {safeEmptyOutDir} from '../../build-tools/safe-vite-plugin.mjs'

// z-ext 组件层双入口（lead 005 §1.2/§9）：'.' 桶导出 + './pages' manifest
export default defineConfig({
    plugins: [safeEmptyOutDir(), react()],
    build: {
        lib: {
            entry: {
                index: path.resolve(__dirname, 'src/index.js'),
                pages: path.resolve(__dirname, 'src/pages.js'),
            },
            formats: ['es'],
        },
        outDir: 'dist',
        emptyOutDir: false,
        rollupOptions: {
            external: ['react', 'react-dom', 'react-dom/client', 'react-router-dom',
                       'antd', '@ant-design/icons', 'axios', '@yuku123/z-frontend-common',
                       '@yuku123/z-frontend-common/ui', '@yuku123/z-frontend-common/ui/echarts'],
        },
    },
})
