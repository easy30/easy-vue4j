# easy-vue4j

基于 Java 的 Vue 运行时转换器 / 全栈框架，支持在 Spring Boot 应用中**直接运行 `.vue` 文件，无需 Webpack/Vite 构建步骤**，同时也支持 TypeScript 与装饰器转译。

- **运行时转换**：`.vue` / `.ts` 文件在请求时动态转换为浏览器可执行的 JavaScript
- **无构建前端**：配合 ES Module + importmap 直接在浏览器中加载组件
- **双 Servlet 版本**：同时支持 javax（Java 8 / Spring Boot 2.x）与 jakarta（JDK 17+ / Spring Boot 3.x）

## 📦 项目结构

```
easy-vue4j/
├── easy-vue4j/                 # 核心库
│   └── src/main/java/com/github/easy30/vue4j/
│       ├── VueFilter.java      # Servlet 过滤器（javax，Java 8 / Spring Boot 2.x）
│       ├── VueJkFilter.java    # Servlet 过滤器（jakarta，Spring Boot 3.x）
│       ├── VueFilterCore.java  # 过滤逻辑核心（环境配置、缓存、转换调度）
│       ├── VueCache.java       # 文件缓存管理器
│       ├── VueToJs.java        # Vue 转 JS 转换器
│       ├── VueTemplate.java    # 模板处理器
│       └── ...                 # 资源、工具、对象等
├── easy-vue4j-demo/            # 示例项目（jk，Spring Boot 3.x，含 HTTP AOP / api-demo）
│   ├── src/main/resources/static/
│   │   ├── index.html          # 主页面（ES Module + importmap）
│   │   ├── routes.js           # 路由配置
│   │   ├── views/*.vue         # Vue 组件
│   │   └── api/api-demo.ts     # TypeScript + 装饰器 API 定义
│   └── pom.xml
└── easy-vue4j-java8-demo/      # 示例项目（javax，Java 8 / Spring Boot 2.7）
    └── src/main/resources/static/
        ├── index.html
        ├── routes.js
        └── *.vue               # Vue 组件（扁平结构）
```

## 🚀 快速开始

### 1. 环境要求

- **easy-vue4j-demo**：JDK > 8、Spring Boot 3.x（jakarta）
- **easy-vue4j-java8-demo**：JDK 8、Spring Boot 2.x（javax）
- Maven 3.6+

### 2. 构建核心库（安装到本地 Maven 仓库）

```bash
cd easy-vue4j
mvn clean install -DskipTests
```

### 3. 启动 Demo 项目

```bash
# 启动 jk 版（推荐，功能最全）
cd easy-vue4j-demo
mvn spring-boot:run
```

或直接运行 `DemoApplication.java` 的 main 方法。

### 4. 访问应用

浏览器打开：**http://localhost:8080/**

默认会重定向到：**http://localhost:8080/#/log-level**

## ⚙️ 配置

所有配置统一按优先级 `-D 系统属性 > easy-vue4j.properties > 缺省值` 解析，无需在 `AppConfig.java` 硬编码。

> 💡 系统属性也可用 `System.setProperty(...)` 注入（与 `-D` 共用同一张全局属性表，框架读 `System.getProperty` 即可取到），适合在启动代码里以编程方式兜底设置；应优先用 `-D`，仅在无法用启动参数时使用。

**命名约定：**
- 配置文件里使用**无前缀**键，如 `resource.root`
- `-D` 注入时使用 **`vue4j.` 前缀**，如 `-Dvue4j.resource.root=...` 对应配置里的 `resource.root=...`（避免与 JVM 通用系统属性撞名）
- 不区分环境，资源配置统一为 `resource.root`

例如启动时用 `-D` 覆盖：

```bash
java -Dvue4j.resource.root=src/main/resources/static -jar xxx.jar
# 或 Maven 方式
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dvue4j.resource.root=src/main/resources/static"
```

各参数的缺省值：

| 配置 | 缺省值 | 说明 |
|------|--------|------|
| `resource.root` | `classpath:/static` | 资源根路径 |
| `default.index` | `index.html` | 默认首页 |
| `vue.ext` | `.vue` | Vue 文件扩展名 |
| `filter.exclude` | 空 | 不经过 Filter 的资源（精确匹配 / 目录前缀 `/*` / 后缀匹配） |
| `filter.exclude-no-ext` | `true` | 无扩展名路径是否跳过 Filter |
| `esbuild.path` | 空（自动下载到 `~/.easy-vue4j/esbuild`） | TS / 装饰器转译 |

**热更新（自动决定，无需配置）：**
- `resource.root` 为**本地文件路径**（非 `classpath:`）→ 自动热更新，改文件后刷新即生效
- `resource.root` 为 `classpath:`（或打包成 jar 内资源）→ 使用缓存，不做热更新
- 开发时若希望热更新，把 `resource.root` 指向真实文件路径（如 `src/main/resources/static`）

`easy-vue4j.properties` 示例（一般放各项目 `src/main/resources/`）：

```properties
# 资源根路径（可选；不配置时使用缺省值 classpath:/static）
# resource.root=classpath:/static
# 开发时如需热更新，指向本地文件路径：
# resource.root=src/main/resources/static

# Filter 排除：不经过 Filter 的资源（支持精确匹配 / 目录前缀 /* / 后缀匹配）
filter.exclude=/favicon.ico, /robots.txt, *.min.js, *.min.css
filter.exclude-no-ext=true

# esbuild 转译路径（可选，用于 .ts / 装饰器转译；不配置时自动下载到 ~/.easy-vue4j/esbuild）
# esbuild.path=/path/to/esbuild
```

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

> 💡 环境与业务参数均通过 `-D` 或 `easy-vue4j.properties` 配置，不再写进 Spring 的 init-parameter。

## 🎯 Demo 功能模块

| 路由 | 组件 | 功能描述 |
|------|------|---------|
| `/log-level` | `setLogLevel.vue` | 设置 Logger 日志级别 |
| `/system-monitor` | `systemMonitor.vue` | 系统监控（CPU、内存） |
| `/log-query` | `logQuery.vue` | 日志查询 |
| `/appender-config` | `appenderConfig.vue` | Logback Appender 配置 |
| `/hello` | `hello.vue` | Hello World（演示 CSS Modules） |
| `/setup-test` | `setup-test.vue` | `<script setup>` 自动暴露 / 解构 / 可选链测试 |
| `/api-demo` | `api-demo.vue` | HTTP AOP 装饰器测试（仅 easy-vue4j-demo） |

## 📖 使用示例

### 创建新的 Vue 组件

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

## 🔧 技术栈

### 后端
- **Spring Boot**: 3.4.3（jk 版）/ 2.7.18（java8 版）
- **Servlet**: jakarta（Spring Boot 3.x）/ javax（Spring Boot 2.x）
- **Lombok / Commons-IO / Commons-Lang3**
- **Jsoup**（HTML/XML 解析）、**Ph-CSS**（CSS 解析）、**Gson**（JSON 处理）
- **esbuild / Rhino / Babel**（TS + 装饰器转译）

### 前端
- **Vue** 3.5.31、**Vue Router** 4.6.4、**Element Plus** 2.13.6、**Axios** 1.13.6、**Font Awesome** 6.4.0

## 🐛 常见问题

### Q1: 访问 http://localhost:8080/ 返回 404

**原因**: 未正确注册 Filter，或 `default.index` 找不到对应文件。

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
2. `resource.root` 路径配置错误
3. `.vue` 文件语法错误

**排查步骤**:
1. 检查控制台日志，确认 Filter 初始化成功
2. 验证 `easy-vue4j.properties` 中 `resource.root` 指向的路径存在
3. 查看浏览器开发者工具的网络请求

### Q4: 访问 .ts API 或装饰器报错 / 首次加载慢

- 首次访问 esbuild 转译需要初始化（可配置 `esbuild.path` 指向本地 esbuild 二进制，避免每次自动下载）
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

**最后更新时间**: 2026-08-14（文档已同步 easy-vue4j 1.0.4）

---

## ✨ 核心特性（非必须，按需阅读）

### 1. 运行时转换 .vue 文件

无需 Webpack/Vite 构建，直接在浏览器中运行 Vue 单文件组件：

- 自动提取 `<template>` 并编译为 render 函数
- 自动处理 `<script>` 和 `<script setup>`
- 支持 CSS Modules（`$style` 对象注入）、命名 Module（`<style module="m1">`）
- 支持 scoped 样式隔离

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

热更新**自动决定**，无需配置：`resource.root` 为本地文件路径（非 `classpath:`）时自动开启，改文件后刷新即生效；`classpath:` / jar 内资源使用缓存。开发时把 `resource.root` 指向真实文件路径即可获得热更新。
