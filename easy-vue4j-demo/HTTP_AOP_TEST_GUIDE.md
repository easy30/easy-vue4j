# HTTP AOP 装饰器测试指南

## 概述

本示例展示了如何使用 easy-vue4j 的 HTTP AOP 装饰器功能，将 TypeScript + 装饰器语法转换为浏览器可执行的 JavaScript。

## 文件结构

```
easy-vue4j-demo/src/main/resources/static/
├── test-api.ts          # TypeScript API 定义（使用装饰器）
├── api-demo.vue         # Vue 测试页面
└── index.html           # 主页面（配置了 importmap）

easy-vue4j-demo/src/main/java/com/github/easy30/vue4jdemo/
└── DemoController.java  # Spring Boot Controller（接收 API 调用）

easy-vue4j/src/main/resources/
├── server-js/babel/     # 后端转换引擎
│   └── babel.min.js
└── client-js/           # 前端运行时库
    ├── api-aop.js       # API AOP 装饰器核心
    └── api-aop-axios.js     # axios 适配器
```

## 测试步骤

### 1. 启动项目

```bash
cd /Users/apple/cyber/easy-vue4j/easy-vue4j-demo
mvn spring-boot:run
```

### 2. 访问测试页面

打开浏览器访问：`http://localhost:8080/index.html`

在左侧菜单点击 "API 测试" 或直接访问：`http://localhost:8080/#/api-demo`

### 3. 测试四种 API 调用方式

#### 3.1 POST JSON - 单参数自动推断
- 填写用户信息（ID、姓名、邮箱）
- 点击 "@post 单参数" 按钮
- 查看请求日志和响应结果

**TypeScript 代码：**
```typescript
@post("/save")
async saveUser(user) { }
```

**生成的请求：**
- URL: POST /api/demo/save
- Content-Type: application/json
- Body: `{ id: 1, name: "张三", email: "zhangsan@example.com" }`

#### 3.2 POST JSON - 显式指定 body 参数
- 填写用户信息
- 点击 "@post + @json" 按钮
- 查看请求日志和响应结果

**TypeScript 代码：**
```typescript
@post("/update", "data")
@json
async updateUser(data, token) { }
```

**生成的请求：**
- URL: POST /api/demo/update?token=test123
- Content-Type: application/json
- Body: `{ id: 1, name: "张三", email: "zhangsan@example.com" }`

#### 3.3 POST Form - 全量打平
- 填写表单信息（姓名、年龄、邮箱）
- 点击 "@post @form 全量" 按钮
- 查看请求日志和响应结果

**TypeScript 代码：**
```typescript
@post("/submit")
@form
async submitForm(name, age, email) { }
```

**生成的请求：**
- URL: POST /api/demo/submit
- Content-Type: application/x-www-form-urlencoded
- Body: `name=李四&age=25&email=lisi@example.com`

#### 3.4 GET 请求
- 填写页码和每页条数
- 点击 "@get 查询列表" 按钮
- 查看请求日志和响应结果

**TypeScript 代码：**
```typescript
@get("/list")
async getList(page, size) { }
```

**生成的请求：**
- URL: GET /api/demo/list?page=1&size=10

## 工作原理

### 1. TypeScript 转换流程

```
test-api.ts (TypeScript + 装饰器)
    ↓
VueFilter 拦截请求
    ↓
TypeScriptToJs 转换（Rhino + Babel）
    ↓
JavaScript (ES5 + 装饰器运行时)
    ↓
返回给浏览器
```

### 2. HTTP AOP 工作流程

```
前端调用 demoApi.saveUser(user)
    ↓
api-aop.js 拦截（装饰器）
    ↓
构建请求配置（URL、方法、参数）
    ↓
api-aop-axios.js 发送请求（axios）
    ↓
Spring Boot Controller 接收
    ↓
返回 JSON 响应
    ↓
前端接收并显示
```

### 3. 关键组件

- **api-aop.js**: 提供 `@api`、`@post`、`@get`、`@json`、`@form` 等装饰器
- **api-aop-axios.js**: 基于 axios 实现实际的 HTTP 请求发送，提供 `setup()` 统一初始化配置
- **TypeScriptToJs**: 后端转换引擎，将 TypeScript 转换为 JavaScript
- **DemoController**: Spring Boot 后端接口，接收并处理请求

## 装饰器说明

### @api(basePath)
设置 API 的基础路径，所有方法的 URL 都会拼接这个前缀。

### @post(path, bodyParams?)
- `path`: 请求路径
- `bodyParams`: 可选，指定哪些参数作为 request body
  - 不指定：单参数自动推断为 body
  - 指定：只有指定的参数作为 body，其他作为 URL 参数

### @json
显式标记该参数作为 JSON body（与 `@post` 的 bodyParams 配合使用）。

### @form
将所有参数打平为 form-data 格式（application/x-www-form-urlencoded）。

### @get(path)
GET 请求，所有参数都作为 query string。

## 注意事项

1. **首次加载较慢**：Babel 初始化需要时间（约 2-5 秒）
2. **缓存机制**：转换后的 JavaScript 会被缓存，后续访问更快
3. **热更新**：修改 `.ts` 文件后刷新页面即可看到变化
4. **浏览器兼容性**：转换后的代码兼容现代浏览器

## 扩展开发

如需添加新的 API：

1. 在 `test-api.ts` 中添加新的方法和装饰器
2. 在 `DemoController.java` 中添加对应的 endpoint
3. 在 `api-demo.vue` 中添加测试按钮和逻辑
4. 刷新页面即可测试

## 常见问题

**Q: 为什么第一次访问很慢？**
A: Babel standalone 需要初始化，加载和编译需要时间。后续访问会使用缓存。

**Q: 如何查看转换后的 JavaScript？**
A: 浏览器开发者工具 → Network → 找到 test-api.ts → Response 标签页

**Q: 支持 async/await 吗？**
A: 支持，Babel 会自动转换为 Promise 链。

**Q: 可以自定义错误处理吗？**
A: 可以，通过 `setup({ onError: handler })` 或直接调用 `setDefaultHeaders()`。
