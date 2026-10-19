# 扩展平台SDK - 技术方案设计

## 1. 架构概述

### 1.1 核心定位

- **平台侧**：提供SDK核心能力，定义扩展点（接口标记），实现路由分发
- **业务侧**：实现具体的业务逻辑，可以替换平台侧实现
- **运行时**：通过动态代理实现"本地实现"与"三方实现"的切换

### 1.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         应用层 (A应用)                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    SDK 核心层                            │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
│  │  │  扩展点注册  │  │  动态代理    │  │  路由分发    │     │   │
│  │  │  标记器     │  │  处理器     │  │  策略引擎    │     │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    扩展实现层                            │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
│  │  │  本地实现    │  │  三方实现    │  │  自定义实现  │     │   │
│  │  │ (Platform) │  │ (External) │  │ (Custom)   │     │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 核心设计

### 2.1 扩展点标记 (Extension Point)

```java
// 定义扩展点注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExtensionPoint {
    String value();           // 扩展点唯一标识
    ExtensionType type();     // 扩展点类型
    int order() default 0;   // 优先级
}

public enum ExtensionType {
    SYNC,     // 同步执行
    ASYNC,    // 异步执行
    CHAIN,    // 链式执行 (可多个实现依次执行)
}
```

### 2.2 实现注册 (Implementation Registry)

```java
// 实现注册注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Extension {
    String point();           // 对应的扩展点标识
    String name();            // 实现名称
    ExtensionImpl impl() default ExtensionImpl.PLATFORM;
}

public enum ExtensionImpl {
    PLATFORM,   // 平台默认实现
    EXTERNAL,   // 三方实现
    CUSTOM,     // 自定义实现（业务方）
}
```

### 2.3 动态代理与路由分发

```java
// 扩展点代理工厂
public class ExtensionProxyFactory {

    public static <T> T createProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class[]{interfaceClass},
            new ExtensionInvocationHandler(interfaceClass)
        );
    }
}

// 调用处理器 - 核心路由逻辑
public class ExtensionInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 获取扩展点配置
        ExtensionPointConfig config = getExtensionConfig(method.getDeclaringClass());

        // 2. 根据路由策略选择实现
        ExtensionSelector selector = getSelector(config.type());
        ExtensionImplementation selectedImpl = selector.select(config, method, args);

        // 3. 执行选中实现
        return selectedImpl.invoke(method, args);
    }
}
```

### 2.4 路由策略引擎

```java
// 路由策略接口
public interface ExtensionSelector {
    ExtensionImplementation select(
        ExtensionPointConfig config,
        Method method,
        Object[] args
    );
}

// 内置策略实现
public class DefaultExtensionSelector implements ExtensionSelector {

    @Override
    public ExtensionImplementation select(
        ExtensionPointConfig config,
        Method method,
        Object[] args
    ) {
        // 1. 检查是否有三方实现注册
        ExtensionImplHolder holder = ExtensionRegistry.get(config.point());

        if (holder != null && holder.hasExternal()) {
            return holder.getExternal();  // 使用三方实现
        }

        // 2. 回退到平台默认实现
        return holder != null ? holder.getPlatform() : null;
    }
}
```

---

## 3. 核心流程

### 3.1 启动时流程

```
应用启动
    │
    ▼
扫描 @ExtensionPoint 注解 ──► 注册扩展点到 ExtensionRegistry
    │
    ▼
扫描 @Extension 注解  ──►  注册实现到对应扩展点
    │
    ▼
创建动态代理对象 ──►  替换原有接口实现
    │
    ▼
应用正常启动
```

### 3.2 运行时调用流程

```
业务调用接口方法
    │
    ▼
进入动态代理 (ExtensionInvocationHandler)
    │
    ▼
获取扩展点配置 & 路由策略
    │
    ▼
根据策略选择实现 (本地/三方/自定义)
    │
    ▼
执行选中的实现
    │
    ▼
返回结果
```

---

## 4. 高级特性

### 4.1 路由规则配置

支持多种路由规则，灵活控制使用哪个实现：

```java
// 方式1: 配置化路由规则 (YAML/JSON)
extension:
  point: "order.create"
  route:
    type: condition
    condition: "env == 'prod' ? external : platform"
    external:
      name: "alibaba-dubbo"
      config:
        group: "production"
    platform:
      name: "default"

// 方式2: 规则引擎集成
extension:
  point: "payment.process"
  route:
    type: rule
    rule: "drools"
    file: "payment-route.drl"
```

### 4.2 扩展点类型详解

#### 4.2.1 SYNC - 同步执行

```java
// 只会选择一个实现执行
@ExtensionPoint(value = "user.validate", type = ExtensionType.SYNC)
public interface UserValidator {
    boolean validate(User user);
}
```

#### 4.2.2 ASYNC - 异步执行

```java
// 异步执行，不阻塞主流程
@ExtensionPoint(value = "notification.send", type = ExtensionType.ASYNC)
public interface NotificationService {
    void send(String message);
}
```

#### 4.2.3 CHAIN - 链式执行

```java
// 多个实现按顺序依次执行，结果可汇聚
@ExtensionPoint(value = "log.record", type = ExtensionType.CHAIN)
public interface LogRecorder {
    void record(LogEntry entry);
}
// 使用场景：日志可以同时记录到文件、数据库、第三方服务
```

### 4.3 热更新支持

```java
// 运行时动态切换实现
ExtensionRegistry.switchImplementation(
    "order.create",      // 扩展点
    "external-v2",       // 新实现名称
    SwitchType.IMMEDIATE // 立即生效 / 下次调用生效
);

// 动态注册新实现
ExtensionRegistry.register(
    "order.create",
    new CustomOrderService() // 动态注册自定义实现
);
```

### 4.4 监控与治理

```java
// 内置监控指标
@ExtensionPoint(value = "payment.process", type = ExtensionType.SYNC)
public interface PaymentService {
    // 自动记录：
    // - 调用耗时
    // - 成功率
    // - 选择的实现
    // - 切换次数等
}
```

---

## 5. 目录结构设计

```
ext-platform-sdk/
├── core/                          # 核心模块
│   ├── annotation/               # 注解定义
│   │   ├── ExtensionPoint.java
│   │   ├── Extension.java
│   │   └── EnableExtension.java
│   ├── registry/                 # 注册中心
│   │   ├── ExtensionRegistry.java
│   │   ├── ExtensionPointConfig.java
│   │   └── ExtensionImplHolder.java
│   ├── proxy/                    # 动态代理
│   │   ├── ExtensionProxyFactory.java
│   │   ├── ExtensionInvocationHandler.java
│   │   └── ProxyGenerator.java
│   ├── selector/                 # 路由策略
│   │   ├── ExtensionSelector.java
│   │   ├── DefaultExtensionSelector.java
│   │   └── ConditionExtensionSelector.java
│   └── context/                  # 上下文
│       ├── ExtensionContext.java
│       └── ExtensionAttributes.java
│
├── spi/                          # SPI扩展机制
│   └── META-INF/services/
│       ├── ExtensionSelector
│       └── ExtensionLoader
│
├── starter/                      # Spring Boot Starter
│   ├── ExtPlatformAutoConfiguration.java
│   ├── properties/
│   │   └── ExtPlatformProperties.java
│   └── endpoints/                # 端点
│       ├── ExtensionEndpoint.java
│       └── SwitchEndpoint.java
│
├── monitor/                      # 监控模块
│   ├── metrics/
│   │   └── ExtensionMetrics.java
│   └── tracing/
│       └── ExtensionTracer.java
│
└── docs/                         # 文档
    ├── user-guide.md
    └── developer-guide.md
```

---

## 6. 技术选型

| 能力     | 方案                                   | 说明                |
|--------|--------------------------------------|-------------------|
| 动态代理   | JDK Proxy / Javassist / ByteBuddy    | 推荐 ByteBuddy，能力更强 |
| 配置管理   | SpEL 表达式 / JSON Schema               | 灵活的条件判断           |
| SPI 加载 | Java SPI / Spring Factories          | 扩展自定义实现           |
| 监控     | Micrometer / OpenTelemetry           | 兼容现有监控系统          |
| 配置中心   | Nacos / Apollo / Spring Cloud Config | 可对接外部配置中心         |

---

## 7. 集成示例

### 7.1 定义扩展点接口

```java
// 平台侧 - 定义扩展点接口
@ExtensionPoint(value = "order.create", type = ExtensionType.SYNC)
public interface OrderCreateService {
    Order create(CreateOrderRequest request);
}
```

### 7.2 平台默认实现

```java
// 平台侧 - 默认实现
@Extension(point = "order.create", name = "default", impl = ExtensionImpl.PLATFORM)
public class DefaultOrderCreateService implements OrderCreateService {

    @Override
    public Order create(CreateOrderRequest request) {
        // 平台侧的默认业务逻辑
        return orderRepository.save(request.toOrder());
    }
}
```

### 7.3 业务方三方实现

```java
// 业务方 - 替换为三方实现
@Extension(point = "order.create", name = "external", impl = ExtensionImpl.EXTERNAL)
public class ExternalOrderCreateService implements OrderCreateService {

    private final ExternalOrderGateway gateway;

    @Override
    public Order create(CreateOrderRequest request) {
        // 调用三方系统
        ExternalOrderResponse response = gateway.createOrder(request);
        return response.toOrder();
    }
}
```

### 7.4 使用方式

```java
// 业务应用启动类
@SpringBootApplication
@EnableExtension(basePackages = "com.platform")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// 业务注入使用
@Service
public class OrderBusinessService {

    @Autowired
    private OrderCreateService orderCreateService;  // 实际是代理对象

    public Order createOrder(CreateOrderRequest request) {
        // 调用时由SDK决定执行哪个实现
        return orderCreateService.create(request);
    }
}
```