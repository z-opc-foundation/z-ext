import test, {after} from 'node:test'
import assert from 'node:assert/strict'
import {fileURLToPath} from 'node:url'
import {createServer} from 'vite'

const context = {current: null}
globalThis.__extLoginTest = context
const previousWindow = globalThis.window
const previousStorage = globalThis.localStorage
after(() => {
    delete globalThis.__extLoginTest
    if (previousWindow === undefined) delete globalThis.window
    else globalThis.window = previousWindow
    if (previousStorage === undefined) delete globalThis.localStorage
    else globalThis.localStorage = previousStorage
})
const bridge = 'const context = globalThis.__extLoginTest;'
const modules = {
    react: `${bridge}
        export const useState = (...args) => context.current.hooks.useState(...args);
        export const useRef = (...args) => context.current.hooks.useRef(...args);
        export const useEffect = (...args) => context.current.hooks.useEffect(...args);`,
    'react/jsx-runtime': 'export const jsx = (type, props) => ({type, props}); export const jsxs = jsx;',
    'react/jsx-dev-runtime': 'export const jsxDEV = (type, props) => ({type, props});',
    antd: `${bridge}
        export const Button = 'Button', Card = 'Card';
        export const Input = Object.assign(() => null, {Password: 'Password'});
        export const Form = Object.assign(() => null, {Item: 'Form.Item', displayName: 'Form'});
        export const message = {
            success: text => context.current.messages.push(['success', text]),
            error: text => context.current.messages.push(['error', text]),
        };`,
    '@ant-design/icons': 'export const LockOutlined = "LockOutlined", UserOutlined = "UserOutlined";',
    '../services/request': `${bridge} export default {post: (...args) => context.current.post(...args)};`,
}
const server = await createServer({
    root: fileURLToPath(new URL('../../', import.meta.url)),
    configFile: false,
    logLevel: 'silent',
    appType: 'custom',
    server: {middlewareMode: true, hmr: false, watch: null},
    optimizeDeps: {noDiscovery: true, include: []},
    ssr: {noExternal: true},
    esbuild: {jsx: 'automatic'},
    plugins: [{
        name: 'login-test-imports', enforce: 'pre',
        resolveId(id) { if (Object.hasOwn(modules, id)) return `\0mock:${id}` },
        load(id) { if (id.startsWith('\0mock:')) return modules[id.slice(6)] },
    }],
})
let LoginPage
try {
    LoginPage = (await server.ssrLoadModule('/src/pages/LoginPage.jsx')).default
} finally {
    await server.close()
}

function nodes(value, result = []) {
    if (Array.isArray(value)) value.forEach(item => nodes(item, result))
    else if (value?.type && value.props) {
        result.push(value)
        Object.values(value.props).forEach(item => nodes(item, result))
    }
    return result
}
function mount(post) {
    const slots = [], cleanups = [], storage = new Map([['token', 'other-session']])
    let cursor = 0
    const state = {
        post, messages: [],
        hooks: {
            useState(initial) {
                const slot = cursor++
                slots[slot] ??= {value: initial}
                return [slots[slot].value, value => { slots[slot].value = value }]
            },
            useRef(initial) {
                const slot = cursor++
                slots[slot] ??= {current: initial}
                return slots[slot]
            },
            useEffect(effect) {
                const slot = cursor++
                if (!slots[slot]) {
                    slots[slot] = {}
                    cleanups.push(effect())
                }
            },
        },
    }
    globalThis.window = {location: {href: '/z-ext/login'}}
    globalThis.localStorage = {setItem: (key, value) => storage.set(key, value)}
    return {
        ...state, storage,
        form() {
            context.current = state
            cursor = 0
            return nodes(LoginPage()).find(node => node.type.displayName === 'Form').props
        },
        unmount() { cleanups.forEach(cleanup => cleanup?.()) },
    }
}
const values = {username: 'fixture-user', password: 'synthetic-test-only'}
const deferred = () => {
    let resolve, reject
    const promise = new Promise((yes, no) => { resolve = yes; reject = no })
    return {promise, resolve, reject}
}

test('login locks repeated submissions and writes only extension credentials', async () => {
    const response = deferred(), calls = []
    const page = mount((...args) => { calls.push(args); return response.promise })
    const submit = page.form().onFinish
    const pending = submit(values)
    await submit(values)
    assert.equal(calls.length, 1)
    assert.deepEqual(calls[0], ['/auth/login', values])
    assert.equal(page.form().disabled, true)
    response.resolve({token: 'local-session-only', user: {username: values.username}})
    await pending
    assert.equal(page.storage.get('token'), 'other-session')
    assert.equal(page.storage.get('zext_token'), 'local-session-only')
    assert.equal(JSON.parse(page.storage.get('zext_user')).name, values.username)
    assert.equal(window.location.href, '/z-ext/home')
    assert.equal(page.form().disabled, false)
    page.unmount()
})

for (const token of [undefined, '', '   ', 42]) {
    test(`invalid token ${JSON.stringify(token)} cannot establish a session`, async () => {
        const page = mount(async () => ({token}))
        await page.form().onFinish(values)
        assert.equal(page.storage.has('zext_token'), false)
        assert.equal(window.location.href, '/z-ext/login')
        assert.deepEqual(page.messages, [['error', '登录响应缺少有效令牌']])
        assert.equal(page.form().disabled, false)
        page.unmount()
    })
}

test('failed login releases the lock for retry', async () => {
    let calls = 0
    const page = mount(async () => {
        if (++calls === 1) throw new Error('暂不可用')
        return {token: 'local-session-only'}
    })
    const submit = page.form().onFinish
    await submit(values)
    assert.equal(page.form().disabled, false)
    assert.equal(page.storage.has('zext_token'), false)
    await submit(values)
    assert.equal(calls, 2)
    assert.equal(JSON.parse(page.storage.get('zext_user')).name, values.username)
    assert.deepEqual(page.messages.map(([type]) => type), ['error', 'success'])
    page.unmount()
})

for (const fail of [false, true]) {
    test(`unmounted login ignores delayed ${fail ? 'failure' : 'success'}`, async () => {
        const response = deferred(), page = mount(() => response.promise)
        const pending = page.form().onFinish(values)
        page.unmount()
        if (fail) response.reject(new Error('delayed failure'))
        else response.resolve({token: 'local-session-only'})
        await pending
        assert.equal(page.storage.has('zext_token'), false)
        assert.equal(window.location.href, '/z-ext/login')
        assert.deepEqual(page.messages, [])
    })
}
