# z-ext-component

z-ext（扩展点管理）跨仓唯一前端面：把「概览 / 扩展点管理 / 实现管理」这一组扩展点注册视图
收敛成一个可复用的 React 组件包，由 `z-ext-suit` 独立壳或 z-opc 主壳挂载。

- **npm 包名**：`@yuku123/z-ext-component`
- **宿主接入**：`@yuku123/z-ext-component/pages`（manifest + 请求层），主壳可只取 `ComponentDocPage`
- **后端**：**z-ext-core 本身没有 HTTP API**（全仓 0 个 `@Controller` / `@RequestMapping`），
  页面打的 `/api/ext/**` 由**宿主应用**实现
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）

---

## 一、能力概览

路由前缀 `/z-ext`，菜单与路由都在 `src/pages-manifest.jsx` 里声明，宿主读数组自己组装。

| 页面 | 路由（相对 `/z-ext/*`） | 源文件 | 能力 |
|------|--------------------------|--------|------|
| 首页 | `home` | `pages/HomePage.jsx` | 读 `userInfo` 显示登录人，按 `menuItems` 渲染功能入口卡片 |
| 概览 | `dashboard` | `pages/Dashboard.jsx` | 扩展点数 / 实现数 / 激活实现 / 切换次数 + 最近活动 |
| 扩展点管理 | `extension/list` | `pages/extension/ExtensionList.jsx` | 扩展点 CRUD + 切换实现 |
| 实现管理 | `implementation/list` | `pages/implementation/ImplementationList.jsx` | 实现 CRUD + 启用开关 + 权重 |
| 登录 | （不在菜单内） | `pages/Login.jsx`（真实登录）/ `pages/LoginPage.jsx`（mock） | 见「已知限制」 |
| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |

包里还有一个可复用的小组件：`extension/ExtensionPointTag.jsx`（扩展点 / 实现的类型标签，
主入口具名导出 `ExtensionPointTag`）。

## 二、导出面

```js
// 主入口（. 桶）：只有标签组件
import {ExtensionPointTag} from '@yuku123/z-ext-component'

// 宿主组装面（./pages 桶）：manifest + 页面组件 + 请求层注入
import {menuItems, routes, appMeta, HomePage, LoginPage, Login, Dashboard,
        ExtensionList, ImplementationList, isAuthenticated,
        configureExt, ComponentDocPage} from '@yuku123/z-ext-component/pages'

// 组件文档页（主壳接线用，名字全仓统一）
import {ComponentDocPage} from '@yuku123/z-ext-component'
```

⚠ `./pages` 桶是 `export * from './pages-manifest.jsx'` 的整份转发，**不要手挑名字** ——
这里原先写死了 `menuItems / routes / isAuthenticated / Dashboard / Login`，
而 manifest 还导出 `HomePage` / `LoginPage`，`z-ext-suit/src/App.jsx` 正好 import 这两个，
于是 rollup 报 `LoginPage is not exported by dist/pages.js`。

## 三、API 端点

请求实例由基座 `createRequest` 工厂创建（`src/services/request.js`），
**`baseURL` 默认 `/api`**，token 走 `Authorization: Bearer`，
token 存 `localStorage.zext_token`，用户信息存 `localStorage.zext_user`。

下表路径是页面里**实际调用**的路径（页面写的是绝对路径 `/api/ext/**`，
与实例的 `baseURL: '/api'` 叠加后浏览器实际打到 `/api/api/ext/**` —— 见「已知限制」）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ext/points` | 扩展点列表（概览页与扩展点页共用） |
| POST | `/api/ext/points` | 新建扩展点 |
| PUT | `/api/ext/points/{id}` | 更新扩展点 |
| DELETE | `/api/ext/points/{id}` | 删除扩展点（函数已写，**页面上没有入口**） |
| GET | `/api/ext/implementations` | 实现列表（概览页与实现页共用） |
| POST | `/api/ext/implementations` | 新建实现 |
| PUT | `/api/ext/implementations/{id}` | 更新实现（也用于启用 / 禁用开关） |
| POST | `/api/auth/login` | 登录（`pages/Login.jsx`） |

## 四、页面说明

### 4.1 首页

登录后的着陆页。读 `localStorage.userInfo` 显示欢迎语与角色标签，再把 `menuItems`（去掉首页自身）
渲染成入口卡片 —— 菜单加一项，首页卡片自动多一张，不用改页面代码。

### 4.2 概览

四张统计卡（扩展点数量 / 实现数量 / 激活实现 / 切换次数）+ 「最近活动」表。
统计值由前端数 `/api/ext/points` 与 `/api/ext/implementations` 两个列表的长度算出来。

### 4.3 扩展点管理

扩展点列表 + 新建 / 编辑弹窗。列：扩展点标识、接口类、类型（`SYNC` / `ASYNC` / `CHAIN`，
用 `ExtensionPointTag` 上色）、描述、版本、实现数量。
行操作：切换实现（弹确认框）、编辑。

表单字段：扩展点标识 / 接口类 / 扩展点类型 / 描述 / 版本。

### 4.4 实现管理

实现列表 + 新建 / 编辑弹窗。列：扩展点、实现名称、类型（`PLATFORM` / `EXTERNAL` / `CUSTOM`，
各配一个图标）、实现类、RPC 地址（拼 `rpcAddress:rpcPort`）、启用开关、权重。
表单字段：所属扩展点（下拉选项来自 `/api/ext/points`）/ 实现名称 / 实现类型 / 实现类 /
RPC 地址 / RPC 端口 / 路由条件（SpEL）/ 权重 / 是否启用 / 描述。

### 4.5 登录

包里有**两个**登录页：

- `pages/Login.jsx`：真登录，`POST /api/auth/login`，成功后写 token 并跳转；
- `pages/LoginPage.jsx`：mock 登录（比对写死的管理员凭据），CTC/SSO 接入前的临时形态。

`./pages` 桶两个都导出（`Login` 与 `LoginPage`）。独立壳 `z-ext-suit` 用的是 **mock** 的
`LoginPage`。

### 4.6 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `configureExt({apiBase})` | `'/api'` | 改写请求实例的 `baseURL`，用于宿主换挂载前缀 |
| token 存储键 | `zext_token` | `createRequest` 的 `tokenKey`（与历史实现兼容，避免清掉旧登录态） |
| userInfo 存储键 | `zext_user` | `createRequest` 的 `userInfoKey` |
| `isAuthenticated()` | — | 读 localStorage / sessionStorage 的 `zext_token` |

## 六、已知限制

- ⚠ **z-ext 后端没有 HTTP API**：全仓 0 个 `@Controller` / `@RequestMapping`，
  `z-ext-web` 只有一个打印 `Hello and welcome!` 的 `Main`。
  页面打的 `/api/ext/**` 与 `/api/auth/login` **要由宿主应用实现**，z-ext 只提供它们背后的
  注册表数据。⇒ 独立跑这个包时列表一定是空的（接口 404）。
- ⚠ **请求路径被拼了两遍**：请求实例 `baseURL` 是 `'/api'`，而页面里写的是绝对路径
  `/api/ext/points` ⇒ axios 拼出来是 **`/api/api/ext/points`**。
  宿主要么把 `baseURL` 配成 `''`，要么让页面改用相对路径 —— 两边必须统一。
- **菜单与路由对不上（两处）**：
  - 菜单「概览」的 key 是 `/z-ext/overview`，但对应路由是 `/z-ext/dashboard` ⇒ 点「概览」会 404；
  - 菜单「路由配置」的 key 是 `/z-ext/route`，**没有任何路由对应它** ⇒ 点它必 404。
- **「切换实现」是空动作**：`ExtensionList` 的 `handleSwitch` 弹一个确认框，
  `onOk` 里只 `message.success('切换成功')` + 重新拉列表，**没有调任何后端接口**。
- **概览页的「最近活动」是假数据**：`Dashboard` 里三条活动是写死在代码里的常量，
  不来自后端。「切换次数」统计也恒为 0（没有这个接口）。
- **删除扩展点没有入口**：`handleDelete` 函数已写好（`DELETE /api/ext/points/{id}`），
  但表里没有删除按钮。
- **两个登录页并存**：`Login`（真登录）与 `LoginPage`（mock）都在 `./pages` 桶里，
  宿主要哪个自己挑；混用会出现「token 键不一致」（`Login.jsx` 写 `token`，
  而请求实例读 `zext_token`）。
- 静态 `message` / `Modal.confirm`（antd 6 下静态 `message` 会告警，规范要求改 `App.useApp()`）。
- z-ext 已被 z-opc 主壳的 `domainRoutes.ts` 引用 ⇒ `/z-ext/docs` 在平台主壳里**可达**。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 0.1.1 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |
| 0.1.1 | 首版：概览 / 扩展点管理 / 实现管理页面集 + manifest + `ExtensionPointTag` |
