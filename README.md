# z-ext

> Java 扩展点框架（Extension Point SDK）—— `@ExtPoint` 标记能力边界，`@ExtImpl` 登记实现，JDK 动态代理在运行时选一个实现来调用

它解决的问题是"同一套接口，多种实现，运行期决定用哪个"：平台方只发布接口和一个默认（PLATFORM）实现，业务方可以注册 CUSTOM 实现顶掉它，三方服务可以登记为 EXTERNAL 实现、由框架走 z-rpc 远程调用；切换靠注册表里的"当前激活实现"，不需要重新编译、不需要 if-else 分支。

需要说清楚的是：**核心仍是进程内的扩展点路由框架，不是插件容器**——本仓代码里没有 ClassLoader 隔离、没有 ServiceLoader/SPI 插件发现、没有 jar 上传/安装/卸载。1.1.0 起它长出了介入能力的骨架：注册表的可变状态收口到 `ActiveImplStore` SPI，starter 把控制面嵌进业务实例，admin 把多个实例的操作聚合到一个控制台（细节见「接口一览」与「已知边界」）。1.2.0 起补上**五档侵入方式**（零侵入预置 / 注解 / 适配既有 Bean / AOP 织入 / 零代码 mock 实现），业务仓按侵入程度自选，控制面始终用同一套接口操作。`_doc/` 里的产品方案写了更多（沙箱、插件市场），那是设计意图，不是现状。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-ext`（origin `git@github.com:z-opc-foundation/z-ext.git`，分支 `main`） |
| **Maven 坐标** | `io.github.yuku123:z-ext`（聚合 POM）· `z-ext-core`（框架核心 jar）· `z-ext-spring-boot-starter`（Boot 自动装配 + 嵌入式控制面）· `z-ext-admin`（控制台 standalone 应用，**不发 Central**） |
| **当前版本** | `1.2.6` 写在根 POM `<version>`，子模块 `<parent>` 逐字重复同一版本号——本仓**没有**用 `<revision>` 占位（尽管继承了带 CI-friendly 约定的 `z-boot-parent`） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.2.6`，`<relativePath/>` 留空（parent 在 repo1，不在磁盘）；第三方地板与兄弟仓权威表均已并入 parent 自身（`z-boot-dependencies` / `z-boot-fleet` 两个文件夹随 2026-10-08 火车制拓扑退役） |
| **Maven Central** | 已发布：`z-ext:1.0.0` pom 200（2026-09-30）；`z-ext-core` 1.0.0 与 **1.0.1**（2026-10-23，repo1 `maven-metadata` latest=release=`1.0.1`）。本仓的 **1.2.6 尚未发布到 Central**（本地 `~/.m2` 与内网 Nexus 已供给）。404 未发布：`z-ext-web`（被 `central` profile 的 `excludeArtifacts` 点名排除）、`z-ext-frontend`（不在 reactor）、旧 namespace `com.zifang:z-ext` / `com.zifang:z-ext-core` |
| **默认端口** | 库本体（z-ext-core / starter）无 —— `z-ext-core` 连 Boot 都不依赖；唯一启 HTTP 服务的是 `z-ext-admin` 控制台，监听 `8888`（`z-ext-admin/src/main/resources/application.yml` 实测 `server.port=8888`）。前端 dev server 监听 `3003`，把 `/api` 代理到 `http://localhost:8888`（控制台或宿主应用） |
| **运行口径** | Java 17（**当前生效**：`z-boot-parent:1.2.6` 下发 `maven.compiler.source/target=17` 与 `-parameters`）· Spring Boot 地板 `3.5.16`（BOM 口径，实测 `spring-boot.version`）；`z-ext-core` 只依赖 spring-context/beans/core/expression，**不依赖 Spring Boot**（仅 test scope 引 `spring-boot-starter-test`）。根 POM 注释里残留的 `source/target=8` / `spring-boot-maven-plugin 2.7.12` 是 parent 升级前（1.0.19 时代）的历史口径，已不生效 |
| **许可证** | MIT（根 [`LICENSE`](LICENSE) 为 MIT 全文，根 POM `<licenses>` 声明 MIT License） |
| **最近更新** | 2026-11-18 |

---

## 🎯 能力清单（逐条对得上类）

核心实现全部在 [`z-ext-core/src/main/java/com/zifang/z/ext/`](z-ext-core/src/main/java/com/zifang/z/ext/)（42 个 main 类）；接入层在 `z-ext-spring-boot-starter`，控制台在 `z-ext-admin`，二者不往 core 里塞一行。

| 能力 | 实现类 | 实测行为 |
|------|--------|----------|
| 扩展点声明 | [`ExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtPoint.java) · [`ExtType`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtType.java) | 标在接口上：`value()` 是 point 标识，另有 `description` / `version`（默认 `"1.0.0"`）/ `order` / `type` ∈ SYNC · ASYNC · CHAIN |
| 实现声明 | [`ExtImpl`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtImpl.java) · [`ExtImplType`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtImplType.java) | `point` + `name` + `type` ∈ PLATFORM · EXTERNAL · CUSTOM，另有 `order` / `enabled` / `condition` / `weight` |
| 注解扫描装配 | [`EnableExt`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/EnableExt.java) → [`ExtRegistrar`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtRegistrar.java) → [`ExtScanner`](z-ext-core/src/main/java/com/zifang/z/ext/annotation/ExtScanner.java) | `@Import(ExtRegistrar.class)`；Registrar 以 `ImportBeanDefinitionRegistrar` 注册名为 `extScanner` 的 BeanDefinition，并用 `addPropertyValue("basePackages", …)` 注入 `ExtScanner.setBasePackages(String[])`；Scanner 作 `BeanFactoryPostProcessor` 用 `PathMatchingResourcePatternResolver` + `CachingMetadataReaderFactory` 扫 `basePackages/**/*.class`，把 `@ExtPoint`/`@ExtImpl` 填进 `ExtRegistry`。未指定 `basePackages` 时默认取 `@EnableExt` 所在类的包。`EnableExtIntegrationTest` 起真实 Spring 上下文验证这条链路（显式包 + 默认包两个分支） |
| 注册中心 | [`ExtRegistry`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtRegistry.java) | 四张 JVM 级静态表：`EXT_POINT_REGISTRY` / `EXT_IMPL_REGISTRY` / **激活映射（`ActiveImplStore` SPI，默认 `LocalActiveImplStore`，可外置换 Config/Remote 实现）** + `LISTENERS`（`CopyOnWriteArrayList`）。`getImplementations` 无实现时返回空列表而非 null；`getEnabledImplementations` 过滤 `enabled` 并按 `order` 升序；`unregisterImpl` / `unregisterPoint` 支持注销（激活态随之撤销） |
| 控制面 SPI | [`ActiveImplStore`](z-ext-core/src/main/java/com/zifang/z/ext/core/store/ActiveImplStore.java) · [`ExtAdminProvider`](z-ext-core/src/main/java/com/zifang/z/ext/core/admin/ExtAdminProvider.java) · [`DefaultExtAdminProvider`](z-ext-core/src/main/java/com/zifang/z/ext/core/admin/DefaultExtAdminProvider.java) | 介入能力的唯一可变状态出口是 `ActiveImplStore`（`setActiveImpl` / `getActiveImpl` / `getAll` / `removeActiveImpl`）；`ExtAdminProvider` 是控制契约（snapshot / activate / deactivate / setEnabled / registerImpl），默认实现读 `ExtRegistry` + 当前 store。快照视图 `ExtSnapshot` / `ExtPointView` / `ExtImplView`。跨 JVM 控制的关键不变量：**切换必须落在业务进程自己的 store 上**，远端控制台改不了别的 JVM 的静态注册表 |
| 条件路由与权重 | [`ExtConditionEvaluator`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/ExtConditionEvaluator.java) · [`SpelConditionEvaluator`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/SpelConditionEvaluator.java) · [`ExtContextHolder`](z-ext-core/src/main/java/com/zifang/z/ext/core/context/ExtContextHolder.java) | `condition` 走 SpEL：根对象是 `ExtRouterContext`，变量 `#methodName` / `#args` / `#environment` + 上下文属性；`#{...}` 模板形态剥壳后等价；求值失败按**不命中**（收紧默认）。非 CHAIN 的同 `order` 组内按 `weight` 加权随机（`order` 小者优先，`weight<=0` 按 1 计）。调用前置上下文用 `ExtContextHolder.runWith(...)`（ThreadLocal，随调用合入路由上下文） |
| 定义模型 | [`ExtPointDefinition`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtPointDefinition.java) · [`ExtImplDefinition`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtImplDefinition.java) | Point 持 `interfaceClass` + 实现列表；Impl 持 `implClass` / `instance` / `rpcAddress` / `rpcPort`（远程实现的路由地址就挂在定义上） |
| 按接口类反查 | `ExtRegistry.getPointByInterface(Class)` | point 标识是自由文本，调用方常只握有接口 Class；遍历比对 `interfaceClass`，找不到返回 null |
| 路由策略 | [`ExtRouter`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/ExtRouter.java) · [`DefaultExtRouter`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/DefaultExtRouter.java) · [`ExtRouterContext`](z-ext-core/src/main/java/com/zifang/z/ext/core/router/ExtRouterContext.java) | `route(pointDef, impls, context)` 可整体替换（`ExtInvocationHandler#setRouter`）。默认：CHAIN 返回条件过滤后的**全部**候选；SYNC/ASYNC 只返回一个 —— 先取"激活实现"（显式 `setActiveImpl`，否则回退注册中心的 PLATFORM 默认实现），且要求它 `enabled` 并以**同一对象身份**出现在候选里；否则按 `EXTERNAL > CUSTOM > PLATFORM` 取第一个启用项 |
| 动态代理 | [`ExtProxyFactory`](z-ext-core/src/main/java/com/zifang/z/ext/core/proxy/ExtProxyFactory.java) · [`ExtInvocationHandler`](z-ext-core/src/main/java/com/zifang/z/ext/core/proxy/ExtInvocationHandler.java) | 单接口 `Proxy.newProxyInstance`。`createProxy` 重载：传 `ExtPointDefinition` / 传 point 字符串（标识或接口类型描述符，自动剥前缀后按接口类反查）/ 传接口类 + 自定义 handler / 传 handler 定制器（starter 用它注入 router 与异步执行器）。`Object` 方法被短路：`toString()` → `ExtProxy[point]`、`hashCode()` → `point.hashCode()`、`equals()` → 引用相等 |
| 执行语义 | `ExtInvocationHandler` | CHAIN → 逐个全跑（收集结果）；ASYNC → **仅当**装配了异步执行器才异步（`z.ext.async.enabled=true`），未装配按 SYNC 同步执行（向后兼容）；其余同步单选执行。异步失败只记日志不向调用方抛 |
| 本地执行 | `ExtInvocationHandler#invokeLocal` | PLATFORM / CUSTOM 走本地：优先用定义上已 `setInstance` 的实例；没有则 `implClass.getDeclaredConstructor().newInstance()` 反射建一个并回填缓存（实现类必须有无参构造）。starter 的 `ExtImplInstanceBinder` 会把 Spring 容器里的 `@ExtImpl` Bean 回填到定义上，使 `@Autowired` 生效 |
| 远程执行 | [`ExtRpcInvoker`](z-ext-core/src/main/java/com/zifang/z/ext/rpc/client/ExtRpcInvoker.java) · [`ExtRpcInvokerFactory`](z-ext-core/src/main/java/com/zifang/z/ext/rpc/client/ExtRpcInvokerFactory.java) · [`ZRpcExtInvoker`](z-ext-core/src/main/java/com/zifang/z/ext/rpc/client/ZRpcExtInvoker.java) | EXTERNAL 走 RPC：工厂按 `host:port` 缓存 invoker（另有 `removeInvoker` / `closeAll` / `getInvokerCount`）。`ZRpcExtInvoker` 用 `com.zifang.z.rpc.remoting.RpcClient` 发 `RpcRequest`（`requestId` 由 `z-util-core` 的 `RandomUtil.uuid()` 生成，参数槽为 `setArguments`），响应里 `getException()` 非空就把异常抛回调用方，否则取 `getResult()` |
| 脚本实现桥 | [`ScriptBackedExtImpl`](z-ext-core/src/main/java/com/zifang/z/ext/core/script/ScriptBackedExtImpl.java) | 把一段脚本包装成 CUSTOM 实现：登记名为 `"script-" + scriptCode.hashCode()`、`order=100` 的 `ExtImplDefinition`，返回一个把调用委派给 `ScriptExecutor.execute(scriptCode, dslType, methodName, args)` 的代理。执行器由**调用方注入**，本仓不依赖 z-script |
| Boot 接入 | `z-ext-spring-boot-starter`（`ZExtAutoConfiguration`） | `@EnableExt` 之外的第二条装配路：Boot 自动装配提供 `ActiveImplStore`（可换 Bean）· `ExtRouter` · `ExtAdminProvider` · `ExtProxyBuilder`（路由 + 异步执行器就绪的代理工厂）· `ExtImplInstanceBinder`（`@ExtImpl` Bean 回填）；servlet-web 环境默认开嵌入式控制面 `z.ext.admin.base-path=/api/ext`（可 `z.ext.admin.enabled=false` 关） |
| 预置埋点（零侵入） | [`PresetPoints`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetPoints.java) · [`PresetGateExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetGateExtPoint.java) · [`PresetAdviceExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetAdviceExtPoint.java) · [`PresetEventExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetEventExtPoint.java) · [`PresetRegistrar`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetRegistrar.java) | starter 自动注册三个点（闸门/环绕/通知）+ 默认实现，**不要求宿主写 `@EnableExt`**；落点覆盖 HTTP 请求、启动就绪、关闭、请求完成。闸门拒绝 ⇒ `503`；闸门实现抛错 ⇒ 失败放行（默认） |
| 适配既有实现 | [`ExtAdapter`](z-ext-core/src/main/java/com/zifang/z/ext/adapter/ExtAdapter.java) · [`ExtAdapterRegistrar`](z-ext-core/src/main/java/com/zifang/z/ext/adapter/ExtAdapterRegistrar.java) | 既有 Bean 标 `@ExtAdapter("point")` 即成为该点的一个可选实现（默认实现名取类名首字母小写）；注册前校验扩展点存在、Bean 实现了目标接口 |
| AOP 织入 | [`ExtHook`](z-ext-core/src/main/java/com/zifang/z/ext/aop/ExtHook.java) · `ExtHookInterceptor` · `ExtHookAutoConfiguration` | 既有方法标 `@ExtHook` ⇒ 调用自动过扩展点，**调用点代码不改**；织入由容器自动代理器完成，宿主已有 `@EnableAspectJAutoProxy` 时同样生效 |
| 零代码实现 | [`MockSpec`](z-ext-core/src/main/java/com/zifang/z/ext/mock/MockSpec.java) · [`MockExtImplFactory`](z-ext-core/src/main/java/com/zifang/z/ext/mock/MockExtImplFactory.java) | 控制面下发行为描述即造出实现代理：固定返回 / 恒拒绝 / 抛错 / 延迟；返回值按方法签名适配（装箱、基本类型默认值、JSON 反序列化） |
| 控制台 | `z-ext-admin`（`ZExtAdminApplication`） | standalone Boot 应用：登录（凭据走环境变量）+ 本机控制面 + **实例聚合**——`/api/ext/instances/**` 把操作代理转发到各业务实例的嵌入式控制面（透传 Authorization），多进程介入的操作入口 |

---

## 🧩 扩展点的注册、生命周期与隔离（以代码为准）

**一个扩展点是什么**：一个被 `@ExtPoint` 标记的接口 + 注册中心里对应的 `ExtPointDefinition`（标识、接口类、执行类型、版本）。**一个插件式实现是什么**：`@ExtImpl` 标出的类 + 一条 `ExtImplDefinition`。二者都是注册表里的数据，不是被容器装载的产物。

**注册的两条路径**：

1. 编程式（当前实测唯一跑通的路径，全部既有测试都走这条）：`ExtRegistry.registerPoint(...)` → `ExtRegistry.registerImpl(...)` → `ExtProxyFactory.createProxy(pointDef)`。
2. 注解式：`@EnableExt(basePackages = "com.x")` → `ExtRegistrar` → `ExtScanner` 扫字节码填充注册表。`ExtScanner` 提供 `setBasePackages(String[])`（防御性拷贝），承接 `ExtRegistrar` 的 `BeanDefinitionBuilder.addPropertyValue("basePackages", …)`；未指定 `basePackages` 时 Registrar 默认取注解所在类的包。`EnableExtIntegrationTest` 起真实 `AnnotationConfigApplicationContext` 验证容器能启动、且注册表里确有扫描到的 `@ExtPoint`/`@ExtImpl`。`@EnableExt.proxyTargetClass` 仍无任何读取方（见「已知边界」第 1 条）。

**生命周期**：只有 *注册 → 启用/停用（`enabled` 标志）→ 激活切换（`setActiveImpl` / `getActiveImpl`）→ 清空（`clear()`，注释写明主要给测试用）* 四态。`ExtRegistry.ExtChangeListener` 提供 `onImplRegistered` 与 `onActiveImplChanged` 两个回调（默认空实现，单个监听器抛异常被吞掉不影响其它监听器），这是"热切换"唯一的可观测钩子。**没有 install / start / stop / uninstall，也没有版本协商**（`ExtPointDefinition.version` 只写不读，`getVersion()` 无调用方）。

**隔离**：注册表是静态全局的，同 JVM 内所有 Spring 上下文共享同一份 point/impl 状态，`clear()` 一发全清；不存在按插件划分的 ClassLoader（代码里出现的 `getClassLoader()` 只是 JDK 代理需要接口类加载器）。真正的隔离只有一种：EXTERNAL 实现跑在**另一个进程**里，靠 z-rpc 的 host:port 通信，本进程的异常边界由 `RpcResponse.getException()` 传回。


---

## 🪝 五档侵入方式（1.2.0 起：从零到深，各仓自选）

「预埋点」不等于「每个仓都写一个扩展点接口」。z-ext 1.2.0 把接入拆成五档，
**侵入程度递增、控制力递增**，业务仓挑一档（常常是「零侵入 + 一处业务点」的组合）：

| 档 | 名称 | 业务侧要写什么 | 能被操控成什么 | 落点代码 |
|----|------|----------------|----------------|----------|
| ① | **零侵入预置** | 只加一行依赖 | 任意 HTTP 请求被闸门拒绝（503）、生命周期事件被接管 | `PresetGateAutoConfiguration` + `PresetWebAutoConfiguration` 自动装配 |
| ② | **注解扩展点** | 新增 `@ExtPoint` 接口 + `@ExtImpl` 实现 + `@EnableExt` + 调用点走代理 | 整个扩展点换实现、换条件、换权重 | `ExtPoint` / `ExtImpl` / `ExtScanner`（既有能力） |
| ③ | **适配既有 Bean** | 在现成 Bean 上加 `@ExtAdapter("point")` | 在「正常实现 / 降级实现」等既有实现之间一键切换 | `ExtAdapter` + `ExtAdapterRegistrar` + `ExtAdapterBeanPostProcessor` |
| ④ | **AOP 织入** | 在既有方法上加 `@ExtHook("point")`，**调用点不改** | 结果被改写、调用被中断 | `ExtHook` + `ExtHookInterceptor` + `ExtHookAutoConfiguration` |
| ⑤ | **零代码实现** | 什么都不写 | 控制面直接下发 mock（固定返回 / 恒拒绝 / 抛错 / 延迟），即刻接管 | `MockExtImplFactory` + 控制面 `POST /api/ext/implementations/mock` |

### 实例怎么被看见（自动登记）

埋点埋好了，控制台得知道这个实例在哪儿才能操控它。两条通道：

| 通道 | 配置 | 说明 |
|------|------|------|
| **直连控制台**（默认推荐） | `z.ext.console.enabled=true` · `z.ext.console.url=http://<控制台>:8888` | 实例启动就绪后向控制台 `POST /api/ext/instances` 登记自己，容器关闭时注销。控制台与实例之间网络可达即可，不依赖任何中间注册中心 |
| **z-config 命名通道** | 控制台侧 `z.ext.console.zconfig.server-addr` | 控制台从 z-config 的命名表拉实例（服务侧用 z-config starter 的 `z.config.naming.auto-register` 注册）。命名接口若在 SSO 闸门内，需要另行开通机器通道 |

直连通道的实现见 `ExtConsoleRegistrar`（starter）：登记失败只记日志，不拖挂启动；
对外地址默认取本机站点地址 + 实际端口（`z.ext.console.base-url` 可覆盖）。
登记失败会**退避重试**（5s / 15s / 30s / 60s，跑在守护线程上）：控制台晚起于实例时，
实例仍会自动出现在控制台里，不必重启；容器关闭即停重试。

### ① 零侵入预置（引依赖即生效）

starter 自动注册三个点并挂好落点，**宿主一行代码都不用写**：

| 点标识 | 接口 | 语义 | 默认实现 |
|--------|------|------|----------|
| `z.ext.preset.gate` | [`PresetGateExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetGateExtPoint.java) | 闸门：`allow(event, attrs)` 决定放行与否，`denyMessage` 给拒绝提示 | `PresetGateDefaultExtPoint`（恒放行） |
| `z.ext.preset.advice` | [`PresetAdviceExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetAdviceExtPoint.java) | 环绕：`around(event, attrs, action)` 可放行后改写、替换结果或中断 | `PresetAdviceDefaultExtPoint`（原样放行） |
| `z.ext.preset.event` | [`PresetEventExtPoint`](z-ext-core/src/main/java/com/zifang/z/ext/preset/PresetEventExtPoint.java) | 通知：`publish(event, attrs)` 抛运行期事件（观测/告警/审计） | `PresetEventDefaultExtPoint`（空实现） |

落点：HTTP 请求（事件名 `http.<METHOD>.<路径模板>`，拒绝返回 `503`）、
启动就绪 `app.ready`、关闭 `app.shutdown`、请求完成 `http.completed`（带耗时）。
**失败放行**：闸门实现自己抛错时按放行处理（`z.ext.preset.http.fail-open`，默认 true）——
控制面是运维手段，它坏了不该把业务一起带下去。

配置项：`z.ext.preset.enabled`（默认开）· `z.ext.preset.http.enabled`（默认开）·
`z.ext.preset.http.deny-status`（默认 503）· `z.ext.preset.http.fail-open`（默认 true）。

### ③ 适配既有 Bean

```java
@ExtAdapter(point = "z.ext.preset.gate", name = "degraded")
@Service
public class DegradedGate implements PresetGateExtPoint { /* 现成的降级逻辑 */ }
```

标完它就成了该扩展点的一个可选实现，控制面切过去即刻生效；
也可以运行时用 `ExtAdapterRegistrar.register(point, bean, name)` 现场挂载，
或走控制面 `POST /api/ext/adapters`（按 Bean 名挂载）。

### ④ AOP 织入（调用点不改）

```java
@ExtHook(event = "order.compute")
public OrderResult compute(OrderRequest req) { /* 原样 */ }
```

调用侧仍是 `service.compute(req)`，但这次调用已经过了扩展点：
控制面切到别的实现后，结果可被改写或调用被中断。
织入由容器里的自动代理器完成；宿主若已有 `@EnableAspectJAutoProxy`，那个织入器优先级更高，
但同样会拾取本件提供的 Advisor，两种情况都生效。

### ⑤ 零代码实现（mock 注入）

```bash
curl -X POST http://<host>:<port>/api/ext/implementations/mock \
  -H 'Content-Type: application/json' \
  -d '{"point":"z.ext.preset.gate","name":"drill","behavior":"DENY","errorMessage":"演练封禁"}'
```

再 `POST /api/ext/points/z.ext.preset.gate/activate {"implName":"drill"}` ⇒ 服务立刻开始拒绝，
控制台上一键回滚即恢复。`behavior` 取 `RETURN` / `THROW` / `DENY`，另支持 `delayMs` 模拟慢依赖。

### 五档的关系

五档共用**同一份注册表、同一套控制面接口**：控制台看到的点/实现/激活态，
不管它是由注解、适配器、AOP 还是 mock 注入产生的，切换方式完全一致。
---

## 🏗️ 项目结构

```
z-ext/
├── pom.xml                 聚合 + 发布 POM：parent z-boot-parent:1.2.6、version 1.2.6、
│                           <modules> = z-ext-core + z-ext-spring-boot-starter + z-ext-admin + z-ext-web（_frontend 已注释）、central profile
├── LICENSE                 MIT 全文
├── README.md               本文档
├── z-ext-core/             框架核心：注解 / 注册表 / 路由 / 代理 / RPC / 脚本桥 / 控制面 SPI（42 个 main 类）
├── z-ext-spring-boot-starter/  Boot 自动装配：注册表装配 / ExtProxyBuilder / 实例回填 / 嵌入式控制面 /api/ext/**（servlet-web 默认开）
├── z-ext-admin/            控制台 standalone 应用：登录 + 实例聚合代理 + 前端内嵌；监听 8888，不发 Central
│   ├── src/main/java/com/zifang/z/ext/admin/   应用入口 / auth（TokenStore + 拦截器）/ instance（注册表 + RestClient 转发）
│   └── src/main/resources/static/        index.html + assets/*.js|css（_frontend 构建拷进来的内嵌前端）
├── z-ext-web/              IDEA 占位模块：src 只有 Main.java 打印 "Hello and welcome!"；在 reactor 里，被 central 排除
├── _frontend/              配套前端（两个 npm 工程，都不在 reactor）
│   ├── z-ext-component/    组件层 library（`@yuku123/z-ext-component`）：pages / extension / services
│   └── z-ext-suit/         应用层 SPA（`@yuku123/z-ext-suit`，private）：App.jsx / main.jsx
│       └── vite.config.js  dev 端口 3003，/api → http://localhost:8888
└── _doc/                   文档，见文末「文档目录」
```

根目录还有 `target/`、各模块的 `.flattened-pom.xml` 与 `node_modules`/`dist` —— **纯构建与发布产物**，`.gitignore` 已忽略，不属于源码结构。

`z-ext-web` 的"POM 里存在"不等于"能用"：它是空壳占位（central 已排除，见根 POM 的 `excludeArtifacts` 与该模块 POM 的 `<description>`）。

---

## 🔧 技术栈

| 层级 | 技术（当前 reactor 实测解析值，地板由 `z-boot-parent:1.2.6` → `spring-boot-dependencies:3.5.16` 下发） |
|------|------|
| 语言 / 运行时 | Java 17 |
| Spring | spring-context / beans / core / expression `6.2.x`（core 无 Boot 依赖）；starter / admin 走 Boot `3.5.16` 自动装配 |
| 网络 / 序列化 | netty-all `4.1.138.Final` · jackson-databind `2.18.6` |
| 远程实现 | `io.github.yuku123:z-rpc-remoting` `1.0.4`（`RpcClient`/`RpcRequest`/`RpcResponse`） |
| 工具 | `io.github.yuku123:z-util-core` `1.0.14`（`RandomUtil.uuid()`；FQN 仍是 `com.zifang.util.core.lang.RandomUtil`） |
| 日志门面 | log4j-api `2.25.4` |
| 编译期 | lombok `1.18.30`（`provided`） |
| 测试 | junit-jupiter `5.9.3`（`z-boot-parent` 自带地板的 `junit-jupiter.version`，已发布 pom 经 flatten 不含 test 依赖）+ spring-boot-starter-test（test scope） |
| 版本治理 | 第三方面值全部由 `z-boot-parent` 下发（第三方地板与兄弟仓权威表均已并进 parent 自身，`z-boot-dependencies` / `z-boot-fleet` 两个文件夹随 2026-10-08 火车制拓扑退役），模块 POM 无字面版本钉；根 POM 只按坐标顶住 `org.yaml:snakeyaml` `2.0`（地板无 snakeyaml 直接条目，面值只会从它导入的 `spring-boot-dependencies:2.7.18` 落到 1.30），且 parent 的兄弟仓权威表里**没有**任何 `z-ext` 条目（实测 0 命中） |
| 构建插件 | flatten-maven-plugin `1.5.0`（`oss` + `updatePomFile`，常开 ⇒ 发布 pom 自包含，不甩 parent 链）· maven-deploy-plugin `3.1.2` · versions-maven-plugin `2.17.1` · spring-boot-maven-plugin（面值随 parent；admin 的 repackage 用）· surefire `2.22.2`（parent 下发） |
| 前端 | React `^19.2.0` · Ant Design `^6.3.3` · react-router-dom `^7.13.1` · axios `^1.16.1` · Vite `^6.2.0` · ESLint `^9.39.4`；共享库 `@yuku123/z-frontend-common` `^0.1.1`、`@yuku123/z-ext-frontend-component` `^0.1.0` |

---

## 🚀 快速开始

### 编译

```bash
mvn clean install -DskipTests      # reactor = z-ext-core + z-ext-spring-boot-starter + z-ext-admin + z-ext-web
mvn -q -pl z-ext-core -am install  # 只要核心 jar
mvn spring-boot:run -pl z-ext-admin   # 起控制台（8888），先 export ZEXT_ADMIN_USERNAME / ZEXT_ADMIN_PASSWORD
```

前提：本地或镜像能解析 `io.github.yuku123:z-boot-parent:1.2.6`（`<relativePath/>` 为空，parent 不在磁盘上）。仓库根不存在 `../pom.xml`，任何"在 z-opc 父项目下编译"的说法都不成立。

### 作为依赖引入

Central 上最新已发布版本是 `z-ext-core:1.0.1`（本仓 1.2.6 未发 Central，发布前用本地 `mvn install`）：

```xml
<!-- 纯库用法（JVM 内扩展点路由） -->
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-ext-core</artifactId>
    <version>1.0.1</version>
</dependency>

<!-- Spring Boot 用法：自动装配注册表 / 代理工厂 / 预置埋点 / 嵌入式控制面（推荐，1.1.0 起） -->
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-ext-spring-boot-starter</artifactId>
    <version>1.2.6</version>
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

`z-ext-core` 没有运行时配置文件；全仓实测 1 个 `application.yml`（`z-ext-admin/src/main/resources/application.yml`：`server.port=8888` + 控制台凭据键），没有 profile 分支。实测存在的环境变量：

| 环境变量 | 出现处 | 用途 |
|----------|--------|------|
| `ZEXT_ADMIN_USERNAME` / `ZEXT_ADMIN_PASSWORD` | `z-ext-admin/src/main/resources/application.yml` → `z.ext.console.*` | 控制台登录凭据，**只从环境变量注入，不落仓**；任一为空则拒绝所有登录（HTTP 503） |
| `VITE_API_BASE_URL` | [`_frontend/z-ext-component/src/services/request.js`](_frontend/z-ext-component/src/services/request.js) | axios `baseURL`，缺省 `/api`；登录态读写键为 `zext_token` / `zext_user`（localStorage / sessionStorage） |

starter 侧可调键（`z.ext.*`，`ZExtProperties`）：`z.ext.admin.enabled`（默认 `true`，关掉嵌入式控制面）· `z.ext.admin.base-path`（默认 `/api/ext`）· `z.ext.instance-name` · `z.ext.async.enabled`（默认 `false`，开 ASYNC 异步执行）+ `z.ext.async.core-pool-size` / `max-pool-size` / `queue-capacity` / `thread-name-prefix`。

---

## 🖥️ 前端（`_frontend/`）

```bash
cd _frontend/z-ext-suit          # 应用层 SPA；组件层在同级的 z-ext-component
npm install
npm run dev      # http://localhost:3003，/api 代理到 http://localhost:8888
npm run build    # 产物 dist/
```

页面全部接真 API（`{code,data,message}` 解包，`code!==200` 即 reject；`/api` 前缀 + 相对路径）：

- **Login** — `POST /api/auth/login` 真登录，token 存 `zext_token`，401 自动跳回登录页（共享 `createRequest` 的 `unauthorizedRedirect`）。
- **概览 Dashboard** — 统计为真实数据；"最近活动"假数据已撤，改为「激活中的扩展点」真实表格（`activeImpl` 非空的点）。
- **扩展点管理 / 实现管理** — CRUD、启停、激活切换（受控 Modal 下拉选实现，恢复默认路由走 DELETE）全部走控制面 API。
- **实例选择器 `InstanceSelect`** — Agent 模式：登记业务实例（name + baseUrl）后，页面请求经 `extPath()` 改写为 `/api/ext/instances/{name}/...`，由控制台聚合代理转发；实例存在 `zext_instance`。本地模式不选实例即为直连控制面。

组件层 `z-ext-component` 以 rollup 库形式产出 `pages.js`（整份 `export *` 转发 manifest，不要手挑名字——曾因挑漏 `LoginPage` 导致 suit 侧 rollup 报 not exported），suit 侧通过 `LOCAL_SIBLINGS=1` 可直链组件层源码调试。

`_frontend/pom.xml` 保留了 frontend-maven-plugin（声明 node `v22.14.0` / npm `11.0.0`）与"把 `dist/` 拷进 `z-ext-admin/src/main/resources/static`"的 resources-plugin 配置，但这条 module 在根 POM 里是被注释掉的 —— 为了不触发发布构建去 nodejs.org 下 node，前端构建需手工执行。

---

## 🔌 接口一览

**Java API**（本仓对外的真正接口面）：

| 入口 | 关键方法 |
|------|----------|
| `ExtProxyFactory` | `createProxy(ExtPointDefinition)` · `createProxy(String point)`（point 标识或接口类型描述符）· `createProxy(Class, ExtInvocationHandler)` · `createProxy(pointDef, Consumer<ExtInvocationHandler>)` |
| `ExtRegistry` | `registerPoint` · `getPoint` · `getPointByInterface` · `getAllPoints` · `registerImpl` · `unregisterImpl` / `unregisterPoint` · `getImplementations` · `getEnabledImplementations` · `setActiveImpl` / `getActiveImpl` / `getActiveImplementation` · `setActiveImplStore` / `getActiveImplStore` · `getPlatformImpl` / `getExternalImpl` / `getCustomImpl` · `addListener` / `removeListener` · `clear` |
| `ExtRouter` / `DefaultExtRouter` | `route(pointDef, impls, ctx)` · `getName()`（默认实现 `"default"`）· `setConditionEvaluator`；换策略用 `ExtInvocationHandler#setRouter` |
| `ActiveImplStore`（SPI） | `setActiveImpl` · `getActiveImpl` · `removeActiveImpl` · `getAll` · `clear`；默认 `LocalActiveImplStore`，换 Config/Remote 实现即接入层外置 |
| `ExtAdminProvider`（SPI） | `snapshot` · `activate` · `deactivate` · `setEnabled` · `registerImpl`；默认 `DefaultExtAdminProvider` |
| `ExtRpcInvokerFactory` | `getInvoker(host, port)` · `removeInvoker` · `closeAll` · `getInvokerCount` |
| `ExtAdapterRegistrar` | `register(point, bean)` · `register(point, bean, name)` · `defaultNameOf(Class)` |
| `MockExtImplFactory` | `register(point, interfaceClass, MockSpec)` → 实现名 |
| `PresetRegistrar` | `ensureRegistered()`（幂等；重复调用不覆盖已有同名实现） |
| `ExtConsoleRegistrar` | 启动就绪 → 登记到控制台；容器关闭 → 注销（starter 内 `z.ext.console.*` 配置） |
| `ScriptBackedExtImpl` | `createProxy(pointName, scriptCode, dslType, interfaceClass)` + `ScriptExecutor` SPI 式回调接口 |

**HTTP API：三层各管一段。** `z-ext-core` / `z-ext-web` 仍然 0 个 `@Controller`；接口面在 starter（嵌入式控制面）与 admin（控制台）。响应统一 `{code, data, message}` 包装（业务错误用业务码，不是 HTTP 错误码）：

| 路径 | 方法 | 提供方 | 说明 |
|------|------|--------|------|
| `/api/ext/points` | GET / POST | starter（业务实例内嵌） | 快照 / 登记 point |
| `/api/ext/points/{point}` | GET / PUT / DELETE | starter | 详情 / 改描述版本 / 注销（级联） |
| `/api/ext/points/{point}/activate` | POST / DELETE | starter | 激活切换（`{implName}`）/ 取消激活 —— 介入动作落在这个实例自己的 `ActiveImplStore` |
| `/api/ext/implementations` | GET / POST | starter | 平铺实现清单 / 登记实现（重名不同类 → 409 业务码） |
| `/api/ext/implementations/{id}` | PUT / DELETE | starter | 改 enabled/weight/order/condition/rpc* / 注销 |
| `/api/ext/implementations/mock` | POST | starter | 注入 mock 实现（`{point, name, behavior, result, errorMessage, delayMs}`）——「零代码接管」的操作口 |
| `/api/ext/adapters` | GET / POST | starter | 列出宿主里带 `@ExtAdapter` 的 Bean / 按 Bean 名现场把既有实现挂上扩展点 |
| `/api/auth/login` · `/api/auth/logout` | POST | z-ext-admin | 控制台会话（Bearer 令牌，内存态） |
| `/api/ext/instances` | GET / POST | z-ext-admin | 实例登记表（内存态）：`{name, baseUrl}` |
| `/api/ext/instances/{name}/...` | GET / POST / PUT / DELETE | z-ext-admin | 把上面的 starter 接口代理转发到目标实例（透传 Authorization），实例不可达 → 502 业务码 |

控制台不鉴权实例——它只转发；每个实例由自己的部署侧（网关/内网隔离）决定谁能调它的 `/api/ext/**`。控制台自身的 `/api/ext/**`（除 instances 外）与本地控制面同路径，受同一 Bearer 拦截器保护。

---

## 🧪 测试

```bash
mvn test                # 全 reactor
mvn -pl z-ext-core test # 只要核心
```

实测的测试类（按模块）：

- `z-ext-core/src/test/java/com/zifang/z/ext/`：`ExtRegistryTest`、`ExtRouterTest`、`ExtProxyFactoryTest`、`ExtInvocationHandlerTest`、`ExtIntegrationTest`、`ScriptBackedImplNamingTest`、`ReadmeClaimsTest`、`EnableExtIntegrationTest`，1.1.0 新增 `LocalActiveImplStoreTest`、`DefaultExtAdminProviderTest`、`ExtConditionRoutingTest`、`ExtAsyncExecutionTest`、`ExtChainIsolationTest`（CHAIN 失败隔离：单实现失败继续跑后续、异常不上抛）、`ExtExternalRpcE2ETest`（EXTERNAL 实现经 z-rpc netty 真连接打对端并拿回结果/异常），1.2.0 新增 `preset/PresetRegistrarTest`（预置点注册幂等）、`mock/MockExtImplFactoryTest`（mock 固定返回/抛错/恒拒绝）、`adapter/ExtAdapterRegistrarTest`（既有 Bean 适配成实现后可直接切换）；starter 侧 1.2.0 新增 `preset/PresetGateAutoConfigurationTest`（引依赖即有可被操控的点）、`preset/PresetGateInterceptorTest`（HTTP 闸门放行/503 拒绝/失败放行）、`aop/ExtHookAutoConfigurationTest`（`@ExtHook` 织入后结果被扩展点改写）、`console/ExtConsoleRegistrarTest`（启动登记/关闭注销/控制台不可达不拖挂启动/控制台晚起时退避重试补登记/关闭后停重试）；`test/` 下另备 `OrderService` + PLATFORM/CUSTOM 两个实现与一个 `@ExtPoint` 标注的 `PaymentService` 作夹具，`enableext/` 下另备 `@EnableExt` 两个分支专用的配置类与 `@ExtPoint`/`@ExtImpl` 夹具。
- `z-ext-spring-boot-starter`：`ZExtAutoConfigurationTest`（装配面：默认 Bean、store 装进 ExtRegistry、实例回填、控制面开关）+ `ExtAdminControllerTest`（MockMvc 打嵌入式控制面的全部端点）。
- `z-ext-admin`：`ZExtAdminApplicationTest`（RANDOM_PORT 端到端：登录 → 令牌 → 本机控制面 → 把自己注册成实例的自环代理）。
- `z-ext-web` / `_frontend` 无测试。

`ReadmeClaimsTest` 是本 README 的守卫：每条定量主张（版本号、类数、端口、测试类点名）都从源码重算对账，README 改错数字会直接红。

如实说明剩余缺口：① `ScriptBackedExtImpl` 仍无上下文级测试；② EXTERNAL 已有单 JVM 内的 netty 真连接 e2e（`ExtExternalRpcE2ETest`，进程边界由 socket 承担），但跨真实多进程 + 序列化边界（复杂对象参数/异常回传）的部署级验证仍未做；③ 控制台的实例聚合只验证了同进程自环，跨真实网络的多实例拓扑未测。没有 `*PerformanceTest.java`。

---

## 📦 发布产物与通道

本仓双层身份：**库**（z-ext-core / starter，发 Maven Central）+ **服务**（z-ext-admin 控制台，standalone 部署，不发 Central）。没有 `Dockerfile*` / `docker-compose*.yml` / `deploy/` / `k8s/` / `Makefile` / 任何 `.sh`——控制台的部署形态留给使用方。库的交付物是 Maven Central 上的 `z-ext-core:1.0.1`（pom 由 flatten `oss` 生成、自包含）。

```bash
mvn -Pcentral,central-dryrun clean install    # 形状检查：只跳 javadoc 与签名，不真上传
mvn -Pcentral deploy -DskipTests              # 真发布
```

`central` profile（照 `z-cache` 口径逐件抄来）：`distributionManagement` 指向 `https://central.sonatype.com`（server id `central` 的凭据只放 `~/.m2/settings.xml`，值不落仓）；`maven-deploy-plugin` 3.1.4 置 `skip=true`，上传由 `central-publishing-maven-plugin` 0.7.0 接管（`tokenAuth` + `autoPublish` + `waitUntil=uploaded`）；`maven-source-plugin` 3.3.0 / `maven-javadoc-plugin` 3.11.2（`doclint=none`）/ `maven-gpg-plugin` 3.2.7（口令只从 `-Dgpg.passphrase` 传入，不写进 pom）。因为该插件不认 `maven.deploy.skip`，不可发布模块必须点名：当前 `excludeArtifacts` 里有 `z-ext-web`、`z-ext-admin`、`z-ext-frontend`。

---

## ⚠️ 已知边界（读代码得出，不是猜测）

1. **`@EnableExt.proxyTargetClass` 仍无人读取**：扫描链路已修通 —— `ExtScanner` 补了 `setBasePackages(String[])`（防御性拷贝）承接 `ExtRegistrar` 的 `addPropertyValue`，并有 `EnableExtIntegrationTest` 起真实 Spring 上下文覆盖两个分支；但 `proxyTargetClass` 从注解定义到 Registrar/Scanner 全链路没有读取方，代理始终是 JDK 接口代理。此外 `@EnableExt` 扫到的类只进静态注册表，不注册成 Spring Bean（starter 的 `ExtImplInstanceBinder` 只回填**已是 Bean** 的 `@ExtImpl` 实例）。
2. **跨 JVM 切换仍未闭环到 EXTERNAL**：控制台能把激活指令转发到目标实例，但落点永远是该实例**进程内**的 `ActiveImplStore`；EXTERNAL 实现的注册（`rpcAddress`/`rpcPort`）要靠实例启动时自己登记，控制台 `registerImpl` 只影响本实例注册表。多实例间没有配置同步——每个实例的激活态独立。
3. **本地实现不受 Spring 管理的场景仍在**：`invokeLocal` 对没有 `instance` 的定义走 `getDeclaredConstructor().newInstance()`，此时 `@Autowired` / `@Value` 不生效；starter 装配下 `@ExtImpl` Bean 会被 `ExtImplInstanceBinder` 回填，不受影响的只剩编程式注册且未 `setInstance` 的定义。
4. **注册表是 JVM 全局静态状态**，不随 Spring 上下文销毁而隔离，多上下文/测试并发共享同一份 point 与激活映射；控制台与实例的令牌、实例登记表都是内存态，重启即失。
5. **控制台无内建账号体系**：单用户凭据走环境变量，无角色/审计日志；`enabled` 切换影响的是资金流一类真实业务路径，操作审计要靠部署侧网关日志。
6. **`_doc/` 有两份文档已过期**：`00-overview.md` 写"SPI/ServiceLoader 插件发现、自定义 ClassLoader 沙箱、Spring Boot 2.7.12"，代码里没有对应实现；`01-module-structure.md` 仍称父项目是 `com.zifang:z-opc:1.0.0-SNAPSHOT` 且"单独 clone 无法 Maven build"，与现在自包含继承 `z-boot-parent` 的 POM 相反。以本 README 与 POM/源码为准。

---

## 📝 近期变更（来自 `git log`）

| 提交 | 内容 |
|------|------|
| 2026-11-19 | `invokeChain` 落地**失败隔离**：CHAIN 点单个实现抛异常只记日志、继续跑后续实现（多消费者资金链路安全语义，`ExtChainIsolationTest` 回归）；starter `extProxyBuilder` 改 `getIfUnique` 取 `Executor` —— 修复宿主同时存在 `applicationTaskExecutor` + `taskScheduler`（`@EnableScheduling`）时的 `NoUniqueBeanDefinitionException` 启动拖挂（z-biz e2e 实测抓到）；触发端接线完成：z-trade `OrderAfterPayExtHook` 改走 `ExtProxyBuilder` 代理（点 `trade.order.after-pay` 定为 CHAIN），z-biz 应收登记 e2e 全绿 |
| 2026-11-19 | 1.1.0：前端 SPA 全面接真 API（真登录 + 401 自动跳转、Dashboard 真统计/真激活表、扩展点与实现 CRUD、激活切换受控 Modal、Agent 模式实例选择器 `InstanceSelect` + `extPath()` 代理改写）；ReadmeClaimsTest 锚点同步 |
| 2026-11-18 | 1.1.0：注册表可变状态收口 `ActiveImplStore` SPI；`condition` 落地 SpEL 条件路由、`weight` 同序加权、ASYNC 可选执行器；新建 `z-ext-spring-boot-starter`（自动装配 + 嵌入式控制面）；`z-ext-admin` 回归 reactor 成真控制台（登录 + 实例聚合代理） |
| `4f40b8f` | 坐标从 `com.zifang` 换成 `io.github.yuku123:1.0.0`，`com.zifang:z-rpc-core` 幻影依赖换 `z-rpc-remoting`（类集与 `setParameters`→`setArguments` 签名比对后改的），补中央发布通道 |
| `673fb6e` · `44dc640` | 消费入口抬到 `z-boot-parent:1.2.4`（组织消费者轮，先前是 1.0.19；现 1.2.6），删掉仓内所有字面版本钉 |
| `67b6615` | `ExtProxyFactory.createProxy(String)` 支持传扩展点接口的类型描述符，按接口类反查扩展点 |
| `6ccab93` | 对齐 `z-boot-dependencies` 口径（netty 4.1.138.Final） |
| 2026-09-06 | 从 `idea_workplace/z-opc` monorepo 拆出独立仓（历史日期，见 `01-module-structure.md`） |

---

## 📄 License

MIT，见仓库根 [`LICENSE`](LICENSE)（`Copyright (c) 2026 z-opc-foundation`）与根 POM 的 `<licenses>` 声明。此前本节写的"Internal use only / 版权属于 z-biz"与磁盘事实不符，已纠正。

_Maintained by the z-opc-foundation organization._

---

## 文档目录

- [`_doc/008_troubleshooting/`](_doc/008_troubleshooting/) — 踩坑与失败要点（控制台点击普查实录）
本项目文档统一收口在 `_doc/` 下：

- [`_doc/001_arch/`](_doc/001_arch/) — 架构与设计文档：
  - [`00-overview.md`](_doc/001_arch/00-overview.md) — 项目总览与愿景（注意：ClassLoader 沙箱 / SPI 插件发现等尚未实现）
  - [`01-module-structure.md`](_doc/001_arch/01-module-structure.md) — 从 z-opc 拆分说明（父项目描述已过期，见「已知边界」第 6 条）
  - [`08-product-design.md`](_doc/001_arch/08-product-design.md) — 产品定位与功能矩阵
  - [`09-technical-design.md`](_doc/001_arch/09-technical-design.md) — 技术方案（其中的 `ExtensionPoint` / `Extension` 命名为设计稿，落码为 `ExtPoint` / `ExtImpl`）
- `_doc/002_deploy/` — 目前为空目录（本仓无可部署资产）
- `_doc/003_script/` — 目前为空目录
- `_doc/004_skill/` — 目前为空目录

关键源码入口：[`z-ext-core/pom.xml`](z-ext-core/pom.xml)、[`ExtRegistry`](z-ext-core/src/main/java/com/zifang/z/ext/core/registry/ExtRegistry.java)、[`ExtProxyFactory`](z-ext-core/src/main/java/com/zifang/z/ext/core/proxy/ExtProxyFactory.java)、[`ExtIntegrationTest`](z-ext-core/src/test/java/com/zifang/z/ext/ExtIntegrationTest.java)、[`_frontend/z-ext-suit/src/App.jsx`](_frontend/z-ext-suit/src/App.jsx)、[`_frontend/z-ext-suit/vite.config.js`](_frontend/z-ext-suit/vite.config.js)。

---

<!-- z-opc-foundation-sibling-registry:v1 -->
## 🔗 兄弟仓全景（z-opc-foundation 组织）

本仓隶属于 [z-opc-foundation](https://github.com/orgs/z-opc-foundation/repositories) 组织——一人公司基座的全部自研件（现存 61 仓，含私有仓；口径 2026-11-24 GitHub org API 实读）。

| 仓 | 一句话说明 |
|---|---|
| [z-agent](https://github.com/z-opc-foundation/z-agent) | 云端 Agent 平台的库与 starter——ReAct/Plan 实现 + 注册中心 + REST 网关 + 只读控制面 |
| [z-agent-kernel](https://github.com/z-opc-foundation/z-agent-kernel) | Agent 框架基座——消息/工具/记忆/provider/会话 SPI + 轻量默认实现（纯 POJO 无 Spring） |
| [z-asset](https://github.com/z-opc-foundation/z-asset) | 企业资产台账——实物设备全生命周期 + 域名/证书/许可到期提醒 |
| [z-audit](https://github.com/z-opc-foundation/z-audit) | 审计与合规留痕——事件模型 + 哈希链 + 事件出口 SPI + 留存期策略 |
| [z-biz](https://github.com/z-opc-foundation/z-biz) | 商务外壳域——客户/合同/票据/对账四聚合 |
| [z-biz-erp](https://github.com/z-opc-foundation/z-biz-erp) | 进销存 ERP——商品/客商/采购/销售/库存流水/应收应付核销（z-biz 业务线） |
| [z-biz-security](https://github.com/z-opc-foundation/z-biz-security) | 业务数据安全防护入口——场景化策略注册 + 安全线四段流水线对外编排 |
| [z-boot](https://github.com/z-opc-foundation/z-boot) | Spring Boot Starter 聚合仓 + 版本权威（火车制）——唯一消费入口 z-boot-parent |
| [z-bot](https://github.com/z-opc-foundation/z-bot) | 本地 agent 应用——终端 CLI + HTTP/SSE web 控制台 + 多通道 gateway（飞书/钉钉/webhook） |
| [z-cache](https://github.com/z-opc-foundation/z-cache) | RESP2 协议兼容的分布式内存缓存——Java 8 + Netty，Redis 客户端零修改直连 |
| [z-camuda](https://github.com/z-opc-foundation/z-camuda) | 【已退役·归档】基于 Camunda 的工作流封装层（原 z-wf 改名，与现行 z-wf 无共同历史） |
| [z-cognition](https://github.com/z-opc-foundation/z-cognition) | 业务认知域/认知舱——术语/概念/证据链 + memory/kb/ontology 渐进式模块 |
| [z-config](https://github.com/z-opc-foundation/z-config) | 分布式配置中心 + 服务注册中心（类 Nacos）——Netty 长连接推送 + React 控制台 |
| [z-ctc](https://github.com/z-opc-foundation/z-ctc) | 4A 中心——认证/授权/账户/审计，多租户 JWT + SSO 跨子域 |
| [z-dms](https://github.com/z-opc-foundation/z-dms) | 数据源管控底座——统一登记、统一建池、统一探活、变更统一推送 |
| [z-env](https://github.com/z-opc-foundation/z-env) | 本地中间件与基础设施环境——31 个 docker-compose 场景 + make 一键运维 |
| [z-etl](https://github.com/z-opc-foundation/z-etl) | ETL 引擎——DataX 式线性作业 + Flink 式 DAG 流管道（键控状态/水位/窗口） |
| [z-ext](https://github.com/z-opc-foundation/z-ext) | Java 扩展点框架——@ExtPoint/@ExtImpl 运行期选实现，五档侵入方式 **（本仓）** |
| [z-frontend-common](https://github.com/z-opc-foundation/z-frontend-common) | 全仓共享前端基座——AppLayout/LoginPage 等前端规范的唯一承载 |
| [z-frontend-markdown](https://github.com/z-opc-foundation/z-frontend-markdown) | Markdown 通用共享组件包（npm）——MarkdownLiveEditor/NoteMarkdown/MermaidBlock 单一真源 |
| [z-frontend-render](https://github.com/z-opc-foundation/z-frontend-render) | 平台共享前端渲染引擎——后端 schema 是唯一事实源，报表与低代码共用 |
| [z-graph](https://github.com/z-opc-foundation/z-graph) | 独立图数据库——MVCC 版本链存储 + OpenCypher 子集 + Bolt 风格协议 + React 控制台 |
| [z-gw](https://github.com/z-opc-foundation/z-gw) | 自研高性能 API 网关——Netty 手写运行时，Route/Predicate/Filter 模型可嵌 Spring Boot |
| [z-indexer](https://github.com/z-opc-foundation/z-indexer) | 代码索引服务——仓库注册表 + 定时重建索引 + 符号/调用链查询 |
| [z-introduce](https://github.com/z-opc-foundation/z-introduce) | 组织仓库展示站——全仓实测元数据汇总成可搜索静态站点 |
| [z-lc](https://github.com/z-opc-foundation/z-lc) | 低代码平台——DB 驱动通用 CRUD 引擎 + 报表/模型设计框架 |
| [z-llm-agent](https://github.com/z-opc-foundation/z-llm-agent) | CLI Agent 服务化——把本机 CLI agent（CC/Hermes/Qoder 等）暴露成 OpenAI 兼容对话 API |
| [z-llm-gateway](https://github.com/z-opc-foundation/z-llm-gateway) | LLM 网关——OpenAI/Anthropic 双协议面 + 多 vendor 凭据池、限流与 token 记账 |
| [z-llm-msghub](https://github.com/z-opc-foundation/z-llm-msghub) | msgHub 中心消息路由——WebSocket 中心枢纽 + Agent 侧客户端 SDK |
| [z-mask](https://github.com/z-opc-foundation/z-mask) | 脱敏与遮蔽引擎——按敏感命中位置遮蔽，策略即数据 |
| [z-mcp](https://github.com/z-opc-foundation/z-mcp) | MCP 服务端 + 上游聚合网关——Java 8 手写协议层，Spring Boot 自动装配 |
| [z-meta](https://github.com/z-opc-foundation/z-meta) | 元数据管理平台——租户/应用/接口清单登记 + @BizModel 业务模型采集 |
| [z-mill](https://github.com/z-opc-foundation/z-mill) | 资料磨坊——全格式文档转 Markdown/JSON/纯文本/HTML（对标 MinerU） |
| [z-mist](https://github.com/z-opc-foundation/z-mist) | KMS 分布式密钥/秘密管理平台——集中加密存储 + Netty 协议 + starter 嵌入 |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 自研分布式消息队列——NameServer+Broker+Store，形状对标 RocketMQ、自有 Netty 协议 |
| [z-msg](https://github.com/z-opc-foundation/z-msg) | 多通道消息 + WebSocket 实时下发基础设施——短信/邮件/站内信/Webhook/IM 平台 |
| [z-observer](https://github.com/z-opc-foundation/z-observer) | 可观测性域——指标/日志/链路 + 告警引擎 |
| [z-opc](https://github.com/z-opc-foundation/z-opc) | 一人公司统一后端+统一前端——单 JVM all-in-one 承载协作/Agent/RPA/交易/资产等业务域与自研中间件 |
| [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) | 组织治理大脑——事实表、强制规范、跨仓工具集、新机器恢复钥匙（本清单权威持有处） |
| [z-opcs](https://github.com/z-opc-foundation/z-opcs) | 任务撮合/服务市场——用户付费发布诉求，持有者接单完成，金额自动瓜分 |
| [z-ops](https://github.com/z-opc-foundation/z-ops) | 个人公司运维平台——部署单元/镜像构建/WebShell/K8s 巡检 + Ops Agent 执行器 |
| [z-oss](https://github.com/z-opc-foundation/z-oss) | 对象存储服务——自持 local 后端 + aliyun/s3 对接层，starter 注入 OssTemplate |
| [z-product](https://github.com/z-opc-foundation/z-product) | 产品设计信息汇总仓（开发域 PM 工件） |
| [z-qa](https://github.com/z-opc-foundation/z-qa) | 步骤式 API 链路测试平台——用例/计划/执行/报告/质量看板（对标 TestRail·MeterSphere） |
| [z-rpa](https://github.com/z-opc-foundation/z-rpa) | RPA 产品皮——应用市场/需求中心（执行归 z-script，触发归 z-schedule） |
| [z-rpc](https://github.com/z-opc-foundation/z-rpc) | Java 8 + Netty RPC 框架——微内核 + SPI 插件化，形状参照 Dubbo/gRPC |
| [z-schedule](https://github.com/z-opc-foundation/z-schedule) | 分布式任务调度中枢（对标 XXL-Job）——选主 + 时间环排期，starter 嵌入宿主 |
| [z-script](https://github.com/z-opc-foundation/z-script) | 多 DSL 脚本平台 + Mock 平台——沙箱执行/版本灰度/可注册 MCP Tool，starter 内嵌 |
| [z-security](https://github.com/z-opc-foundation/z-security) | 数据安全域编排层——场景→策略→识别→脱敏→水印→审计 四段流水线编排 |
| [z-sense](https://github.com/z-opc-foundation/z-sense) | 敏感识别与分类分级引擎——数据安全线底座（规则目录 + 可插拔识别器 + 分类分级） |
| [z-skill](https://github.com/z-opc-foundation/z-skill) | Skill 平台聚合器——归一化各家 Skill 形状，再按各家 API 形状发回 |
| [z-surl](https://github.com/z-opc-foundation/z-surl) | 短网址服务——短码生成/解析/命中统计，starter 一行集成 |
| [z-task](https://github.com/z-opc-foundation/z-task) | 任务协作平台——项目空间/看板/任务卡片（看板范式，对标 Trello·Teambition） |
| [z-team](https://github.com/z-opc-foundation/z-team) | 数字员工协同域——全栈独立仓，自 z-opc 毕业 |
| [z-trade](https://github.com/z-opc-foundation/z-trade) | 交易域独立仓——自 z-opc reactor 迁出 |
| [z-util](https://github.com/z-opc-foundation/z-util) | 底层 Java 工具库——89 个模块 POM，Java 8/17 口径，按需引用按需升级 |
| [z-vector](https://github.com/z-opc-foundation/z-vector) | 自研 Java 向量数据库——嵌入式+可独立部署，ANN+Payload 过滤，兼容 Qdrant REST |
| [z-voice-demo](https://github.com/z-opc-foundation/z-voice-demo) | 语音链路 demo——流式识别→kernel LLM→流式 TTS→可打断 的契约验证实现 |
| [z-watermark](https://github.com/z-opc-foundation/z-watermark) | 水印引擎——显式文字/图片隐写/盲水印，三种技术成对「打+验」 |
| [z-webide](https://github.com/z-opc-foundation/z-webide) | 企业级 WebIDE 后端——Git 仓/共享目录秒变 code-server 实例，全生命周期管理 |
| [z-wf](https://github.com/z-opc-foundation/z-wf) | 自研流程引擎——不依赖 Camunda/Flowable，定义层与运行时全自研 |

**已退役仓**（迁至 [z-trash](https://github.com/z-trash) 组织，archived 只读）：

| 仓 | 说明 |
|---|---|
| [z-agent-proxy](https://github.com/z-trash/z-agent-proxy) | 三方 coding agent 适配代理（Aider/Cursor → kernel Agent SPI），2026-10-26 退役 |
| [z-kb](https://github.com/z-trash/z-kb) | 自研知识库系统（对标 LightRAG/RAGFlow），2026-11 并入 z-cognition 认知舱后退役 |
| [z-ontology](https://github.com/z-trash/z-ontology) | 本体层（TBox/ABox/RDFS 推理），并入 z-cognition 后退役 |

另有 [z-report](https://github.com/z-opc-foundation/z-report)（报表域，2026-10-11 撤销并入 z-lc（远端已删除））。

总清单权威持有处在 [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) `008_组织规范/005_兄弟仓清单.md`。
