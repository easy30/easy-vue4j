# easy-vue4j

基于 Java 的 Vue 运行时转换器 / 全栈框架，支持在 Spring Boot 应用中**直接运行 `.vue` 文件，无需 Webpack/Vite 构建步骤**，同时也支持 TypeScript 与装饰器转译。

- **运行时转换**：`.vue` / `.ts` 文件在请求时动态转换为浏览器可执行的 JavaScript
- **无构建前端**：配合 ES Module + importmap 直接在浏览器中加载组件
- **双 Servlet 版本**：同时支持 javax（Java 8 / Spring Boot 2.x）与 jakarta（JDK 17+ / Spring Boot 3.x）

## 🚀 快速开始

### 1. 环境要求

- **easy-vue4j-demo**：JDK > 8、Spring Boot 3.x（jakarta）
- Maven 3.6+

 
### 2. 启动 Demo 项目

```bash
# 启动 demo（功能最全）
cd easy-vue4j-demo
mvn spring-boot:run
```

或直接运行 `DemoApplication.java` 的 main 方法。

### 3. 访问应用

浏览器打开：**http://localhost:8080/**

默认会重定向到：**http://localhost:8080/#/log-level**

### 4. 本地调试（热更新）

本地调试时只需要把 `vue4j.resource.root` 指向真实的文件路径，就能在修改 `.vue` / `.ts` / `routes.js` 文件后**刷新浏览器即生效**（无需重启、无需构建）。

默认 `vue4j.resource.root` 缺省值是 `classpath:/static`（走缓存）。要开启热更新，编辑 `easy-vue4j-demo/src/main/resources/easy-vue4j.properties`，取消注释并指向本地文件路径：

```properties
vue4j.resource.root=src/main/resources/static
```

> 热更新由框架自动决定：`vue4j.resource.root` 为本地文件路径（非 `classpath:`）时自动开启，改文件后刷新即生效；为 `classpath:`（或打进 jar）时走缓存、不做热更新。


## 配置

### 引入依赖（Maven 1.0.5）

在项目的 `pom.xml` 中引入 `easy-vue4j` 核心库（当前最新版本 **1.0.5**）：

```xml
<dependency>
    <groupId>io.github.easy30</groupId>
    <artifactId>easy-vue4j</artifactId>
    <version>1.0.5</version>
</dependency>
```

Demo 项目的 `easy-vue4j-demo`（jakarta / Boot 3.x）已引入该依赖，可直接作为参考。

> 提示：核心库同时兼容 javax 与 jakarta（分别提供 `VueFilter` / `VueJkFilter`，用于 Spring Boot 2.x / 3.x），无需区分坐标。



### Filter 注册（AppConfig)

`VueJkFilter`（jakarta）与 `VueFilter`（javax）的注册方式一致，只负责拦截请求，**不配置任何业务参数**：

```java
@Bean
public FilterRegistrationBean<VueJkFilter> vueFilterRegistrationBean() {
    FilterRegistrationBean<VueJkFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new VueJkFilter());
    registrationBean.addUrlPatterns("/*");
    registrationBean.setOrder(1);
    return registrationBean;
}
```

### ⚙️ 参数配置



所有配置统一按优先级 `-D 系统属性 > easy-vue4j.properties > 缺省值` 解析，无需在 `AppConfig.java` 硬编码。
 
**命名约定：**
- 所有配置键统一以 **`vue4j.` 开头**，`easy-vue4j.properties` 与 `-D` 注入写法**完全一致**，如 `vue4j.resource.root`
- `-D` 注入同样带 `vue4j.` 前缀，如 `-Dvue4j.resource.root=...` 对应配置里的 `vue4j.resource.root=...`
- 不区分环境，资源配置统一为 `vue4j.resource.root`

例如启动时用 `-D` 覆盖：

```bash
java -Dvue4j.resource.root=src/main/resources/static -jar xxx.jar
# 或 Maven 方式
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dvue4j.resource.root=src/main/resources/static"
```

各参数的缺省值：

| 配置 | 缺省值 | 说明 |
|------|--------|------|
| `vue4j.resource.root` | `classpath:/static` | 资源根路径 |
| `vue4j.default.index` | `index.html` | 默认首页 |
| `vue4j.vue.ext` | `.vue` | Vue 文件扩展名 |
| `vue4j.filter.exclude` | 空 | 不经过 Filter 的资源（精确匹配 / 目录前缀 `/*` / 后缀匹配） |
| `vue4j.filter.exclude-no-ext` | `true` | 无扩展名路径是否跳过 Filter |
| `vue4j.esbuild.path` | 空（自动下载到 `~/.easy-vue4j/esbuild`） | TS / 装饰器转译 |

**热更新（自动决定，无需配置）：**
- `vue4j.resource.root` 为**本地文件路径**（非 `classpath:`）→ 自动热更新，改文件后刷新即生效
- `vue4j.resource.root` 为 `classpath:`（或打包成 jar 内资源）→ 使用缓存，不做热更新
- 开发时若希望热更新，把 `vue4j.resource.root` 指向真实文件路径（如 `src/main/resources/static`）

`easy-vue4j.properties` 示例（一般放各项目 `src/main/resources/`）：

```properties
# 资源根路径（可选；不配置时使用缺省值 classpath:/static）
# vue4j.resource.root=classpath:/static
# 开发时如需热更新，指向本地文件路径：
# vue4j.resource.root=src/main/resources/static

# Filter 排除：不经过 Filter 的资源（支持精确匹配 / 目录前缀 /* / 后缀匹配）
vue4j.filter.exclude=/favicon.ico, /robots.txt, *.min.js, *.min.css
vue4j.filter.exclude-no-ext=true

# esbuild 转译路径（可选，用于 .ts / 装饰器转译；不配置时自动下载到 ~/.easy-vue4j/esbuild）
# vue4j.esbuild.path=/path/to/esbuild
```

 

## 🎯 前端Demo 

**Vue** 3.5.31、**Vue Router** 4.6.4、**Element Plus** 2.13.6、**Axios** 1.13.6

### index.html

`index.html` 是单页应用的**唯一入口**，包含三部分：

1. **依赖引入**：用 `<link>` 引入 Element Plus 样式与图标库，用 `<script type="importmap">` 声明各依赖（Vue、Vue Router、Element Plus、Axios 及框架提供的 `api-aop*`）映射到本地 `vendor/` 文件：

   ```html
   <script type="importmap">
   {
     "imports": {
       "vue": "./vendor/js/vue@3.5.31.esm-browser.js",
       "vue-router": "./vendor/js/vue-router@4.6.4.esm-browser.js",
       "element-plus": "./vendor/js/element-plus@2.13.6.esm-browser.mjs",
       "axios": "./vendor/js/axios@1.13.6.esm-browser.js",
       "api-aop": "./client-js/api-aop.js",
       "api-aop-axios": "./client-js/api-aop-axios.js"
     }
   }
   </script>
   ```

2. **页面骨架**：左侧 `<el-menu>` 为菜单（每个 `el-menu-item` 的 `index` 对应路由路径，跳转时通过 `@select` 触发 `router.push`），右侧为 `<router-view>` 路由出口，配合 `keep-alive` 缓存组件。

3. **启动逻辑**（底部 `<script type="module">`）：动态 `import('./routes.js')` 加载路由配置 → `setup({ baseURL: '/', defaultBodyType: 'json' })` 初始化 HTTP AOP → 创建路由与根组件 → 注册 Element Plus 后挂载到 `#app`。

<!-- placeholder 3 -->
### routes.js

`routes.js` 集中管理**前端路由**，采用 Vue Router 4 的 hash 模式配置，导出默认路由数组。每个路由通过懒加载 `component: () => import('./views/xxx.vue')` 指向对应的 `.vue` 组件，并用 `meta.title` 定义菜单/标题文案；`/` 重定向到默认首页：

```javascript
import { defineAsyncComponent } from 'vue';

const routes = [
    {
        path: '/log-level',
        name: 'SetLogLevel',
        component: () => import('./views/setLogLevel.vue'),
        meta: { title: '日志级别设置' }
    },
    // ... 其他路由
    {
        path: '/',
        redirect: '/log-level'
    }
];

export default routes;
```

新增页面时只需在 `routes.js` 中添加一条配置，同时在 `index.html` 的菜单里加一个指向对应 `path` 的 `el-menu-item` 即可。

### views

`views/` 目录存放所有 Vue 单文件组件（`.vue`），易用 Vue 3 `<script setup>` 语法，支持 CSS Modules 与 Element Plus 组件。Demo 中已有示例组件（每个都展示了不同的能力）：

| 组件 | 说明 |
|------|------|
| `hello.vue` | 基础示例：`script setup` + `<style scoped>` / `<style module>` 多区块 |
| `setup-test.vue` | 完整展示 `<script setup>` 的自动暴露：`ref` / `reactive` / `computed` / 解构 / 可选链等 |
| `api-demo.vue` | HTTP AOP 装饰器测试：JSON Body / Form Body / GET 三种请求 |
| `logQuery.vue` | 日志查询页面 |
| `setLogLevel.vue` | 日志级别设置页面 |
| `appenderConfig.vue` | Appender 配置页面 |
| `systemMonitor.vue` | 系统监控页面 |

一个最简单的 `hello.vue` 写法：

```vue
<template>
  <div :class="$style.container">
    <h1 :class="m1.title">{{ title }}</h1>
  </div>
</template>

<script setup>
import { ref } from 'vue';
const title = ref('欢迎');
</script>

<style module>
.container { padding: 20px; }
</style>
<style module="m1">
.title { color: blue; }
</style>
```

1. **在 `src/main/resources/static/` 目录创建 `.vue` 文件**（easy-vue4j-demo 放 `views/` 下）：

```vue
<template>
    <base-card title="我的页面" icon="fas fa-star">
        <div :class="$style.container">
            <h1 :class="m1.title">{{ title }}</h1>
        </div>
    </base-card>
</template>

<script setup>
import { ref } from 'vue';
const title = ref('欢迎');
</script>

<style module>
.container { padding: 20px; }
</style>
<style module="m1">
.title { color: blue; }
</style>
```

2. **添加到路由配置（`routes.js`）**：
```javascript
{
    path: '/my-page',
    name: 'MyPage',
    component: () => import('./views/myPage.vue'),
    meta: { title: '我的页面' }
}
```

3. **添加到菜单（`index.html`）**：
```html
<el-menu-item index="/my-page">
    <span>我的页面</span>
</el-menu-item>
```

 

## 🐛 常见问题

### Q1: 访问 http://localhost:8080/ 返回 404

**原因**: 未正确注册 Filter，或 `vue4j.default.index` 找不到对应文件。

**解决**:
1. 确认 `AppConfig` 已注册 `VueJkFilter`（或 `VueFilter`）
2. 确认 `static/index.html` 存在
3. 查看控制台确认 Filter 初始化成功

### Q2: 如何覆盖参数

**原因**: 需要临时覆盖某参数。

**解决**: 通过 `-D` 系统属性覆盖，不改代码、不改文件：
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dvue4j.resource.root=/data/static"
```
参数不区分环境，`-D` 一律带 `vue4j.` 前缀即可覆盖。

### Q3: Vue 组件无法加载

**可能原因**:
1. Filter 未正确注册
2. `vue4j.resource.root` 路径配置错误
3. `.vue` 文件语法错误

**排查步骤**:
1. 检查控制台日志，确认 Filter 初始化成功
2. 验证 `easy-vue4j.properties` 中 `vue4j.resource.root` 指向的路径存在
3. 查看浏览器开发者工具的网络请求

### Q4: 访问 .ts API 或装饰器报错 / 首次加载慢

- 首次访问 esbuild 转译需要初始化（可配置 `vue4j.esbuild.path` 指向本地 esbuild 二进制，避免每次自动下载）
- 确认网络可达 `registry.npmjs.org`（自动下载 esbuild 时需要）
- 转换结果会被缓存，后续访问更快

### Q5: CSS Modules 样式不生效

- ✅ `:class="$style.className"`
- ✅ `:class="m1.className"`（命名 module）
- ❌ `:class="className"`（缺少 module 对象）

## 📚 相关资源

- [Vue 3 官方文档](https://vuejs.org/)
- [Element Plus 文档](https://element-plus.org/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)

---

**最后更新时间**: 2026-08-14（文档已同步 easy-vue4j 1.0.5）

---

## ✨ 核心特性（非必须，按需阅读）

### 1. 运行时转换 .vue 文件

无需 Webpack/Vite 构建，直接在浏览器中运行 Vue 单文件组件：



### 2. HTTP AOP（装饰器式 API 定义）

在 `.ts` 文件中用装饰器声明 API，框架用 esbuild 转译后浏览器直接调用：

```typescript
// api/api-demo.ts
import { api, post, get, json, form } from 'api-aop';

@api("/api/demo")
class DemoApi {
    @post("/save")
    saveUser(user) { }                    // user 自动作为 JSON body

    @post("/update", "data")
    @json
    updateUser(data, token) { }           // data 为 JSON body，token 为 URL 参数

    @post("/submit")
    @form
    submitForm(name, age, email) { }      // 所有参数作为 form data

    @get("/list")
    getList(page, size) { }               // 所有参数作为 query string
}

export const demoApi = new DemoApi();
```

- `api-aop.js`：装饰器核心（`@api` / `@post` / `@get` / `@json` / `@form`）
- `api-aop-axios.js`：基于 axios 的请求适配器，通过 `setup({ baseURL, defaultBodyType })` 统一初始化
- 详细测试请查看 [easy-vue4j-demo 的 HTTP AOP 测试指南](easy-vue4j-demo/HTTP_AOP_TEST_GUIDE.md)

### 3. ES Module + Importmap

`index.html` 通过 importmap 映射依赖到本地 `vendor/` 目录，`client-js/api-aop*.js` 由框架通过 `/client-js/*` 提供：

```html
<script type="importmap">
{
  "imports": {
    "vue": "./vendor/js/vue@3.5.31.esm-browser.js",
    "vue-router": "./vendor/js/vue-router@4.6.4.esm-browser.js",
    "element-plus": "./vendor/js/element-plus@2.13.6.esm-browser.mjs",
    "axios": "./vendor/js/axios@1.13.6.esm-browser.js",
    "api-aop": "./client-js/api-aop.js",
    "api-aop-axios": "./client-js/api-aop-axios.js"
  }
}
</script>
```

### 4. 热更新支持

热更新**自动决定**，无需配置：`vue4j.resource.root` 为本地文件路径（非 `classpath:`）时自动开启，改文件后刷新即生效；`classpath:` / jar 内资源使用缓存。开发时把 `vue4j.resource.root` 指向真实文件路径即可获得热更新。
