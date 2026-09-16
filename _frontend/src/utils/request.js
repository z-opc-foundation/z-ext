/**
 * z-ext 请求实例
 *
 * baseURL: VITE_API_BASE_URL 环境变量，缺省 '/api'（与改造前保持一致）。
 * token key: 'zext_token'（与改造前保持一致，避免清空旧登录态）。
 * userInfo key: 'zext_user'。
 */
import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    tokenKey: 'zext_token',
    userInfoKey: 'zext_user',
})

export default request
