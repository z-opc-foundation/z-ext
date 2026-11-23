import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import {fileURLToPath} from 'node:url'
import {safeEmptyOutDir} from '../../build-tools/safe-vite-plugin.mjs'

// z-ext 独立运行壳。dev 3003；proxy /api → z-ext server（本地 8888 或后端约定端口）。
export default defineConfig({
    plugins: [safeEmptyOutDir(), react()],
    resolve: {
        dedupe: ['react', 'react-dom', 'react-router-dom', 'antd', '@ant-design/icons', 'axios'],
        alias: process.env.LOCAL_SIBLINGS === '1' ? [
            {find: /^@yuku123\/z-ext-component\/pages$/, replacement: fileURLToPath(new URL('../z-ext-component/src/pages.js', import.meta.url))},
            {find: /^@yuku123\/z-ext-component$/, replacement: fileURLToPath(new URL('../z-ext-component/src/index.js', import.meta.url))},
            {find: /^@yuku123\/z-frontend-common\/ui$/, replacement: fileURLToPath(new URL('../../../z-frontend-common/src/components/ui/index.js', import.meta.url))},
            {find: /^@yuku123\/z-frontend-common$/, replacement: fileURLToPath(new URL('../../../z-frontend-common/src/index.js', import.meta.url))},
        ] : [],
    },
    server: {
        port: 3003,
        fs: {allow: [fileURLToPath(new URL(process.env.LOCAL_SIBLINGS === '1' ? '../../../' : '../', import.meta.url))]},
        proxy: {'/api': {target: 'http://localhost:8888', changeOrigin: true}},
    },
})
