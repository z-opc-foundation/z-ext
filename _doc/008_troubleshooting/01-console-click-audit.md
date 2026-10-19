# z-ext 控制台：路由页是空壳、请求路径双层 `/api`、登录页写着默认口令

日期 2026-10-01。发现方式：`_frontend` rsync 到仓外副本装依赖、起 vite、Playwright 真点击普查
（量具 `~/.cache/zopc_ui_sweep/sweep/sweep_console.js`，账本 `click_z-ext.jsonl` / `click_z-ext_p2.jsonl`）。
本轮只点不改、不在页面里打字；非 GET 请求在浏览器层 `abort` 并记账。

## 0. 怎么把闸内页点开的（不涉及真凭证）

`src/App.jsx:11` 的登录判据只是 `localStorage.getItem('zext_token') || sessionStorage.getItem('zext_token')`，
`App.jsx:16` 拿到真值就放行。普查量具本来就会往 localStorage 写哨兵字面量
（`SENTINEL_NOT_A_CREDENTIAL`，不是任何人的口令），但键名列表原本是写死的四个通用名，
不含本模块的 `zext_token` ⇒ 第一遍 5 条路由全被 `<Navigate to="/login">` 弹回登录页，只点到 2 条。
补上这个键名之后：**5 条路由 / 21 次点击**（第一遍 2 条 / 7 次）。

这条只是取证通道的说明，不等于"绕过鉴权"：闸在服务端，客户端哨兵拿不到任何真数据。

## 1. 实测读数

| 路由 | 控件 | 表格行 | 正文起首（逐字） | 失败请求 |
|---|---|---|---|---|
| `/login` | 2 | 0 | `默认账号: admin / admin` | 0 |
| `/`（概览） | 5 | 1 | `扩展点数量 0 实现数量 0 激活实现 0 切换次数 0 最近活动 … No data` | 4（全 500） |
| `/extension/list` | 5 | 1 | `新建扩展点 扩展点标识 … No data 获取扩展点列表失败` | 2（全 500） |
| `/implementation/list` | 5 | 1 | `新建实现 扩展点 … 获取实现列表失败` | 4（全 500） |
| `/route` | 4 | 0 | `z-ext 扩展管理 概览 扩展点管理 实现管理 路由配置 路由配置` | 0 |

21 次点击里弹出 toast：`获取扩展点列表失败` ×15、`获取实现列表失败` ×15。

## 2. 三条独立缺陷

### 缺陷 A：`/api` 前缀被拼了两次，即使后端补齐这四个接口也照样打不通

- `src/utils/request.js:11` `baseURL: import.meta.env.VITE_API_BASE_URL || '/api'`
- 调用方却把完整路径又写了一遍：
  `src/pages/extension/ExtensionList.jsx:85,127,130,146`、`src/pages/Dashboard.jsx:24`、
  `src/pages/implementation/ImplementationList.jsx:113` 全部是 `request.get('/api/ext/points')` 这种形状。

⇒ 浏览器实际请求 `GET /api/api/ext/points`（本轮账本里 10 条失败请求逐字如此，无一例外）。
`vite.config.js` 把 `/api` 代理到 `http://localhost:8080`，转发出去的还是 `/api/api/ext/points`。
而 README 登记的契约是 `/api/ext/points`（见缺陷 C）——也就是说**这条路径永远对不上**，
"后端没起"只是把它挡在了第一个 500 上，掩盖了第二个必然的 404。

### 缺陷 B：`/route`「路由配置」是路由表里的一个空 `div`，但它在主菜单里当作一个功能挂着

`src/App.jsx:44`：

```jsx
<Route path="route" element={<div>路由配置</div>}/>
```

菜单项"路由配置"（`App.jsx` 同一份 `menuItems`）点开就是这四个字：无控件、无请求、无占位提示、无"未实现"字样。
实测该页 `textLen=34`，正文逐字是 `z-ext 扩展管理 概览 扩展点管理 实现管理 路由配置 路由配置`
——"路由配置"出现两遍：第一遍是侧栏菜单项，第二遍就是那个 `div` 本身，中间没有任何别的内容。
同页 0 次网络请求 ⇒ 这一格和"接口 500"是两类问题：500 那一族至少说明"这里该有数据"，
`/route` 是结构性空壳。

### 缺陷 C：登录页把口令写在页面上，而本仓 README 自己承认这些接口没实现

`src/pages/Login.jsx:87` 渲染字面量 `默认账号: admin / admin`。
而 `README.md:202-203` 逐字登记着：

```
| `/api/ext/points` | GET / POST | 本仓未实现 |
| `/api/ext/points/{id}` | PUT / DELETE | 本仓未实现 |
```

两件事叠在一起：前台向任何路过的人播报"admin/admin 能进门"，后台却把扩展点/实现的读写接口登记为未实现。
若"默认账号"只是历史脚手架残留，应该删掉而不是留在生产可见的登录页上；
若它真能在某个部署里进门，那它现在是一条公开的弱口令线索。这一条我按"待你判定属于哪种"记录，没有去试登录
（试过就得在页面里打字，而登录请求会经代理打到真后端，不在本轮"只点不改"的授权射程内）。

## 3. 附带读数（不是缺陷，但影响下一轮判断）

- 本轮 `blockedNonGET = 0`：21 次点击里没有一次发出非 GET。原因是列表全空 ⇒ 行内的
  编辑/删除按钮结构上不存在（`No data`），"新建扩展点/新建实现"两个对话框我打开了也没提交。
  ⇒ **防火墙在 z-ext 这一格尚未被验证过**（不像 z-graph 那一格实测拦下 6 条非 GET）。
  下一轮要验它，得先让列表有行，而那需要真后端（见 §2 缺陷 A/C）。
- 500 的来源要说清：`lsof -nP -iTCP:8080 -sTCP:LISTEN` 空 ⇒ 是 vite 代理打不到上游，
  不是 z-ext 后端返回 500。

## 4. 建议（未动代码，等拍板）

1. 二选一改法：`request.js:11` 的 baseURL 去掉 `/api`，或 6 处调用点去掉自己写的 `/api` 前缀
   （改调用点更保险——baseURL 还兼任别的路径拼接）。改完加一条尺：账本里出现 `/api/api/` 即红。
2. `/route` 要么接真功能，要么把菜单项一起摘掉；不要留"点进去只有四个字"的门面。
3. `Login.jsx:87` 的 `默认账号: admin / admin` 删除；口令是否真能用需要单独裁定。
4. README `202-203` 那两行"本仓未实现"与前端已上线的三张表页面互相矛盾，建议明确一处：
   要么 README 改口（接口在别的仓实现），要么前端这三张卡标成"未接入"。

## 5. 复跑

```
cd ~/.cache/zopc_ui_sweep/sweep && python3 -u console_click_probe.py z-ext
```

副本在 `~/.cache/zopc_ui_sweep/consoles/z-ext___frontend`（刻意装在仓库外：本仓 `.gitignore`
不覆盖 `node_modules`，原地装会在共享工作树留下上万条未跟踪文件）。
