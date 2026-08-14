# 项目介绍
/Users/apple/cyber/easy-vue4j/README.md

# 纯前端老项目代码参考
老这是之前在前端用babel实现把ts和装饰器的代码转成浏览器能够执行的代码：
//装饰器样例
/Users/apple/cyber/cyberwater/cyberwater-iot/src/main/resources/static/js/api/equipInstanceApi.js
//装饰器代理实现
/Users/apple/github/vue3-loader/easy-vue/src/httpApi.ts
//实际发起的请求
/Users/apple/cyber/cyberwater/cyberwater-iot/src/main/resources/static/js/util/http.js

# 用java实现ts和装饰器的代码转成浏览器能够执行的代码
- 工程路径:/Users/apple/cyber/easy-vue4j/  ,  核心代码为 /Users/apple/cyber/easy-vue4j/easy-vue4j
- 使用nashorn
```xml
   <dependency>
  <groupId>org.openjdk.nashorn</groupId>
  <artifactId>nashorn-core</artifactId>
  <version>15.4</version>  <!-- 或最新 15.7，Java 8+ 均可 -->
  </dependency>
```
- 用nashorn 调用babel 实现转换
- 修改/Users/apple/cyber/easy-vue4j/easy-vue4j/src/main/java/io/github/easy30/vue4j/VueCache.java , 对.ts文件进行转换
  io.github.easy30.vue4j.VueCache.getContent(io.github.easy30.vue4j.util.resource.BaseResource, java.lang.String, java.lang.String)
- httpApi.ts 和 http.js 直接改为浏览器支持的httpApi.js, 代码放入/Users/apple/cyber/easy-vue4j/easy-vue4j/src/main/resources/core-js 目录,
   /Users/apple/cyber/easy-vue4j/easy-vue4j-demo/src/main/resources/easy-vue4j.properties 配置浏览器访问路径,如 http.api.path=/core-js
  VueCache.java 识别到http.api.path则会直接读取资源下面的core-js目录相应文件返回.
  httpApi.ts 是核心处理代理的代码,   http.js 根据不同的http客户端引擎而不一样,如axios.  这两个文件如果前端要用我的http框架则必须引用.

   请把我的需求先整理清楚重新输出,  另外先告诉我resources/core-js目录,httpApi.ts,  http.js 应该怎么命名比较好.
  

 