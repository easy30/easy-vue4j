package com.github.easy30.vue4j;

import com.github.easy30.vue4j.util.PathMatcher;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/**
 * Vue 文件过滤器 - 集成 VueResourceTransformer 的所有功能
 * 动态读取 .vue 文件，转换为 JS，设置 Content-Type 为 application/javascript
 * 支持缓存机制，自动检测文件修改并重新加载（仅磁盘文件）
 *
 * @author CyberWater
 */
@Slf4j
public class VueJakartaFilter implements Filter {

    /**
     * 环境配置：dev | prod
     */
    private String env;

    /**
     * Vue 文件编码，默认 UTF-8
     */
    private String charset;

    /**
     * 资源根路径
     */
    private String resourceRoot;

    /**
     * Vue 文件扩展名
     */
    private String vueExt;

    /**
     * 默认首页文件名
     */
    private String defaultIndex;

    /**
     * 热更新 include 配置
     */
    private String reloadInclude;

    /**
     * 热更新 exclude 配置
     */
    private String reloadExclude;

    /**
     * Filter 排除配置
     */
    private String filterExclude;

    private VueCache vueCache;
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 1. 从 FilterConfig 读取环境配置
        env = filterConfig.getInitParameter("env");
        if (StringUtils.isBlank(env)) {
            env = "dev";
        }
        log.info("VueJakartaFilter initialized with env: {}", env);

        // 2. 读取 easy-vue4j.properties 配置文件
        Properties config = loadConfig();

        // 3. 解析基础配置（从配置文件读取）
        charset = getConfigValue(config, "charset", "UTF-8");
        resourceRoot = getConfigValue(config, "resource.root", null);
        vueExt = getConfigValue(config, "vue.ext", ".vue");
        defaultIndex = getConfigValue(config, "default.index", "index.html");

        // 4. 解析热更新配置（根据环境）
        String reloadKeyPrefix = env + ".reload";
        reloadInclude = config.getProperty(reloadKeyPrefix + ".include", "");
        reloadExclude = config.getProperty(reloadKeyPrefix + ".exclude", "");

        // 5. 解析 Filter 排除配置
        filterExclude = config.getProperty("filter.exclude", "");

        log.info("VueJakartaFilter config loaded:");
        log.info("  - charset: {}", charset);
        log.info("  - resourceRoot: {}", resourceRoot);
        log.info("  - vueExt: {}", vueExt);
        log.info("  - defaultIndex: {}", defaultIndex);
        log.info("  - reloadInclude: {}", reloadInclude);
        log.info("  - reloadExclude: {}", reloadExclude);
        log.info("  - filterExclude: {}", filterExclude);

        // 6. 初始化 VueCache（不再需要 reload 参数，由我们控制缓存策略）
        // 固定传入 1（热更新模式），实际是否检查由 doFilter 中的 shouldReload 决定
        vueCache = new VueCache(resourceRoot, 1, vueExt);
    }

    /**
     * 加载配置文件（使用 UTF-8 编码）
     */
    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("easy-vue4j.properties")) {
            if (is != null) {
                // 使用 UTF-8 编码读取配置文件
                java.io.Reader reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8);
                props.load(reader);
                log.info("Loaded easy-vue4j.properties successfully with UTF-8 encoding");
            } else {
                log.warn("easy-vue4j.properties not found, using defaults");
            }
        } catch (IOException e) {
            log.error("Failed to load easy-vue4j.properties", e);
        }
        return props;
    }

    /**
     * 从配置文件中获取值，支持默认值
     */
    private String getConfigValue(Properties config, String key, String defaultValue) {
        String value = config.getProperty(key);
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String servletPath = request.getServletPath();

        // 处理根路径访问
        if ("/".equals(servletPath)) {
            servletPath = "/" + defaultIndex;
            log.debug("Redirecting root path to: {}", servletPath);
        }

        // 1. 检查是否是 Filter 排除项（图片、字体等）
        if (PathMatcher.matches(filterExclude, servletPath)) {
            log.debug("Resource excluded by filter, passing to next filter: {}", servletPath);
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 判断是否需要热更新（reload）
        boolean needReload = shouldReload(servletPath);

        // 3. 设置缓存控制头
        if (needReload) {
            // 开发环境热更新：不缓存
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            log.debug("Hot reload enabled for: {}", servletPath);
        } else {
            // 第三方库或生产环境：启用缓存
            response.setHeader("Cache-Control", "max-age=3600");
            response.setDateHeader("Expires", System.currentTimeMillis() + 3600000);
            log.debug("Cache enabled for: {}", servletPath);
        }

        try {
            // 4. 获取文件名
            String filename = servletPath.substring(servletPath.lastIndexOf('/') + 1);

            // 5. 尝试带缓存的转换
            byte[] content = vueCache.getContent(filename, servletPath, charset);

            if (content != null) {
                // 6. 设置正确的 Content-Type
                setContentType(response, filename);
                response.setCharacterEncoding(charset);
                response.getOutputStream().write(content);
                response.getOutputStream().flush();

                log.debug("Served file: {} (reload={})", servletPath, needReload);
                return;
            } else {
                // 文件不存在，返回 404
                log.debug("File not found: {}", servletPath);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found: " + servletPath);
                return;
            }

        } catch (Exception e) {
            log.error("Error processing file: {}", servletPath, e);
        }

        // 如果处理失败，继续执行过滤器链
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * 判断是否需要热更新
     */
    private boolean shouldReload(String servletPath) {
        // 优先级：exclude > include > 默认值

        // 1. 检查是否在 exclude 中
        if (PathMatcher.matches(reloadExclude, servletPath)) {
            return false; // 在 exclude 中，不热更新
        }

        // 2. 检查是否在 include 中
        if (PathMatcher.matches(reloadInclude, servletPath)) {
            return true; // 在 include 中，需要热更新
        }

        // 3. 使用默认值：dev 环境热更新，prod 环境不热更新
        return "dev".equals(env);
    }

    /**
     * 设置正确的 Content-Type
     */
    private void setContentType(HttpServletResponse response, String filename) {
        if (filename.endsWith(".vue") || filename.endsWith(".js") || filename.endsWith(".mjs")) {
            response.setContentType("application/javascript");
        } else if (filename.endsWith(".html")) {
            response.setContentType("text/html");
        } else if (filename.endsWith(".css")) {
            response.setContentType("text/css");
        } else if (filename.endsWith(".json")) {
            response.setContentType("application/json");
        } else {
            // 默认类型
            response.setContentType("application/octet-stream");
        }
    }



    @Override
    public void destroy() {
        vueCache.clear();
        log.info("VueFilter destroyed, cache cleared");
    }


}
