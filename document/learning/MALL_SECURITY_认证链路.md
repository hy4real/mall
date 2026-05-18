# mall-security 认证授权链路解读

> mall-security 是认证授权层，被 mall-admin 和 mall-portal 共用。
> 共 14 个文件，~800 行。读完后你会掌握：JWT 原理、Spring Security 过滤器链、动态 RBAC、条件装配。

---

## 一、模块全景图

```
mall-security (14 files)
├── util/                           ← 工具类
│   ├── JwtTokenUtil                ← JWT 生成/解析/验证/刷新（核心）
│   └── SpringUtil                  ← 获取 Spring 容器中的 Bean
├── annotation/                     ← 自定义注解
│   └── CacheException              ← 标记"缓存异常需抛出"的方法
├── aspect/                         ← AOP 切面
│   └── RedisCacheAspect            ← Redis 缓存容错切面
├── component/                      ← Spring Security 核心组件（8 个）
│   ├── JwtAuthenticationTokenFilter ← JWT 认证过滤器（每个请求都经过）
│   ├── DynamicSecurityFilter       ← 动态权限过滤器（仅 mall-admin）
│   ├── DynamicSecurityMetadataSource ← URL→权限 映射（从 DB 加载）
│   ├── DynamicAccessDecisionManager ← 权限比对（用户权限 vs 需要的权限）
│   ├── DynamicSecurityService      ← 接口（mall-admin 实现它）
│   ├── RestAuthenticationEntryPoint ← 未登录时返回 401 JSON
│   └── RestfulAccessDeniedHandler  ← 无权限时返回 403 JSON
└── config/                         ← 配置类（4 个）
    ├── SecurityConfig             ← SecurityFilterChain 定义（过滤器组装）
    ├── CommonSecurityConfig       ← 通用 Bean 注册 + 条件装配
    ├── IgnoreUrlsConfig           ← 白名单 URL 配置（从 YAML 读取）
    └── RedisConfig                ← 继承 BaseRedisConfig + 启用 @EnableCaching
```

---

## 二、JWT 原理与实现

### 2.1 JWT 是什么

JWT（JSON Web Token）是无状态的认证方案，不需要服务端存储 session。

**三段式结构：**

```
header.payload.signature

┌──────────────────┐
│ {"alg":"HS512",  │  ← Base64 编码
│  "typ":"JWT"}    │
├──────────────────┤
│ {"sub":"admin", │  ← Base64 编码
│  "created":...   │     这是负载，存用户名和创建时间
│  "exp":...}      │
├──────────────────┤
│ HMACSHA512(     │  ← 用密钥签名，防篡改
│  header.payload, │
│  secret)        │
└──────────────────┘
```

**为什么不用 session？**
- Session 需要服务端存储（Redis 存 session ID），每次请求都要查一次 Redis
- JWT 自带用户信息，服务端只需要验证签名，不需要查存储
- 适合分布式部署（不依赖单点存储）

### 2.2 JwtTokenUtil 核心方法

```java
// 生成 token
public String generateToken(UserDetails userDetails) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", userDetails.getUsername());  // payload: 用户名
    claims.put("created", new Date());            // payload: 创建时间
    return Jwts.builder()
            .setClaims(claims)
            .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))  // 过期时间
            .signWith(SignatureAlgorithm.HS512, secret)  // 用 HS512 算法 + 密钥签名
            .compact();  // 压缩成 "xxx.yyy.zzz" 格式
}

// 解析 token（返回 payload）
private Claims getClaimsFromToken(String token) {
    return Jwts.parser()
            .setSigningKey(secret)    // 用密钥验证签名
            .parseClaimsJws(token)    // 如果签名不匹配会抛异常
            .getBody();              // 返回 Claims（即 payload 的 Map）
}

// 验证 token 是否有效
public boolean validateToken(String token, UserDetails userDetails) {
    String username = getUserNameFromToken(token);
    return username.equals(userDetails.getUsername())   // 1. 用户名匹配
        && !isTokenExpired(token);                       // 2. 未过期
}

// 刷新 token（30 秒内刚刷新过的返回原 token，防频繁刷新）
public String refreshHeadToken(String oldToken) {
    String token = oldToken.substring(tokenHead.length());  // 去掉 "Bearer " 前缀
    Claims claims = getClaimsFromToken(token);
    if (isTokenExpired(token)) return null;     // 过期了不刷新
    if (tokenRefreshJustBefore(token, 30 * 60)) return token;  // 30 秒内刷新过，返回原 token
    claims.put("created", new Date());
    return generateToken(claims);  // 生成新 token（保留原 payload）
}
```

**Java 知识点：**
- `@Value("${jwt.secret}")` — 从 `application.yml` 读取配置值，注入到字段
- JJWT 库的流式 API：`Jwts.builder().setClaims().setExpiration().signWith().compact()`
- HS512 = HMAC-SHA512，对称加密（签名和验证用同一个密钥）
- `expiration * 1000` — 配置文件中单位是秒，JWT 需要**毫秒**

---

## 三、Spring Security 过滤器链

### 3.1 SecurityConfig — 过滤器组装

```java
@Configuration
@EnableWebSecurity  // 启用 Spring Security
public class SecurityConfig {

    @Autowired(required = false)  // ← 关键：mall-portal 没有这个 Bean，不会报错
    private DynamicSecurityService dynamicSecurityService;
    @Autowired(required = false)
    private DynamicSecurityFilter dynamicSecurityFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) {
        httpSecurity
            // 1. 白名单放行（从 YAML 读取）
            .authorizeRequests()
                .antMatchers(ignoreUrls).permitAll()
                .antMatchers(HttpMethod.OPTIONS).permitAll()
            // 2. 其他所有请求需要认证
            .anyRequest().authenticated()
            // 3. 关闭 CSRF（前后端分离不需要）
            .csrf().disable()
            // 4. 不创建 Session（用 JWT 替代）
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            // 5. 未登录返回 401 JSON（而不是重定向到登录页）
            .exceptionHandling()
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restfulAccessDeniedHandler)
            // 6. 插入 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
            .addFilterBefore(jwtAuthenticationTokenFilter,
                UsernamePasswordAuthenticationFilter.class);

        // 7. 如果有 DynamicSecurityService（mall-admin），插入动态权限过滤器
        if (dynamicSecurityService != null) {
            httpSecurity.addFilterBefore(dynamicSecurityFilter,
                FilterSecurityInterceptor.class);
        }

        return httpSecurity.build();
    }
}
```

### 3.2 请求经过的过滤器顺序

```
HTTP 请求
    │
    ▼
[1] IgnoreUrlsConfig 白名单检查
    │   匹配 → permitAll() → 直接放行
    │   不匹配 → 继续
    │
    ▼
[2] OPTIONS 请求检查
    │   是 OPTIONS → permitAll() → 放行（浏览器 CORS 预检）
    │   不是 → 继续
    │
    ▼
[3] JwtAuthenticationTokenFilter（每个请求都经过）
    │   ├─ 读取 Authorization: Bearer xxx
    │   ├─ 解析 token → 提取 username
    │   ├─ 从数据库查询用户详情（UserDetailsService.loadUserByUsername）
    │   ├─ 验证 token 有效性
    │   ├─ 有效 → SecurityContextHolder.setAuthentication(auth) → 放行
    │   └─ 无效/没有 token → 不设置认证 → 后续抛 401
    │
    ▼
[4] DynamicSecurityFilter（仅 mall-admin 有）
    │   ├─ OPTIONS / 白名单 → 放行
    │   ├─ 从 DynamicSecurityMetadataSource 获取当前 URL 需要的权限
    │   │     如 /admin/product/** → "pms:product:read"
    │   ├─ DynamicAccessDecisionManager.decide():
    │   │     ├─ 用户权限列表包含所需权限 → 放行
    │   │     └─ 不包含 → 抛 AccessDeniedException → 403
    │   └─ 返回空（URL 未配置权限）→ 放行
    │
    ▼
[5] Controller 处理请求
```

### 3.3 JWT 过滤器核心代码

```java
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        // 1. 从请求头读取 token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String authToken = authHeader.substring("Bearer ".length());

            // 2. 从 token 中提取用户名
            String username = jwtTokenUtil.getUserNameFromToken(authToken);

            // 3. 如果用户名存在且当前请求还没有认证信息
            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 4. 从数据库加载用户详情（包含权限列表）
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 5. 验证 token 是否有效
                if (jwtTokenUtil.validateToken(authToken, userDetails)) {

                    // 6. 创建认证对象并放入 SecurityContext
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,       // principal（用户信息）
                            null,              // credentials（密码，已认证过不需要了）
                            userDetails.getAuthorities()  // authorities（权限列表）
                        );
                    authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        // 7. 无论是否认证，都继续执行后续过滤器
        chain.doFilter(request, response);
    }
}
```

**Java 知识点：SecurityContextHolder**
- Spring Security 的核心：一个 ThreadLocal 存储当前线程的认证信息
- `setAuthentication(auth)` — 标记"这个线程的用户已认证"
- `getAuthentication()` — 获取当前线程的用户信息
- Controller 中通过 `SecurityContextHolder.getContext().getAuthentication()` 拿到当前用户

**Java 知识点：OncePerRequestFilter**
- 保证每个请求只过滤一次（即使内部有 forward/include）
- 继承 `Filter` 的话需要自己实现这个逻辑，Spring 提供了基类

---

## 四、动态 RBAC（仅 mall-admin）

### 4.1 三个核心组件

```
DynamicSecurityService (接口，mall-admin 实现)
    │
    │  loadDataSource() 返回:
    │  {"/admin/product/**" → ConfigAttribute("pms:product:read"),
    │   "/admin/order/**"   → ConfigAttribute("oms:order:read"),
    │   ...}
    │
    ▼
DynamicSecurityMetadataSource (数据源)
    │
    │  getAttributes(request):
    │  根据 URL 匹配 ant pattern，返回所需权限
    │  如 GET /admin/product/list → ["pms:product:read"]
    │
    ▼
DynamicAccessDecisionManager (决策器)
    │
    │  decide(authentication, object, configAttributes):
    │  遍历用户权限，看是否包含所需权限
    │  包含 → 放行 | 不包含 → 抛 AccessDeniedException
```

### 4.2 DynamicSecurityMetadataSource — URL 匹配

```java
public Collection<ConfigAttribute> getAttributes(Object o) {
    // 获取当前请求的 URL
    String url = ((FilterInvocation) o).getRequestUrl();
    String path = URLUtil.getPath(url);
    PathMatcher pathMatcher = new AntPathMatcher();

    // 遍历所有配置的 ant pattern
    Iterator<String> iterator = configAttributeMap.keySet().iterator();
    while (iterator.hasNext()) {
        String pattern = iterator.next();
        if (pathMatcher.match(pattern, path)) {   // ant 风格匹配
            configAttributes.add(configAttributeMap.get(pattern));
        }
    }
    return configAttributes;
    // 如果没有匹配的 pattern，返回空集合 → DynamicAccessDecisionManager 直接放行
}
```

**ant pattern 匹配规则：**
- `/admin/product/**` — 匹配 `/admin/product/list`、`/admin/product/create` 等
- `*` — 匹配一层路径
- `**` — 匹配多层路径

### 4.3 DynamicAccessDecisionManager — 权限比对

```java
public void decide(Authentication authentication, Object object,
                   Collection<ConfigAttribute> configAttributes) {
    if (CollUtil.isEmpty(configAttributes)) {
        return;  // URL 没有配置所需权限 → 直接放行
    }
    // 遍历每个所需权限
    Iterator<ConfigAttribute> iterator = configAttributes.iterator();
    while (iterator.hasNext()) {
        String needAuthority = iterator.next().getAttribute();
        // 遍历用户拥有的权限
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (needAuthority.trim().equals(grantedAuthority.getAuthority())) {
                return;  // 匹配到 → 放行
            }
        }
    }
    throw new AccessDeniedException("抱歉，您没有访问权限");
    // 全部遍历完都没有匹配 → 403
}
```

---

## 五、条件装配 — 一个模块两套行为

### 5.1 @ConditionalOnBean 的作用

```java
// CommonSecurityConfig 中：
@Autowired(required = false)
private DynamicSecurityService dynamicSecurityService;

// 只有当容器中存在名为 "dynamicSecurityService" 的 Bean 时，才注册这些组件
@ConditionalOnBean(name = "dynamicSecurityService")
@Bean
public DynamicAccessDecisionManager dynamicAccessDecisionManager() { ... }

@ConditionalOnBean(name = "dynamicSecurityService")
@Bean
public DynamicSecurityMetadataSource dynamicSecurityMetadataSource() { ... }

@ConditionalOnBean(name = "dynamicSecurityService")
@Bean
public DynamicSecurityFilter dynamicSecurityFilter() { ... }
```

### 5.2 mall-admin vs mall-portal 的差异

```
mall-admin 模块:
  ├─ 定义了 DynamicSecurityService Bean（从数据库加载权限数据）
  └─ @ConditionalOnBean 检测到 → 注册 3 个动态权限组件
     → SecurityConfig 中 dynamicSecurityFilter != null → 添加到过滤器链
     → 请求经过 JWT 过滤器 + 动态权限过滤器

mall-portal 模块:
  ├─ 没有定义 DynamicSecurityService Bean
  └─ @ConditionalOnBean 检测不到 → 不注册 3 个动态权限组件
     → SecurityConfig 中 dynamicSecurityFilter == null → 跳过
     → 请求只经过 JWT 过滤器（只区分登录/未登录）
```

**Java 知识点：@ConditionalOnBean**
- Spring Boot 的条件装配：只有满足条件时才创建 Bean
- `required = false`：注入时如果容器中没有这个 Bean，不会报错，而是注入 null
- 这个设计让**同一个 security 模块**支持两种权限模式，不需要写两套代码

---

## 六、其他组件

### 6.1 IgnoreUrlsConfig — 白名单配置

```java
@ConfigurationProperties(prefix = "secure.ignored")
public class IgnoreUrlsConfig {
    private List<String> urls = new ArrayList<>();
    // 从 application.yml 的 secure.ignored.urls 自动绑定
}
```

对应 YAML 配置：
```yaml
secure:
  ignored:
    urls:
      - /admin/login
      - /admin/register
      - /**/v2/api-docs
      - /swagger-ui/**
      - /sso/**
      - /home/**
      - /product/**
```

### 6.2 RestAuthenticationEntryPoint + RestfulAccessDeniedHandler

```java
// 未登录 → 返回 401 JSON
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    public void commence(...) {
        response.setContentType("application/json");
        response.getWriter().println(
            JSONUtil.parse(CommonResult.unauthorized(authException.getMessage()))
        );
        // 输出: {"code":401,"message":"Full authentication is required...","data":null}
    }
}

// 无权限 → 返回 403 JSON
public class RestfulAccessDeniedHandler implements AccessDeniedHandler {
    public void handle(...) {
        response.getWriter().println(
            JSONUtil.parse(CommonResult.forbidden(e.getMessage()))
        );
        // 输出: {"code":403,"message":"抱歉，您没有访问权限","data":null}
    }
}
```

**为什么要自定义？**
- Spring Security 默认行为是：未登录 → 302 重定向到 `/login` 页面
- 前后端分离架构中，API 应该返回 JSON 而不是重定向

### 6.3 RedisCacheAspect — 缓存容错

```java
@Aspect
@Component
@Order(2)
public class RedisCacheAspect {
    // 切点：匹配所有 *CacheService 的 public 方法
    @Pointcut("execution(public * com.mall.*CacheService.*(..))")
    public void cacheAspect() {}

    @Around("cacheAspect()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            if (method.isAnnotationPresent(CacheException.class)) {
                throw throwable;   // 有 @CacheException 注解 → 抛出异常
            } else {
                LOGGER.error(throwable.getMessage());  // 无注解 → 吞掉异常，返回 null
            }
        }
    }
}
```

**设计思想：Redis 宕机不应阻断业务**
- 查缓存失败时，不应该让整个接口报错
- 没有 `@CacheException` 注解的方法：日志记录错误，返回 null（降级）
- 有 `@CacheException` 注解的方法：异常必须抛出（说明这个缓存结果业务上必须有效）

### 6.4 SpringUtil — Spring 上下文工具

```java
@Component
public class SpringUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    // 在 Bean 初始化时，Spring 自动调用这个方法注入上下文
    public void setApplicationContext(ApplicationContext ctx) {
        if (SpringUtil.applicationContext == null) {
            SpringUtil.applicationContext = ctx;
        }
    }

    // 静态方法：通过 Bean 名称或类型获取 Bean
    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }
}
```

**Java 知识点：ApplicationContextAware**
- `ApplicationContextAware` 是 Spring 的回调接口
- Spring 在创建这个 Bean 时会自动调用 `setApplicationContext()`，传入 Spring 容器
- 用途：在非 Spring 管理的类中获取 Bean（如工具类、过滤器）

---

## 七、认证流程图

### 7.1 登录流程

```
用户 → POST /admin/login {username:"admin", password:"123456"}
    │
    ▼
UmsAdminServiceImpl.login()
    │
    ├─ 1. loadUserByUsername("admin")
    │       → 查 ums_admin 表 → 构建 AdminUserDetails（包含权限列表）
    │
    ├─ 2. BCryptPasswordEncoder.matches(rawPassword, encodedPassword)
    │       → 比对密码（BCrypt 自动加盐）
    │
    └─ 3. jwtTokenUtil.generateToken(userDetails)
            → 生成 JWT token
            → 返回给前端

前端收到 token → 存储在 localStorage
后续请求带上: Authorization: Bearer xxx
```

### 7.2 认证流程（每次请求）

```
GET /admin/product/list + Authorization: Bearer eyJhbG...
    │
    ▼
JwtAuthenticationTokenFilter.doFilterInternal()
    │
    ├─ 1. 读取 tokenHead("Bearer ") 之后的 token
    │
    ├─ 2. getUserNameFromToken(token)
    │       → 解析 JWT payload，提取 sub 字段 → "admin"
    │
    ├─ 3. userDetailsService.loadUserByUsername("admin")
    │       → 查 ums_admin 表
    │       → 查 ums_role 关联 → ums_resource 关联
    │       → 返回 AdminUserDetails(username, password, authorities)
    │       其中 authorities = ["pms:product:read", "oms:order:read", ...]
    │
    ├─ 4. validateToken(token, userDetails)
    │       → username 匹配 + 未过期 → true
    │
    └─ 5. SecurityContextHolder.setAuthentication(auth)
            → 后续过滤器可以从 SecurityContext 获取当前用户
            → Controller 中 SecurityContextHolder.getContext().getAuthentication()
```

---

## 八、Java 知识点清单

读完 mall-security，你应该理解以下概念：

### Spring Security
- [x] **SecurityFilterChain** — 过滤器链的组装方式
- [x] **UsernamePasswordAuthenticationToken** — Spring Security 的认证对象
- [x] **SecurityContextHolder** — ThreadLocal 存储当前线程认证信息
- [x] **UserDetailsService** — 加载用户信息的接口
- [x] **GrantedAuthority** — 权限标识
- [x] **AccessDecisionManager** — 权限决策器
- [x] **AuthenticationEntryPoint** — 未登录时的处理器
- [x] **AccessDeniedHandler** — 无权限时的处理器

### JWT
- [x] **三段式结构** — header.payload.signature
- [x] **HS512 对称签名** — 签名和验证用同一个密钥
- [x] **@Value** — 从 YAML 读取配置
- [x] **过期机制** — exp claim + validateToken 校验

### Spring Boot
- [x] **@ConditionalOnBean** — 条件装配（同一个模块两种行为）
- [x] **@ConfigurationProperties** — YAML 前缀绑定
- [x] **@EnableCaching** — 启用 Spring Cache
- [x] **@EnableWebSecurity** — 启用 Spring Security

### 设计模式
- [x] **策略模式** — AccessDecisionManager 决定是否放行
- [x] **过滤器链模式** — 多个过滤器按顺序处理请求
- [x] **模板方法** — BaseRedisConfig → RedisConfig
- [x] **观察者模式** — ApplicationContextAware 回调
