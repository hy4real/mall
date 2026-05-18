# mall-common 核心类速查（带 Java 知识点注释）

> mall-common 是整个项目的基础设施层，被所有业务模块依赖。
> 共 15 个文件，~1250 行。读完后你会掌握：泛型、异常体系、AOP、Redis 配置、设计模式。

---

## 一、模块全景图

```
mall-common
├── api/                    ← API 返回格式（全局依赖）
│   ├── IErrorCode          ← 错误码接口（2 个方法）
│   ├── ResultCode          ← 错误码枚举（5 个常量）
│   ├── CommonResult<T>     ← 统一返回包装（被引用 49 次）
│   └── CommonPage<T>       ← 统一分页包装（被引用 34 次）
├── exception/              ← 异常处理（全局依赖）
│   ├── ApiException        ← 业务异常
│   ├── Asserts             ← 断言工具
│   └── GlobalExceptionHandler ← 全局异常捕获
├── config/                 ← 基础配置（子模块继承）
│   ├── BaseRedisConfig     ← Redis 序列化 + 缓存管理器
│   └── BaseSwaggerConfig   ← Swagger 文档配置（模板方法）
├── domain/                 ← 数据对象
│   ├── WebLog              ← 请求日志 DTO
│   └── SwaggerProperties   ← Swagger 配置属性
├── service/                ← Redis 操作封装
│   ├── RedisService        ← 接口（30+ 方法）
│   └── impl/RedisServiceImpl ← 实现（委托给 RedisTemplate）
├── log/                    ← AOP 日志
│   └── WebLogAspect        ← 请求日志切面
└── util/
    └── RequestUtil         ← IP 地址获取
```

---

## 二、逐文件解读

### 2.1 IErrorCode — 错误码接口

```java
// 文件: api/IErrorCode.java（17 行）
public interface IErrorCode {
    long getCode();      // 返回码，如 200、500
    String getMessage(); // 错误信息，如 "操作成功"
}
```

**Java 知识点：interface 接口**
- 接口定义的是「契约」——任何实现这个接口的类，都必须提供 `getCode()` 和 `getMessage()`
- 为什么要用接口而不是直接写常量？因为这样 `CommonResult.failed(IErrorCode)` 可以接受任何实现了 `IErrorCode` 的类型，不绑定具体的枚举
- 这是**面向接口编程**的基本体现

---

### 2.2 ResultCode — 错误码枚举

```java
// 文件: api/ResultCode.java（28 行）
public enum ResultCode implements IErrorCode {    // ← 枚举实现接口
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(404, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限");

    private long code;
    private String message;

    private ResultCode(long code, String message) {  // ← 枚举的构造器是 private
        this.code = code;
        this.message = message;
    }

    // 实现 IErrorCode 接口的两个方法
    public long getCode() { return code; }
    public String getMessage() { return message; }
}
```

**Java 知识点：enum 枚举**
- `enum` 在 Java 里是特殊的 class，每个枚举值（如 `SUCCESS`）都是这个类的实例
- 枚举的构造器**必须是 private**，因为 Java 不允许外部创建新的枚举值
- `implements IErrorCode` 让枚举可以被当作接口类型使用：`IErrorCode code = ResultCode.SUCCESS;`
- 这是**类型安全常量**的模式——比 `public static final int SUCCESS = 200` 更安全，因为编译器会检查类型

---

### 2.3 ApiException — 业务异常

```java
// 文件: exception/ApiException.java（32 行）
public class ApiException extends RuntimeException {  // ← 继承非受检异常
    private IErrorCode errorCode;

    public ApiException(IErrorCode errorCode) {
        super(errorCode.getMessage());  // 把错误信息传给父类 RuntimeException
        this.errorCode = errorCode;
    }

    public ApiException(String message) {
        super(message);  // 直接传自定义消息
    }

    public ApiException(Throwable cause) {
        super(cause);  // 包装其他异常
    }

    public IErrorCode getErrorCode() {
        return errorCode;
    }
}
```

**Java 知识点：异常体系**
- Java 异常分两种：
  - **Checked（受检）**：必须处理（try-catch 或 throws），如 `IOException`
  - **Unchecked（非受检）**：`RuntimeException` 的子类，不强制处理
- `ApiException` 继承 `RuntimeException`，所以调用方不需要 try-catch
- 为什么要用非受检？因为业务异常（如"库存不足"）是正常业务流程的一部分，不应该强制每个调用都写 try-catch
- `GlobalExceptionHandler` 会统一捕获并转为 JSON 返回给前端

**设计模式：异常包装**
- 构造器 `ApiException(Throwable cause)` 可以把底层异常包装起来，保留完整堆栈
- 例如 `catch (IOException e) { throw new ApiException("文件读取失败", e); }` — 不会丢失原始异常信息

---

### 2.4 Asserts — 断言工具

```java
// 文件: exception/Asserts.java（17 行）
public class Asserts {
    public static void fail(String message) {
        throw new ApiException(message);        // ← 直接抛异常
    }

    public static void fail(IErrorCode errorCode) {
        throw new ApiException(errorCode);      // ← 带错误码抛异常
    }
}
```

**用法：**
```java
if (stock < quantity) {
    Asserts.fail("库存不足，无法下单");  // 比 throw new ApiException(...) 更简洁
}
```

**Java 知识点：静态工具类**
- 所有方法都是 `static`，不需要创建实例
- 类名本身表达意图：`Asserts.fail()` 比 `throw new ApiException()` 更有语义

---

### 2.5 CommonResult\<T\> — 统一返回格式

```java
// 文件: api/CommonResult.java（133 行）
public class CommonResult<T> {      // ← 泛型类，T 是返回数据的类型
    private long code;
    private String message;
    private T data;                  // ← 泛型字段，可以是任何类型

    protected CommonResult() {}     // ← protected：不让外部 new，只能通过静态工厂创建

    protected CommonResult(long code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 静态工厂方法 —— 创建实例的推荐方式
    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> CommonResult<T> failed(IErrorCode errorCode) {
        return new CommonResult<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> CommonResult<T> failed(String message) {
        return new CommonResult<>(ResultCode.FAILED.getCode(), message, null);
    }

    public static <T> CommonResult<T> validateFailed(String message) {
        return new CommonResult<>(ResultCode.VALIDATE_FAILED.getCode(), message, null);
    }

    public static <T> CommonResult<T> unauthorized(T data) {
        return new CommonResult<>(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage(), data);
    }
}
```

**使用示例：**
```java
// Controller 中的实际用法
@GetMapping("/{id}")
public CommonResult<PmsProduct> getItem(@PathVariable Long id) {
    PmsProduct product = productService.getById(id);
    return CommonResult.success(product);    // {"code":200,"message":"操作成功","data":{...}}
}

@PostMapping("/delete/{id}")
public CommonResult<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return CommonResult.success(null);       // {"code":200,"message":"操作成功","data":null}
}
```

**Java 知识点：泛型 Generics**
- `CommonResult<T>` 中的 `T` 是类型参数，使用时才确定具体类型
- `CommonResult<PmsProduct>` — data 字段自动是 PmsProduct 类型
- `CommonResult<Void>` — 表示没有返回数据
- `CommonResult<List<PmsProduct>>` — 返回列表
- `<T> CommonResult<T> success(T data)` — 方法上的 `<T>` 声明这个方法自己是泛型的

**设计模式：静态工厂方法**
- 构造器是 `protected`，外部只能通过 `CommonResult.success()` 创建
- 好处 1：方法名表达语义（`success` vs `failed` vs `validateFailed`）
- 好处 2：可以返回子类型（虽然这里没用上）
- 好处 3：可以缓存实例（虽然这里也没用上）

**实际 JSON 输出：**
```json
{"code": 200, "message": "操作成功", "data": {"id": 1, "name": "iPhone 14"}}
```

---

### 2.6 CommonPage\<T\> — 统一分页格式

```java
// 文件: api/CommonPage.java（100 行）
public class CommonPage<T> {
    private Integer pageNum;     // 当前页码
    private Integer pageSize;    // 每页数量
    private Integer totalPage;   // 总页数
    private Long total;           // 总条数
    private List<T> list;         // 分页数据

    // 适配器方法 1：适配 PageHelper（mall-admin/mall-portal 使用）
    public static <T> CommonPage<T> restPage(List<T> list) {
        PageInfo<T> pageInfo = new PageInfo<>(list);  // PageHelper 自动识别分页信息
        CommonPage<T> result = new CommonPage<>();
        result.setTotalPage(pageInfo.getPages());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setTotal(pageInfo.getTotal());
        result.setList(pageInfo.getList());
        return result;
    }

    // 适配器方法 2：适配 Spring Data（mall-search-modern 使用）
    public static <T> CommonPage<T> restPage(Page<T> pageInfo) {
        CommonPage<T> result = new CommonPage<>();
        result.setTotalPage(pageInfo.getTotalPages());
        result.setPageNum(pageInfo.getNumber());
        result.setPageSize(pageInfo.getSize());
        result.setTotal(pageInfo.getTotalElements());
        result.setList(pageInfo.getContent());
        return result;
    }
}
```

**Java 知识点：适配器模式**
- 两个不同的分页库（PageHelper 的 `PageInfo` 和 Spring Data 的 `Page`）产出不同格式的分页数据
- `CommonPage.restPage()` 统一它们的接口，让 Controller 不需要关心底层用的是什么分页库
- **方法重载（Overload）**：两个 `restPage` 方法名字相同但参数类型不同

---

### 2.7 GlobalExceptionHandler — 全局异常处理

```java
// 文件: exception/GlobalExceptionHandler.java（68 行）
@ControllerAdvice   // ← 声明这是全局异常处理器
public class GlobalExceptionHandler {

    @ResponseBody     // ← 返回 JSON 而不是视图
    @ExceptionHandler(value = ApiException.class)  // ← 拦截 ApiException
    public CommonResult handle(ApiException e) {
        if (e.getErrorCode() != null) {
            return CommonResult.failed(e.getErrorCode());
        }
        return CommonResult.failed(e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)  // ← @Valid 校验失败
    public CommonResult handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String message = fieldError.getField() + fieldError.getDefaultMessage();
        return CommonResult.validateFailed(message);
    }

    @ResponseBody
    @ExceptionHandler(value = SQLSyntaxErrorException.class)  // ← SQL 语法错误
    public CommonResult handleSQLSyntaxErrorException(SQLSyntaxErrorException e) {
        if (e.getMessage().contains("denied")) {
            return CommonResult.failed("演示环境暂无修改权限！");
        }
        return CommonResult.failed(e.getMessage());
    }
}
```

**Java 知识点：Spring @ControllerAdvice + @ExceptionHandler**
- `@ControllerAdvice` = "对所有 Controller 生效的建议类"
- `@ExceptionHandler(ApiException.class)` = "当任何 Controller 抛出 ApiException 时，调用这个方法"
- 有了它，Controller 里**不需要写 try-catch**，直接 `throw new ApiException("库存不足")` 即可
- Spring 会自动把返回值序列化为 JSON

**数据流：**
```
Controller: throw new ApiException("库存不足")
    ↓ Spring 拦截
GlobalExceptionHandler.handle()
    ↓ 返回
{"code": 500, "message": "库存不足", "data": null}
```

---

### 2.8 BaseRedisConfig — Redis 配置

```java
// 文件: config/BaseRedisConfig.java（67 行）
public class BaseRedisConfig {   // ← 注意：没有 @Configuration，子类必须加

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory, RedisSerializer<Object> redisSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());     // key 用 String
        template.setValueSerializer(redisSerializer);                 // value 用 JSON
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(redisSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisSerializer<Object> redisSerializer() {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 关键配置：存储类型信息，反序列化时能还原为原始类型
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL);
        serializer.setObjectMapper(mapper);
        return serializer;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        return new RedisCacheManager(
            RedisCacheWriter.nonLockingRedisCacheWriter(factory),
            RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(redisSerializer()))
                .entryTtl(Duration.ofDays(1))  // 缓存默认 1 天过期
        );
    }
}
```

**Java 知识点：Spring @Bean 配置**
- `@Bean` 方法 = 手动创建 Spring 容器管理的对象（等价于 XML 中的 `<bean>`）
- 方法参数 `RedisConnectionFactory factory` 会自动注入（Spring 自动从容器中找匹配的 Bean）
- `redisSerializer()` Bean 被 `redisTemplate()` 通过参数引用 —— Spring 的**依赖注入**让 Bean 之间自动关联

**关键配置解释：**
- `DefaultTyping.NON_FINAL`：存储 Redis 时带上 `@class` 类型信息
  - 存储：`["com.macro.mall.model.UmsAdmin", {"id":1,"username":"admin"}]`
  - 不带的话反序列化会变成 `LinkedHashMap` 而不是 `UmsAdmin` 对象

---

### 2.9 RedisService / RedisServiceImpl — Redis 操作封装

```java
// 接口: service/RedisService.java（181 行）
public interface RedisService {
    // String 操作
    void set(String key, Object value, long time);
    Object get(String key);
    Boolean del(String key);
    Boolean hasKey(String key);
    Long incr(String key, long delta);     // 原子递增（订单号生成用）
    Long decr(String key, long delta);     // 原子递减

    // Hash 操作
    Object hGet(String key, String hashKey);
    void hSet(String key, String hashKey, Object value);
    Map<Object, Object> hGetAll(String key);
    void hDel(String key, Object... hashKey);

    // Set 操作
    Set<Object> sMembers(String key);
    Long sAdd(String key, Object... values);
    Boolean sIsMember(String key, Object value);

    // List 操作
    List<Object> lRange(String key, long start, long end);
    Long lPush(String key, Object value);
}
```

```java
// 实现: service/impl/RedisServiceImpl.java（197 行）
public class RedisServiceImpl implements RedisService {
    @Autowired  // ← 字段注入（旧风格，Phase 2 改造时会换成构造器注入）
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void set(String key, Object value, long time) {
        redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
        // opsForValue() → Redis 的 String 数据结构操作
    }

    @Override
    public Long incr(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
        // Redis INCR 是原子操作，线程安全 — 适合生成订单号
    }

    // ... 其余 30+ 方法都是类似的薄封装
}
```

**设计模式：外观模式（Facade）**
- `RedisTemplate` 的 API 比较底层（`opsForValue()`, `opsForHash()`...）
- `RedisService` 提供了更简洁的业务接口（`set`, `get`, `hGet`...）
- 业务代码只需要知道 `redisService.set("key", value)` 而不需要了解 `opsForValue()`

---

### 2.10 WebLogAspect — 请求日志 AOP 切面

```java
// 文件: log/WebLogAspect.java（127 行）
@Aspect        // ← 声明为切面
@Component     // ← Spring 组件扫描
@Order(1)      // ← 执行优先级（数字越小越先执行）
public class WebLogAspect {

    // 切点：匹配所有 Controller 的 public 方法
    @Pointcut("execution(public * com.macro.mall.controller.*.*(..))"
            + "||execution(public * com.macro.mall.*.controller.*.*(..))")
    public void webLog() {}

    @Around("webLog()")  // ← 环绕通知：在方法执行前后都加入逻辑
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取 HTTP 请求信息
        HttpServletRequest request = ((ServletRequestAttributes)
            RequestContextHolder.getRequestAttributes()).getRequest();

        // 执行目标方法
        Object result = joinPoint.proceed();   // ← 这一行就是调用实际的 Controller 方法

        // 记录日志
        long endTime = System.currentTimeMillis();
        WebLog webLog = new WebLog();
        webLog.setIp(RequestUtil.getRequestIp(request));
        webLog.setMethod(request.getMethod());
        webLog.setParameter(getParameter(method, joinPoint.getArgs()));
        webLog.setResult(result);
        webLog.setSpendTime((int)(endTime - startTime));
        // 通过 Logstash 输出到 Elasticsearch
        LOGGER.info(Markers.appendEntries(logMap), JSONUtil.parse(webLog).toString());

        return result;  // 返回 Controller 方法的返回值给调用方
    }

    // 通过反射读取方法参数上的 @RequestBody 和 @RequestParam 注解
    private Object getParameter(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(RequestBody.class)) {
                // @RequestBody 注解的参数 = POST 请求体
                argList.add(args[i]);
            }
            if (parameters[i].isAnnotationPresent(RequestParam.class)) {
                // @RequestParam 注解的参数 = URL 查询参数
                argList.add(Map.of(key, args[i]));
            }
        }
    }
}
```

**Java 知识点：AOP（面向切面编程）**
- **切面（Aspect）**= 横切关注点的模块化，这里是"日志记录"
- **切点（Pointcut）**= 定义在哪些方法上生效，用表达式匹配包名+方法名
- **通知（Advice）**= 切面在切点上执行的动作，`@Around` 可以包裹整个方法
- `joinPoint.proceed()` = 执行原始方法（不调用这一行，原始方法就不会执行）
- 有了 AOP，Controller 里**完全不需要写日志代码**，所有请求自动被记录

**执行流程：**
```
HTTP 请求 → Tomcat → DispatcherServlet → WebLogAspect.doAround()
    → [记录开始时间] → Controller 方法 → [记录结果和耗时] → JSON 响应
```

---

### 2.11 RequestUtil — IP 获取工具

```java
// 文件: util/RequestUtil.java（47 行）
public class RequestUtil {
    public static String getRequestIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("x-forwarded-for");
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        // ... 依次尝试多个代理头
        if (ipAddress == null || ...) {
            ipAddress = request.getRemoteAddr();  // 最终回退到直连 IP
        }
        // 多级代理时取第一个（逗号分隔）
        if (ipAddress != null && ipAddress.indexOf(",") > 0) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
        }
        return ipAddress;
    }
}
```

**Java 知识点：null 安全编程**
- 链式 `if (x == null || x.isEmpty() || "unknown".equals(x))` — 从最可能命中到最不可能命中
- `"unknown".equalsIgnoreCase(ipAddress)` 而不是 `ipAddress.equalsIgnoreCase("unknown")` — 避免 NPE
- 这是 Java 旧写法。JDK 21 的 `String.isEmpty()` 更简洁

---

### 2.12 BaseSwaggerConfig — Swagger 配置基类

```java
// 文件: config/BaseSwaggerConfig.java（121 行）
public abstract class BaseSwaggerConfig {  // ← abstract：不能直接实例化

    @Bean
    public Docket createRestApi() {
        SwaggerProperties props = swaggerProperties();  // ← 调用子类的实现
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo(props))
                .select()
                .apis(RequestHandlerSelectors.basePackage(props.getApiBasePackage()))
                .paths(PathSelectors.any())
                .build();
    }

    public abstract SwaggerProperties swaggerProperties();  // ← 子类必须实现

    // 修复 Springfox 与 Spring Boot 2.6+ 的兼容性问题
    public BeanPostProcessor generateBeanPostProcessor() {
        return new BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    // 通过反射修改 handlerMappings，过滤掉 patternParser != null 的 mapping
                }
                return bean;
            }
        };
    }
}
```

**设计模式：模板方法（Template Method）**
- 基类定义算法骨架（`createRestApi`），将可变部分延迟到子类（`swaggerProperties()`）
- 子类只需要提供配置属性，不需要重复写 Swagger 的 DSL

---

### 2.13 WebLog + SwaggerProperties

```java
@Data          // ← Lombok：自动生成 getter/setter/toString/equals/hashCode
@EqualsAndHashCode
public class WebLog {
    private String description;
    private String username;
    private Long startTime;
    private Integer spendTime;
    private String uri;
    private Object parameter;
    private Object result;
}

@Data
@Builder      // ← Lombok：生成 Builder 模式
public class SwaggerProperties {
    private String apiBasePackage;
    private boolean enableSecurity;
    private String title;
    private String description;
    private String version;
}
```

**Java 知识点：Lombok**
- `@Data` = `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`
- `@Builder` = 生成 Builder 模式：`SwaggerProperties.builder().title("xxx").build()`
- Phase 2 改造时会移除 Lombok，因为 JDK 25 上 Lombok 有兼容性问题

---

## 三、模块关系图

```
                    ┌─────────────────────┐
                    │   Controller 层     │
                    │ 返回 CommonResult   │
                    └──────────┬──────────┘
                               │ 调用
                    ┌──────────▼──────────┐
                    │    Service 层       │
                    │ throw ApiException  │──→ GlobalExceptionHandler → JSON
                    │ 使用 RedisService   │
                    └──────────┬──────────┘
                               │ 调用
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼──────┐ ┌─────▼──────┐ ┌───────▼──────────┐
    │ MBG Mapper     │ │ Custom DAO │ │ RedisService      │
    │ (单表 CRUD)    │ │ (复杂查询) │ │ → RedisTemplate   │
    └────────────────┘ └───────────┘ └──────────────────┘

    ┌──────────────────────────────────────────────────────────┐
    │                   WebLogAspect (AOP)                      │
    │    自动拦截所有 Controller 方法，记录请求/响应/耗时        │
    └──────────────────────────────────────────────────────────┘
```

---

## 四、Java 知识点清单

读完 mall-common，你应该理解以下 Java 概念：

### 基础语法
- [x] **interface** — 定义契约，面向接口编程
- [x] **enum** — 类型安全常量，可以实现接口
- [x] **泛型** — `CommonResult<T>`，`<T> List<T>`，类型参数的声明和使用
- [x] **异常体系** — checked vs unchecked，自定义异常，throw vs throws
- [x] **static 方法** — 工具类，不需要实例化
- [x] **方法重载** — 同名方法不同参数

### 面向对象
- [x] **abstract class** — 模板方法模式
- [x] **继承** — `ApiException extends RuntimeException`
- [x] **多态** — `IErrorCode code = ResultCode.SUCCESS`
- [x] **访问修饰符** — public / protected / private 的区别

### Spring 框架
- [x] **@ControllerAdvice + @ExceptionHandler** — 全局异常处理
- [x] **@Bean** — 手动注册 Spring 容器管理的对象
- [x] **依赖注入** — `@Autowired`，构造器参数注入
- [x] **AOP** — `@Aspect`、`@Pointcut`、`@Around`、`ProceedingJoinPoint`

### 设计模式
- [x] **静态工厂方法** — `CommonResult.success()` 代替构造器
- [x] **适配器模式** — `CommonPage.restPage()` 统一两种分页库
- [x] **模板方法** — `BaseSwaggerConfig` 基类定义骨架
- [x] **外观模式** — `RedisService` 简化 `RedisTemplate`
- [x] **异常包装** — `ApiException(Throwable cause)`

### 进阶
- [x] **Redis 序列化** — Jackson 类型信息（`DefaultTyping.NON_FINAL`）
- [x] **反射** — `Method.setAccessible(true)`、读取注解
- [x] **BeanPostProcessor** — Spring Bean 后置处理器
