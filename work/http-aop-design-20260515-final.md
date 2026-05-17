# easy-vue4j HTTP AOP 装饰器设计方案

> 日期：2026-05-15 (Final)  
> 整理：Spring 风格 HTTP API 装饰器语法规范

---

## 一、核心改造需求

### 1. 项目背景
- **目标**：Java + Nashorn + Babel 实现 TypeScript/装饰器代码在线转换为浏览器可执行 JavaScript
- **工程路径**：`/Users/apple/cyber/easy-vue4j/`
- **核心模块**：`easy-vue4j`

### 2. 技术方案

| 组件 | 说明 |
|-----|------|
| **Nashorn 引擎** | 引入 `org.openjdk.nashorn:nashorn-core:15.4`，Java 8+ 支持 |
| **Babel 转换** | Nashorn 内调用 Babel 实现 TypeScript + 装饰器语法转换 |
| **VueCache.java** | 修改 `getContent()` 方法，对 `.ts` 文件进行在线转换 |

### 3. 核心 JS 文件命名规范

| 原文件               | 存放目录 | 新文件名 | 说明 |
|-------------------|---------|---------|------|
| `work/httpApi.ts` | `src/main/resources/core-js/` | `http-aop.js` | AOP 装饰器核心：`@api/@post/@get/@json/@form/@defaultBody` |
| `work/http.js`    | `src/main/resources/core-js/` | `http-axios.js` | axios 适配器，实际发起 HTTP 请求 |

### 4. 配置映射

`easy-vue4j.properties`:
```properties
# ==========================================
# HTTP AOP 核心配置 - 最终命名
# ==========================================

# AOP 核心 JS 浏览器访问路径，直接返回 core-js 目录文件不转换
core-js.resource.path=/core-js

# POST 方法无显式 @json/@form 时的默认 body 类型（推荐配置）
#   可选值:
#     - json  = 推荐，微服务/前后端分离首选
#     - form  = 传统表单提交场景
#   默认不配则内置 = json
http-aop.body-type=json
```

---

## 二、HTTP AOP 装饰器语法规范

### 优先级决策层次
1. **方法显式装饰器**：`@json` / `@form` → 最高优先级
2. **全局配置**：`http-aop.body-type`

### Spring 对照表

| Spring Controller | easy-vue4j 写法 | 说明   |
|------------------|-----------------|------|
| `@RequestMapping("/api")` | `@api("/api")` | 类级路径前缀 |
| `@PostMapping` + `@RequestBody` | `@post("/save") save(t)` |JSON  |
| `@PostMapping` + 多参数无注解 | `@post(...) @form` | 打平到 Form body |

---

### 1. 类装饰器

#### 1.1 `@api("路径前缀")`

```typescript
@api("/api/user")
class UserApi {
    // 所有方法路径自动拼上 /api/user
}

// 跨域绝对路径也支持
@api("http://other-server.com/api")
class ExternalApi {}
```



---

### 2. 方法装饰器语法

#### `@post(path, bodyNames?)`
| 参数 | 类型 | 必须 | 说明 |
|-----|------|-----|------|
| `path` | string | 是 | 请求路径 |
| `bodyNames` | string | 否 | body 参数名（逗号分隔），不指定时 `@json` 默认取第一个参数 |

#### `@get(path)` / `@post(path)` / `@put(path)` / `@delete(path)`
请求方法装饰器，参数为请求路径，GET 请求参数自动拼 query string

#### `@json`
表示请求 body 为 JSON 格式（对应 Spring `@RequestBody`），**不带参数**

#### `@form`
表示请求 body 为 form 格式（对应传统表单提交），**不带参数**

---

### 3. JSON Body 使用示例

```typescript
@api("/api/user")
class UserApi {
    // ✅ 单参数自动推断为 JSON body
    @post("/save") save(user: User) {}

    // ✅ 显式指定 body 参数名（第二个参数）
    @post("/save", "user") save(user, token) {}

    // ✅ @json 不带参数，bodyNames 省略时默认取第一个参数
    @post("/save") @json save(user) {}
}
```

---

### 4. FORM Body 使用示例

| 使用场景 | 语法 |
|---------|------|
| **全量打平到 Form** | `@post("/save") @form save(a, b, c) {}` |
| **指定参数打平** | `@post("/save", "user,dept") @form save(user, dept, logId) {}` |

```typescript
@api("/api/user")
class UserApi {
    // ✅ 所有参数打平到 Form body
    @post("/save") @form save(name, age, email) {}

    // ✅ 指定哪些参数进 body（用逗号分隔）
    @post("/update", "name,email") @form save(name, email, logId) {}
}
```

**打平规则：** 对象属性递归合并，`http-axios.js` 通过 `qs.stringify` 序列化为 `application/x-www-form-urlencoded`

---

### 5. 方法体两种支持写法

```typescript
// ========== 手写代码用空对象 ==========
@api("/api/user")
class UserApi {
    @post("/save") save(user: User) {}
    @get("/list") list(page, size) {}
}

// ========== 工具生成代码推荐 abstract ==========
@api("/api/user")
abstract class UserApi {
    @post("/save") abstract save(user: User): Promise<Result>;
    @get("/list") abstract list(page: number): Promise<User[]>;
}
```

---

## 三、核心决策表（最终确认）

| 决策项 | 结论 |
|-------|------|
| 配置前缀统一 | **`http-aop.*`** (与 `http-aop.js` 模块命名一致) |
| 核心 JS 资源路径配置 | `http-aop.resource.path=/core-js` |
| 默认 POST Body 类型 | `http-aop.body-type=json` (kebab-case 推荐) |
| 类级统一默认类型装饰器 | `@defaultBody("json/form")` |
| 优先级 | 方法装饰器 > @defaultBody(类级) > 全局配置 > 内置兼容逻辑 |
| POST 单参数默认行为 | 自动推断为 JSON body（全局默认 = json） |
| 目录/文件命名 | `resources/core-js/http-aop.js` <br> `resources/core-js/http-axios.js` |
| JSON/FORM 区分 | `@json` / `@form` 独立装饰器为主 <br> `@post("/url", "param")` 简写为辅 |
| 方法体写法 | `{}` 或 `abstract class` 均支持 |
