# z-ext

> Java 扩展点框架（Extension Point SDK）—— `@ExtPoint` 标记能力边界，`@ExtImpl` 登记实现，JDK 动态代理在运行时选一个实现来调用

它解决的问题是"同一套接口，多种实现，运行期决定用哪个"：平台方只发布接口和一个默认（PLATFORM）实现，业务方可以注册 CUSTOM 实现顶掉它，三方服务可以登记为 EXTERNAL 实现、由框架走 z-rpc 远程调用；切换靠注册表里的"当前激活实现"，不需要重新编译、不需要 if-else 分支。

需要说清楚的是：**这是一个进程内的扩展点路由框架，不是插件容器**。本仓代码里没有 ClassLoader 隔离、没有 ServiceLoader/SPI 插件发现、没有 jar 上传/安装/卸载（细节见「已知边界」）。`_doc/` 里的产品方案写了这些，那是设计意图，不是现状。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-ext`（origin `git@github.com:z-opc-foundation/z-ext.git`，分支 `main`） |
| **Maven 坐标** | `io.github.yuku123:z-ext`（聚合 POM）· `io.github.yuku123:z-ext-core`（唯一有 jar 的构件） |
| **当前版本** | `1.0.0` 写在根 POM `<version>`，两个子模块 `<parent>` 逐字重复同一版本号——本仓**没有**用 `<revision>` 占位（尽管继承了带 CI-friendly 约定的 `z-boot-parent`） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.0.21`，`<relativePath/>` 留空（parent 在 repo1，不在磁盘）；父链地板 `z-boot-dependencies:1.0.20` + 兄弟仓权威表 `z-boot-fleet:1.0.1`，三件 repo1 均可读（200） |
| **Maven Central** | 已发布（2026-09-30，`maven-metadata` latest=release=`1.0.0`）：`z-ext:1.0.0` pom 200；`z-ext-core:1.0.0` pom/jar/-sources/-javadoc/.asc 全 200。404 未发布：`z-ext-web:1.0.0`（被 `central` profile 的 `excludeArtifacts` 点名排除）、`z-ext-frontend`（不在 reactor）、旧 namespace `com.zifang:z-ext` / `com.zifang:z-ext-core` |
| **默认端口** | 无 —— 本仓不启 HTTP 服务（实测：`application*.yml` 0 个、`@RestController`/`@RequestMapping` 0 处）。前端 dev server 监听 `3000`，把 `/api` 代理到 `http://localhost:8080`（由宿主应用提供） |
| **运行口径** | Java 8（parent 下发 `maven.compiler.source/target=8`）· Spring Framework 5.3.39（`z-boot-dependencies` 导入 Boot BOM 2.7.18 口径）；`z-ext-core` 只依赖 spring-context/beans/core/expression，**不依赖 Spring Boot** |
| **许可证** | MIT（根 [`LICENSE`](LICENSE) 为 MIT 全文，根 POM `<licenses>` 声明 MIT License） |
| **最近更新** | 2026-09-30 |

---

## 🎯 能力清单（逐条对得上类）

全部实现都在 [`z-ext-core/src/main/java/com/zifang/z/ext/`](z-ext-core/src/main/java/com/zifang/z/ext/)（19 个 main 类），没有一行在 web/admin 里。

| 能力 | 实现类 | 实测行为 |
|------|--------|----------|
| 扩展点声明 | [`ExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtPoint.java) · [`ExtType`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtType.java) | 标在接口上：`value()` 是 point 标识，另有 `description` / `version`（默认 `"1.0.0"`）/ `order` / `type` ∈ SYNC · ASYNC · CHAIN |
| 实现声明 | [`ExtImpl`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtImpl.java) · [`ExtImplType`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtImplType.java) | `point` + `name` + `type` ∈ PLATFORM · EXTERNAL · CUSTOM，另有 `order` / `enabled` / `condition` / `weight` |
| 注解扫描装配 | [`EnableExt`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/EnableExt.java) → [`ExtRegistrar`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtRegistrar.java) → [`ExtScanner`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtScanner.java) | `@Import(ExtRegistrar.class)`；Registrar 以 `ImportBeanDefinitionRegistrar` 注册名为 `extScanner` 的 BeanDefinition，Scanner 作 `BeanFactoryPostProcessor` 用 `PathMatchingResourcePatternResolver` + `CachingMetadataReaderFactory` 扫 `basePackages/**/*.class`。**这条链路目前不可用，见「已知边界」第 1 条** |
| 注册中心 | [`ExtRegistry`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtRegistry.java) | 四张 JVM 级静态表：`EXT_POINT_REGISTRY` / `EXT_IMPL_REGISTRY` / `ACTIVE_IMPL`（均 `ConcurrentHashMap`）+ `LISTENERS`（`CopyOnWriteArrayList`）。`getImplementations` 无实现时返回空列表而非 null；`getEnabledImplementations` 过滤 `enabled` 并按 `order` 升序 |
| 定义模型 | [`ExtPointDefinition`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtPointDefinition.java) · [`ExtImplDefinition`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtImplDefinition.java) | Point 持 `interfaceClass` + 实现列表；Impl 持 `implClass` / `instance` / `rpcAddress` / `rpcPort`（远程实现的路由地址就挂在定义上） |
| 按接口类反查 | `ExtRegistry.getPointByInterface(Class)` | point 标识是自由文本，调用方常只握有接口 Class；遍历比对 `interfaceClass`，找不到返回 null |
| 路由策略 | [`ExtRouter`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/ExtRouter.java) · [`DefaultExtRouter`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/DefaultExtRouter.java) · [`ExtRouterContext`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/ExtRouterContext.java) | `route(pointDef, impls, context)` 可整体替换（`ExtInvocationHandler#setRouter`）。默认：CHAIN 返回全部候选；SYNC/ASYNC 只返回一个 —— 先取"激活实现"（显式 `setActiveImpl`，否则回退注册中心的 PLATFORM 默认实现），且要求它 `enabled` 并以**同一对象身份**出现在候选里；否则按 `EXTERNAL > CUSTOM > PLATFORM` 取第一个启用项 |
| 动态代理 | [`ExtProxyFactory`](z-ext-core/src/main/java/com/zifang/z/ext/core/proxy/ExtProxyFactory.java) · [`ExtInvocationHandler`](z-ext-core/src/main/java/com/zifang/z/ext/core/proxy/ExtInvocationHandler.java) | 单接口 `Proxy.newProxyInstance`。`createProxy` 三个重载：传 `ExtPointDefinition` / 传 point 字符串 / 传接口类 + 自定义 handler。point 字符串既接受标识（`"order.service"`），也接受类型描述符（`"interface com.x.OrderService"`，即 `Class.toString()` 形态，自动剥前缀后按接口类反查），两者都查不到抛 `IllegalArgumentException`。`Object` 方法被短路：`toString()` → `ExtProxy[point]`、`hashCode()` → `point.hashCode()`、`equals()` → 引用相等 |
| 本地执行 | `ExtInvocationHandler#invokeLocal` | PLATFORM / CUSTOM 走本地：优先用定义上已 `setInstance` 的实例；没有则 `implClass.getDeclaredConstructor().newInstance()` 反射建一个并回填缓存（因此实现类必须有无参构造，且**不是** Spring Bean，`@Autowired` 不生效）。无候选实现时抛 `IllegalStateException` |
| 远程执行 | [`ExtRpcInvoker`](z-ext-core/src/main/java/com/zifang/z/ext/rpc/client/ExtRpcInvoker.java) · [`ExtRpcInvokerFactory`](z-ext-core/src/main/java/com/zifang/z/ext/rpc/client/ExtRpcInvokerFactory.java) · [`ZRpcExtInvoker`](z-ext-core/src/main/java/com/zifang/z/ext/rpc/client/ZRpcExtInvoker.java) | EXTERNAL 走 RPC：工厂按 `host:port` 缓存 invoker（另有 `removeInvoker` / `closeAll` / `getInvokerCount`）。`ZRpcExtInvoker` 用 `com.zifang.z.rpc.remoting.RpcClient` 发 `RpcRequest`（`requestId` 由 `z-util-core` 的 `RandomUtil.uuid()` 生成，参数槽为 `setArguments`），响应里 `getException()` 非空就把异常抛回调用方，否则取 `getResult()` |
| 脚本实现桥 | [`ScriptBackedExtImpl`](z-ext-core/src/main/java/com/zifang/z/ext/core/script/ScriptBackedExtImpl.java) | 把一段脚本包装成 CUSTOM 实现：登记名为 `"script-" + scriptCode.hashCode()`、`order=100` 的 `ExtImplDefinition`，返回一个把调用委派给 `ScriptExecutor.execute(scriptCode, dslType, methodName, args)` 的代理。执行器由**调用方注入**，本仓不依赖 z-script |

---

## 🧩 扩展点的注册、生命周期与隔离（以代码为准）

**一个扩展点是什么**：一个被 `@ExtPoint` 标记的接口 + 注册中心里对应的 `ExtPointDefinition`（标识、接口类、执行类型、版本）。**一个插件式实现是什么**：`@ExtImpl` 标出的类 + 一条 `ExtImplDefinition`。二者都是注册表里的数据，不是被容器装载的产物。

**注册的两条路径**：

1. 编程式（当前实测唯一跑通的路径，全部 40 条测试都走这条）：`ExtRegistry.registerPoint(...)` → `ExtRegistry.registerImpl(...)` → `ExtProxyFactory.createProxy(pointDef)`。
2. 注解式：`@EnableExt(basePackages = "com.x")` → `ExtRegistrar` → `ExtScanner` 扫字节码填充注册表。`ExtScanner` 只有 `ExtScanner(String[])` 构造和字段，**没有 `basePackages` 的 setter**，而 `ExtRegistrar` 是用 `BeanDefinitionBuilder.addPropertyValue("basePackages", …)` 写这个属性的 ⇒ 实例化该 BeanDefinition 时 Spring 会因属性不可写而失败（`NotWritablePropertyException`）；`@EnableExt.proxyTargetClass` 也没有任何读取方。这条链路 0 测试覆盖。

**生命周期**：只有 *注册 → 启用/停用（`enabled` 标志）→ 激活切换（`setActiveImpl` / `getActiveImpl`）→ 清空（`clear()`，注释写明主要给测试用）* 四态。`ExtRegistry.ExtChangeListener` 提供 `onImplRegistered` 与 `onActiveImplChanged` 两个回调（默认空实现，单个监听器抛异常被吞掉不影响其它监听器），这是"热切换"唯一的可观测钩子。**没有 install / start / stop / uninstall，也没有版本协商**（`ExtPointDefinition.version` 只写不读，`getVersion()` 无调用方）。

**隔离**：注册表是静态全局的，同 JVM 内所有 Spring 上下文共享同一份 point/impl 状态，`clear()` 一发全清；不存在按插件划分的 ClassLoader（代码里出现的 `getClassLoader()` 只是 JDK 代理需要接口类加载器）。真正的隔离只有一种：EXTERNAL 实现跑在**另一个进程**里，靠 z-rpc 的 host:port 通信，本进程的异常边界由 `RpcResponse.getException()` 传回。

---

## 🏗️ 项目结构

```
z-ext/
├── pom.xml                 聚合 + 发布 POM：parent z-boot-parent:1.0.21、version 1.0.0、
│                           <modules> = z-ext-core + z-ext-web（_frontend 已注释、admin 已废弃）、central profile
├── LICENSE                 MIT 全文
├── README.md               本文档
├── z-ext-core/             框架核心：注解 / 注册表 / 路由 / 代理 / RPC / 脚本桥（19 个 main 类 + 9 个 test 文件）
├── z-ext-web/              IDEA 占位模块：src 只有 Main.java 打印 "Hello and welcome!"；在 reactor 里，被 central 排除
├── z-ext-admin/            已废弃、不在 reactor：无 Java 源码，只剩 SPA 静态产物
│   └── src/main/resources/static/        index.html + assets/*.js|css（_frontend 构建拷过来的历史快照）
├── _frontend/              配套 SPA（独立 npm 工程，同样不在 reactor）
│   ├── src/App.jsx         路由与菜单（概览 / 扩展点管理 / 实现管理 / 路由配置）
│   ├── src/pages/          Dashboard · Login · extension/ExtensionList · implementation/ImplementationList
│   ├── src/utils/request.js  axios 实例（createRequest from @yuku123/z-frontend-common）
│   ├── pom.xml             frontend-maven-plugin 配置（未参与构建，见「前端」一节）
│   └── vite.config.js      dev 端口 3000，/api → http://localhost:8080
└── _doc/                   文档，见文末「文档目录」
```

根目录还有 `target/`（`z-ext-1.0.0.pom`、`central-staging/`、`central-publishing/`、`central-deferred/`）与各模块的 `.flattened-pom.xml` —— **纯构建与发布产物**，`.gitignore` 已忽略、`git ls-files` 实测 0 条命中，不属于源码结构。

`z-ext-web` 与 `z-ext-admin` 的"POM 里存在"不等于"能用"：前者是空壳（central 已排除，见根 POM 的 `excludeArtifacts` 与该模块 POM 的 `<description>`），后者引用了仓库里根本不存在的构件（见「已知边界」第 3 条）。

---

## 🔧 技术栈

| 层级 | 技术（版本为已发布的 `z-ext-core-1.0.0.pom` 实测解析值） |
|------|------|
| 语言 / 运行时 | Java 8 |
| Spring | spring-context / beans / core / expression `5.3.39`（无 Spring Boot 依赖） |
| 网络 / 序列化 | netty-all `4.1.138.Final` · jackson-databind `2.18.6` |
| 远程实现 | `io.github.yuku123:z-rpc-remoting` `1.0.4`（`RpcClient`/`RpcRequest`/`RpcResponse`） |
| 工具 | `io.github.yuku123:z-util-core` `1.0.14`（`RandomUtil.uuid()`；FQN 仍是 `com.zifang.util.core.lang.RandomUtil`） |
| 日志门面 | log4j-api `2.25.4` |
| 编译期 | lombok `1.18.30`（`provided`） |
| 测试 | junit-jupiter `5.9.3`（地板 `z-boot-dependencies:1.0.20` 的 `junit-jupiter.version`，已发布 pom 经 flatten 不含 test 依赖）+ spring-boot-starter-test（test scope） |
| 版本治理 | 第三方面值全部由 `z-boot-parent` → `z-boot-dependencies`（地板）+ `z-boot-fleet`（权威表）下发，模块 POM 无字面版本钉；根 POM 只按坐标顶住 `org.yaml:snakeyaml` `2.0`（地板无 snakeyaml 直接条目，面值只会从它导入的 `spring-boot-dependencies:2.7.18` 落到 1.30），且 fleet 里**没有**任何 `z-ext` 条目（实测 0 命中） |
| 构建插件 | flatten-maven-plugin `1.5.0`（`oss` + `updatePomFile`，常开 ⇒ 发布 pom 自包含，不甩 parent 链）· maven-deploy-plugin `3.1.2` · versions-maven-plugin `2.17.1` · spring-boot-maven-plugin 本仓钉 `2.7.12`（仅供不在 reactor 的 admin）· surefire `2.22.2`（parent 下发） |
| 前端 | React `^19.2.0` · Ant Design `^6.3.3` · react-router-dom `^7.13.1` · axios `^1.16.1` · Vite `^6.2.0` · ESLint `^9.39.4`；共享库 `@yuku123/z-frontend-common` `^0.1.1`、`@yuku123/z-ext-frontend-component` `^0.1.0` |

---

## 🚀 快速开始

### 编译

```bash
mvn clean install -DskipTests      # reactor = z-ext-core + z-ext-web
mvn -q -pl z-ext-core -am install  # 只要核心 jar
```

前提：本地或镜像能解析 `io.github.yuku123:z-boot-parent:1.0.21`（`<relativePath/>` 为空，parent 不在磁盘上）。仓库根不存在 `../pom.xml`，任何"在 z-opc 父项目下编译"的说法都不成立。

### 作为依赖引入

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-ext-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 最小可用样例（照 `ExtIntegrationTest` 的真实调用顺序）

```java
@ExtPoint(value = "order.service", type = ExtType.SYNC)
public interface OrderService { String createOrder(String productId, int quantity); }

// 1) 登记扩展点
ExtPointDefinition point = new ExtPointDefinition();
point.setPoint("order.service");
point.setInterfaceClass(OrderService.class);
point.setType(ExtType.SYNC);
ExtRegistry.registerPoint(point);

// 2) 登记实现（PLATFORM 作基线，CUSTOM/EXTERNAL 可覆盖它）
ExtImplDefinition impl = new ExtImplDefinition();
impl.setPoint("order.service");
impl.setName("default");
impl.setImplClass(DefaultOrderService.class);
impl.setType(ExtImplType.PLATFORM);
impl.setEnabled(true);
impl.setInstance(new DefaultOrderService());   // 不设则由无参构造反射创建
ExtRegistry.registerImpl(impl);

// 3) 取代理并调用
OrderService svc = ExtProxyFactory.createProxy(point);
svc.createOrder("P001", 10);

// 4) 运行期切换实现
ExtRegistry.setActiveImpl("order.service", "custom");
```

### 配置与环境变量

本仓**没有**任何运行时配置文件（`application*.yml` / `logback.xml` 实测 0 个），也没有 profile 分支。唯一实测存在的环境变量在前端：

| 环境变量 | 出现处 | 用途 |
|----------|--------|------|
| `VITE_API_BASE_URL` | [`_frontend/src/utils/request.js`](_frontend/src/utils/request.js) | axios `baseURL`，缺省 `/api`；登录态读写键为 `zext_token` / `zext_user`（localStorage / sessionStorage） |

---

## 🖥️ 前端（`_frontend/`）

```bash
cd _frontend
pnpm install     # pnpm-workspace.yaml: nodeLinker: hoisted
pnpm dev         # http://localhost:3000，/api 代理到 http://localhost:8080
pnpm build       # 产物 dist/
pnpm lint
```

四个页面：概览 Dashboard（统计扩展点数 / 实现数 / 启用数，"最近活动"是硬编码的示例数据）、Login、扩展点管理 ExtensionList、实现管理 ImplementationList（列含 `point` / `name` / `type` / `implClass` / `rpcAddress` / `enabled` / `weight`）；菜单里还有一条 `/route`「路由配置」，实现是占位文本。

`_frontend/pom.xml` 保留了 frontend-maven-plugin（声明 node `v22.14.0` / npm `11.0.0`）与"把 `dist/` 拷进 `z-ext-admin/src/main/resources/static`"的 resources-plugin 配置，但这条 module 在根 POM 里是被注释掉的 —— 为了不触发发布构建去 nodejs.org 下 node，前端构建需手工执行。

---

## 🔌 接口一览

**Java API**（本仓对外的真正接口面）：

| 入口 | 关键方法 |
|------|----------|
| `ExtProxyFactory` | `createProxy(ExtPointDefinition)` · `createProxy(String point)`（point 标识或接口类型描述符）· `createProxy(Class, ExtInvocationHandler)` |
| `ExtRegistry` | `registerPoint` · `getPoint` · `getPointByInterface` · `getAllPoints` · `registerImpl` · `getImplementations` · `getEnabledImplementations` · `setActiveImpl` / `getActiveImpl` / `getActiveImplementation` · `getPlatformImpl` / `getExternalImpl` / `getCustomImpl` · `addListener` / `removeListener` · `clear` |
| `ExtRouter` / `DefaultExtRouter` | `route(pointDef, impls, ctx)` · `getName()`（默认实现 `"default"`）；换策略用 `ExtInvocationHandler#setRouter` |
| `ExtRpcInvokerFactory` | `getInvoker(host, port)` · `removeInvoker` · `closeAll` · `getInvokerCount` |
| `ScriptBackedExtImpl` | `createProxy(pointName, scriptCode, dslType, interfaceClass)` + `ScriptExecutor` SPI 式回调接口 |

**HTTP API：本仓没有。** 全仓 0 个 `@Controller` / `@RequestMapping`，`z-ext-web` 只有一个打印 `Hello and welcome!` 的 `Main`。SPA 发出的这些请求要由**宿主应用**实现，z-ext 只提供它们背后的注册表数据：

| 前端请求路径 | 方法 | 实现位置 |
|--------------|------|----------|
| `/api/auth/login` | POST | 本仓未实现 |
| `/api/ext/points` | GET / POST | 本仓未实现 |
| `/api/ext/points/{id}` | PUT / DELETE | 本仓未实现 |
| `/api/ext/implementations` | GET / POST | 本仓未实现 |
| `/api/ext/implementations/{id}` | PUT | 本仓未实现 |

---

## 🧪 测试

```bash
mvn -pl z-ext-core test
```

实测 5 个测试类 / 40 个 `@Test`，全在 `z-ext-core/src/test/java/com/zifang/z/ext/`：`ExtRegistryTest`(9)、`ExtRouterTest`(9)、`ExtProxyFactoryTest`(9)、`ExtInvocationHandlerTest`(7)、`ExtIntegrationTest`(6)；`test/` 下另备 `OrderService` + PLATFORM/CUSTOM 两个实现与一个 `@ExtPoint` 标注的 `PaymentService` 作夹具。`z-ext-web` / `z-ext-admin` / `_frontend` 无测试。

如实说明两处缺口：① 无 Spring 上下文测试 —— `ExtScanner`、`ExtRegistrar`、`@EnableExt`、`ScriptBackedExtImpl` 在测试源码里 0 命中，所以"注解扫描自动注册"这条链路没有任何测试把它跑通过；② `ZRpcExtInvoker` 需要真实 z-rpc 服务端才能端到端验证，纯单测只覆盖到注册表与代理层。没有 `*PerformanceTest.java`。

---

## 📦 发布产物与通道

本仓是库，不是可部署服务：实测没有 `Dockerfile*` / `docker-compose*.yml` / `deploy/` / `k8s/` / `Makefile` / 任何 `.sh`，也不该有。交付物就是 Maven Central 上的 `z-ext-core:1.0.0`（pom 由 flatten `oss` 生成、自包含）。

```bash
mvn -Pcentral,central-dryrun clean install    # 形状检查：只跳 javadoc 与签名，不真上传
mvn -Pcentral deploy -DskipTests              # 真发布
```

`central` profile（照 `z-cache` 口径逐件抄来）：`distributionManagement` 指向 `https://central.sonatype.com`（server id `central` 的凭据只放 `~/.m2/settings.xml`，值不落仓）；`maven-deploy-plugin` 3.1.4 置 `skip=true`，上传由 `central-publishing-maven-plugin` 0.7.0 接管（`tokenAuth` + `autoPublish` + `waitUntil=uploaded`）；`maven-source-plugin` 3.3.0 / `maven-javadoc-plugin` 3.11.2（`doclint=none`）/ `maven-gpg-plugin` 3.2.7（口令只从 `-Dgpg.passphrase` 传入，不写进 pom）。因为该插件不认 `maven.deploy.skip`，不可发布模块必须点名：当前 `excludeArtifacts` 里有 `z-ext-web`。

---

## ⚠️ 已知边界（读代码得出，不是猜测）

1. **`@EnableExt` 目前接不上**：`ExtScanner` 有 `basePackages` 字段和 `ExtScanner(String[])` 构造，却没有 setter，而 `ExtRegistrar` 恰恰用 `addPropertyValue` 写它 ⇒ 属性不可写、Bean 实例化即失败。可用路径是手工 `ExtRegistry.register*`；若要修，把 Registrar 改成用构造参数（`genericBeanDefinition(ExtScanner.class, basePackages)`）而不是属性注入。`@EnableExt.proxyTargetClass` 同样无人读取，代理始终是 JDK 接口代理。
2. **注解上写了但框架没实现的字段**：`ExtImpl.condition`（注释宣称支持 SpEL，但全仓 0 处 `ExpressionParser`/`StandardEvaluationContext`，路由从不求值）、`ExtImpl.weight`（只有 getter/setter，无负载均衡）、`ExtType.ASYNC`（`ExtInvocationHandler` 只对 CHAIN 分支特殊处理，ASYNC 与 SYNC 一样同步执行并阻塞）、`ExtPoint.order`（候选排序只用实现自身的 `order`）、`ExtPointDefinition.version`（只写不读）。`ExtInvocationHandler.routeCache` 声明并会被 `clearCache()` 清空，但从没被写入 —— 实际每次调用都重新路由。
3. **`z-ext-admin` 无法构建**：它依赖 `io.github.yuku123:z-ext-spring-boot-starter:${project.version}`（`${project.version}` = 1.0.0），而该坐标 repo1 实测 404、本地 `~/.m2` 无、`z-boot/z-boot-integration-starters/` 下也无该模块；同时它已不在 `<modules>` 里，也没有任何 Java 源码。它的 `static/` 资产是历史快照。
4. **本地实现不受 Spring 管理**：`invokeLocal` 走 `getDeclaredConstructor().newInstance()`，实现类里的 `@Autowired` / `@Value` 都不生效；需要依赖注入就自己 `setInstance(bean)`。
5. **注册表是 JVM 全局静态状态**，不随 Spring 上下文销毁而隔离，多上下文/测试并发共享同一份 point 与 active 映射。
6. **`_doc/` 有两份文档已过期**：`00-overview.md` 写"SPI/ServiceLoader 插件发现、自定义 ClassLoader 沙箱、Spring Boot 2.7.12"，代码里没有对应实现；`01-module-structure.md` 仍称父项目是 `com.zifang:z-opc:1.0.0-SNAPSHOT` 且"单独 clone 无法 Maven build"，与现在自包含继承 `z-boot-parent` 的 POM 相反。以本 README 与 POM/源码为准。

---

## 📝 近期变更（来自 `git log`）

| 提交 | 内容 |
|------|------|
| `4f40b8f` | 坐标从 `com.zifang` 换成 `io.github.yuku123:1.0.0`，`com.zifang:z-rpc-core` 幻影依赖换 `z-rpc-remoting`（类集与 `setParameters`→`setArguments` 签名比对后改的），补中央发布通道 |
| `673fb6e` · `44dc640` | 消费入口抬到 `z-boot-parent:1.0.21`（组织消费者轮，先前是 1.0.19），删掉仓内所有字面版本钉 |
| `67b6615` | `ExtProxyFactory.createProxy(String)` 支持传扩展点接口的类型描述符，按接口类反查扩展点 |
| `6ccab93` | 对齐 `z-boot-dependencies` 口径（netty 4.1.138.Final） |
| 2026-09-06 | 从 `idea_workplace/z-opc` monorepo 拆出独立仓（历史日期，见 `01-module-structure.md`） |

---

## 📄 License

MIT，见仓库根 [`LICENSE`](LICENSE)（`Copyright (c) 2026 z-opc-foundation`）与根 POM 的 `<licenses>` 声明。此前本节写的"Internal use only / 版权属于 z-biz"与磁盘事实不符，已纠正。

_Maintained by the z-opc-foundation organization._

---

## 文档目录

本项目文档统一收口在 `_doc/` 下：

- [`_doc/001_arch/`](_doc/001_arch/) — 架构与设计文档：
  - [`00-overview.md`](_doc/001_arch/00-overview.md) — 项目总览与愿景（注意：ClassLoader 沙箱 / SPI 插件发现等尚未实现）
  - [`01-module-structure.md`](_doc/001_arch/01-module-structure.md) — 从 z-opc 拆分说明（父项目描述已过期，见「已知边界」第 6 条）
  - [`扩展平台SDK产品方案.md`](_doc/001_arch/扩展平台SDK产品方案.md) — 产品定位与功能矩阵
  - [`扩展平台SDK技术方案.md`](_doc/001_arch/扩展平台SDK技术方案.md) — 技术方案（其中的 `ExtensionPoint` / `Extension` 命名为设计稿，落码为 `ExtPoint` / `ExtImpl`）
- `_doc/002_deploy/` — 目前为空目录（本仓无可部署资产）
- `_doc/003_script/` — 目前为空目录
- `_doc/004_skill/` — 目前为空目录

关键源码入口：[`z-ext-core/pom.xml`](z-ext-core/pom.xml)、[`ExtRegistry`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtRegistry.java)、[`ExtProxyFactory`](z-ext-core/src/main/java/com/zifang/z/ext/core/proxy/ExtProxyFactory.java)、[`ExtIntegrationTest`](z-ext-core/src/test/java/com/zifang/z/ext/ExtIntegrationTest.java)、[`_frontend/src/App.jsx`](_frontend/src/App.jsx)、[`_frontend/vite.config.js`](_frontend/vite.config.js)。
