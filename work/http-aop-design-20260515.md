# easy-vue4j HTTP AOP 装饰器设计方案

> 日期：2026-05-15  
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

| 原文件 | 存放目录 | 新文件名 | 说明 |
|-------|---------|---------|------|
| `httpApi.ts` | `src/main/resources/core-js/` | `http-aop.js` | AOP 装饰器核心：`@api/@post/@get/@json/@form` |
| `http.js` | `src/main/resources/core-js/` | `http-axios.js` | axios 适配器，实际发起 HTTP 请求 |

### 4. 配置映射

`easy-vue4j.properties`:
```properties
http.api.path=/core-js  # 浏览器访问路径，直接返回 core-js 目录文件不转换
```

---

## 二、HTTP AOP 装饰器语法规范

### Spring 对照表

| Spring Controller | easy-vue4j 写法 | 说明 |
|------------------|-----------------|------|
| `@RequestMapping("/api")` | `@api("/api")` | 类级路径前缀 |
| `@PostMapping` + `@RequestBody` | `@post("/save") save(t)` | 单参数自动 JSON |
| `@PostMapping` + 多参数无注解 | `@post(...) @form` | 打平到 Form body |

---

### 1. 类装饰器：`@api("路径前缀")`

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

### 2. JSON Body（对应 Spring `@RequestBody`）

| 使用场景 | 推荐语法 |
|---------|---------|
| **单参数自动推断（90% 场景）** | `@post("/save") save(user: User) {}` |
| **简写第二参 = JSON 参数名** | `@post("/save", "user") save(user, token) {}` |
| **显式 `@json` 装饰器** | `@post("/save") @json("user") save(user) {}` |
| **单参数 `@json` 省略参数名** | `@post("/save") @json save(user) {}` |

**示例：**
```typescript
@api("/api/user")
class UserApi {
    @post("/save") save(user: User) {}              // ✅ 单参数 = JSON body
    @get("/list") list(page: number, size: number) {} // ✅ GET = 参数到 query
}
```

---

### 3. FORM Body（对应 Spring 无 `@RequestBody`）

| 使用场景 | 语法 |
|---------|------|
| **全量打平到 Form** | `@post("/save") @form save(a, b, c) {}` |
| **指定参数打平（逗号分隔）** | `@post("/save") @form("user,dept") save(user, dept, logId) {}` |

**打平规则：** 对象属性递归合并，`http-axios.js` 通过 `qs.stringify` 序列化为 `application/x-www-form-urlencoded`

---

### 4. 方法体两种支持写法

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

## 三、核心决策表

| 决策项 | 结论 |
|-------|------|
| POST 单参数默认行为 | 自动推断为 JSON body |
| POST 多参数（无注解）默认行为 | Form 全量打平 body |
| 命名目录/文件 | `resources/core-js/http-aop.js` <br> `resources/core-js/http-axios.js` |
| 区分 JSON/FORM | `@json` / `@form` 独立装饰器为主 <br> `@post("/url", "param")` 简写为辅 |
| 方法体写法 | `{}` 或 `abstract class` 均支持 |
