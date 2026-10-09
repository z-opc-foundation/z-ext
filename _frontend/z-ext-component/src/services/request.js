/**
 * z-ext 请求实例
 *
 * baseURL: 默认 '/api'（与改造前保持一致）。
 * token key: 'zext_token'（与改造前保持一致，避免清空旧登录态）。
 * userInfo key: 'zext_user'。
 *
 * 配置：
 *   - baseURL '/api'  →  路径走 /api/ext/points、/api/ext/implementations、/api/auth/login
 *   - token key 保留 'zext_token'（与历史兼容）
 *   - userInfo key 保留 'zext_user'
 */
import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({
    baseURL: '/api',
    tokenKey: 'zext_token',
    userInfoKey: 'zext_user',
})

export default request

// 前缀参数注入（lead 005 section 9.3）：默认 '/api'（与原行为一致）。
// 宿主换挂载前缀时 configureExt({ apiBase })，直接改写实例 defaults。
export function configureExt(config) {
    if (config && config.apiBase !== undefined) {
        request.defaults.baseURL = config.apiBase
    }
}
