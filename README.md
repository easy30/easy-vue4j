# easy-vue4j

基于 Java 的 Vue 运行时转换器，支持在 Spring Boot 应用中直接运行 `.vue` 文件，无需构建步骤。

## 📦 项目结构

```
easy-vue4j/
├── easy-vue4j/          # 核心库
│   ├── src/main/java/com/github/easy30/vue4j/
│   │   ├── VueFilter.java        # Servlet 过滤器（javax）
│   │   ├── VueJakartaFilter.java # Jakarta Servlet 过滤器
│   │   ├── VueCache.java         # 缓存管理器
│   │   ├── VueToJs.java          # Vue 转 JS 转换器
│   │   └── VueTemplate.java      # 模板处理器
│   └── pom.xml
└── easy-vue4j-demo/     # 示例项目
    ├── src/main/resources/static/
    │   ├── index.html           # 主页面
    │   ├── routes.js            # 路由配置
    │   ├── *.vue                # Vue 组件
    │   └── js/                  # 浏览器版本依赖库
    └── pom.xml
```

## 🚀 快速开始

### 1. 环境要求
- JDK 17+ (Spring Boot 3.x 要求)
- Maven 3.6+

### 2. 构建核心库

```bash
cd easy-vue4j
mvn clean install
```

### 3. 启动 Demo 项目

```bash
cd easy-vue4j-demo
mvn spring-boot:run
```

或者直接运行 `DemoApplication.java` 的 main 方法。

### 4. 访问应用

浏览器打开：**http://localhost:8080/**

默认会重定向到：**http://localhost:8080/#/log-level**

## ⚙️ 核心特性

### 1. 运行时转换 .vue 文件

无需 Webpack/Vite 构建，直接在浏览器中运行 Vue 单文件组件：

- 自动提取 `<template>` 并编译为 render 函数
- 自动处理 `<script>` 和 `<script setup>`
- 支持 CSS Modules（`$style` 对象注入）
- 支持 scoped 样式隔离

### 2. 热更新支持

通过 Filter 配置实现开发环境热更新：

```java
@Bean
public FilterRegistrationBean<VueJakartaFilter> vueFilterRegistrationBean() {
    FilterRegistrationBean<VueJakartaFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new VueJakartaFilter());
    registrationBean.addUrlPatterns("*.vue", "*.html");
    registrationBean.setOrder(1);
    registrationBean.addInitParameter("reload", "1"); // 热更新模式
    registrationBean.addInitParameter("resourceRoot", "classpath:/static");
    return registrationBean;
}
```

#### reload 参数说明

| 值 | 行为 | 适用场景 |
|----|------|---------|
| `0` | 不检查文件修改，永久缓存 | 生产环境 |
| `1` | 检查文件修改时间，变化后重新加载 | 开发环境 ✅ |
| `2` | 每次都重新加载，不缓存 | 调试模式 |

### 3. ES Module + Importmap

使用现代浏览器的 ES Module 和 importmap 机制管理依赖：

```html
<script type="importmap">
{
  "imports": {
    "vue": "./js/vue@3.5.31.esm-browser.js",
    "vue-router": "./js/vue-router@4.6.4.esm-browser.js",
    "element-plus": "./js/element-plus@2.13.6.esm-browser.mjs",
    "axios": "./js/axios@1.13.6.esm-browser.js"
  }
}
</script>

<script type="module">
import { createApp } from 'vue';
import axios from 'axios';
// 直接使用导入的模块
</script>
```

## 🎯 Demo 功能模块

| 路由 | 组件 | 功能描述 |
|------|------|---------|
| `/log-level` | `setLogLevel.vue` | 设置 Logger 日志级别 |
| `/system-monitor` | `systemMonitor.vue` | 系统监控（CPU、内存） |
| `/log-query` | `logQuery.vue` | 日志查询 |
| `/appender-config` | `appenderConfig.vue` | Logback Appender 配置 |
| `/hello` | `hello.vue` | Hello World（演示 CSS Modules） |

## 📖 使用示例

### 创建新的 Vue 组件

1. **在 `src/main/resources/static/` 目录创建 `.vue` 文件**

```vue
<template>
    <base-card title="我的页面" icon="fas fa-star">
        <div :class="$style.container">
            <h1>{{ title }}</h1>
        </div>
    </base-card>
</template>

<script>
import BaseCard from './card.vue';

export default {
    name: 'MyPage',
    components: { BaseCard },
    data() {
        return {
            title: '欢迎'
        }
    }
}
</script>

<style module>
.container {
    padding: 20px;
    background-color: #f0f0f0;
}
</style>
```

2. **添加到路由配置 (`routes.js`)**

```javascript
{
    path: '/my-page',
    name: 'MyPage',
    component: () => import('./myPage.vue'),
    meta: { title: '我的页面' }
}
```

3. **添加到菜单 (`index.html`)**

```html
<el-menu-item index="/my-page">
    <i class="fas fa-star menu-icon"></i>
    <span>我的页面</span>
</el-menu-item>
```

### 使用 CSS Modules

`easy-vue4j` 自动处理 CSS Modules，将样式对象注入到组件中：

#### 默认 Module（`$style`）

```vue
<template>
    <div :class="$style.container">
        <h1 :class="$style.title">标题</h1>
    </div>
</template>

<style module>
.container {
    padding: 20px;
}
.title {
    color: blue;
}
</style>
```

#### 命名 Module

```vue
<template>
    <h1 :class="m1.title">标题</h1>
</template>

<style module="m1">
.title {
    color: red;
}
</style>
```

### 调用后端 API

在 Vue 组件中使用 axios：

```vue
<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';

export default {
    methods: {
        async fetchData() {
            try {
                const { data } = await axios.get('/api/endpoint', {
                    params: { key: 'value' }
                });
                console.log(data);
            } catch (error) {
                ElMessage.error('请求失败：' + error.message);
            }
        }
    }
}
</script>
```

## 🔧 技术栈

### 后端
- **Spring Boot**: 3.4.3
- **Jakarta Servlet**: 6.0.0
- **Lombok**: 简化代码
- **Jsoup**: HTML/XML 解析
- **Ph-CSS**: CSS 解析
- **Gson**: JSON 处理

### 前端
- **Vue**: 3.5.31 (ES Module 格式)
- **Vue Router**: 4.6.4
- **Element Plus**: 2.13.6
- **Axios**: 1.13.6
- **Font Awesome**: 6.4.0

## 📝 注意事项

### 1. axios 导入问题

如果在 `.vue` 文件中遇到 `axios is not defined` 错误，需要在 `<script>` 标签顶部显式导入：

```javascript
import axios from 'axios';
```

### 2. 静态资源路径配置

`AppConfig.java` 中的 `resourceRoot` 建议使用相对路径或 classpath：

```java
// 推荐方式
registrationBean.addInitParameter("resourceRoot", "classpath:/static");

// 或者动态获取路径
String userDir = System.getProperty("user.dir");
String resourcePath = userDir + "/src/main/resources/static";
registrationBean.addInitParameter("resourceRoot", resourcePath);
```

### 3. 后端 API 实现

当前 Demo 的前端页面已搭建完成，但大部分后端 API 尚未实现。需要自行开发 Controller 来处理：

- `GET /setLogLevel` - 设置日志级别
- `GET /system-monitor/metrics` - 获取系统监控指标
- `POST /log-query` - 查询日志
- `GET /appender-config` - 获取 Appender 配置

### 4. 生产环境部署

打包为 JAR 后，禁用热更新以提升性能：

```bash
# 构建 JAR
mvn clean package -DskipTests

# 修改 AppConfig.java 中的 reload 参数为 "0"
registrationBean.addInitParameter("reload", "0");

# 运行 JAR
java -jar target/easy-vue4j-demo-1.0.0.jar
```

## 🐛 常见问题

### Q1: 访问 http://localhost:8080/ 返回 404

**原因**: 没有配置欢迎页或 `/` 的 Controller

**解决**: 确保 `static/index.html` 存在，Spring Boot 会自动将其作为欢迎页

### Q2: Vue 组件无法加载

**可能原因**:
1. `VueJakartaFilter` 未正确注册
2. `resourceRoot` 路径配置错误
3. `.vue` 文件语法错误

**排查步骤**:
1. 检查控制台日志，确认 Filter 初始化成功
2. 验证 `resourceRoot` 路径是否存在
3. 查看浏览器开发者工具的网络请求

### Q3: CSS Modules 样式不生效

**原因**: 模板中的 `:class` 绑定不正确

**解决**: 
- ✅ `:class="$style.className"`
- ✅ `:class="m1.className"` (命名 module)
- ❌ `:class="className"` (缺少 module 对象)

## 📚 相关资源

- [Vue 3 官方文档](https://vuejs.org/)
- [Element Plus 文档](https://element-plus.org/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)

---

**最后更新时间**: 2026-03-27

