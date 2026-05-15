package com.github.easy30.vue4j;

import com.github.easy30.vue4j.util.PathMatcher;
import com.github.easy30.vue4j.util.VueGlobal;
import com.github.easy30.vue4j.util.resource.CacheContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Properties;

/**
 * Vue 文件过滤器 - 集成 VueResourceTransformer 的所有功能
 * 动态读取 .vue 文件，转换为 JS，设置 Content-Type 为 application/javascript
 * 支持缓存机制，自动检测文件修改并重新加载（仅磁盘文件）
 *
 * @author CyberWater
 */
@Slf4j
public class VueFilter implements Filter {

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

    /**
     * 是否排除没有扩展名的路径（默认 true）
     */
    private boolean excludeNoExt;

    /**
     * ServletContext（用于获取 MIME Type）
     */
    private ServletContext servletContext;

    private VueCache vueCache;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 1. 从 FilterConfig 读取环境配置
        env = filterConfig.getInitParameter("vue4j.env");
        if (StringUtils.isBlank(env)) {
            env=System.getProperty("vue4j.env");
            if (StringUtils.isBlank(env))  throw new ServletException("vue4j.env is required");
        }
        log.info("Vue Filter initialized with env: {}", env);

        // 2. 读取 easy-vue4j.properties 配置文件
        Properties config = VueGlobal.loadProperties("easy-vue4j.properties");

        // 3. 解析基础配置（从配置文件读取）
        charset = config.getProperty( "charset", "UTF-8");
        vueExt = config.getProperty( "vue.ext", ".vue");
        defaultIndex = config.getProperty( "default.index", "index.html");

        // 4. 解析资源根路径：优先读取系统属性，其次配置文件
        resourceRoot = System.getProperty("vue4j.resource.root");
        if (resourceRoot==null) {
            resourceRoot = config.getProperty( "vue4j.resource.root", "/static");
        }


        // 5. 解析热更新配置（根据环境）
        String reloadKeyPrefix = env + ".reload";
        reloadInclude = config.getProperty(reloadKeyPrefix + ".include", "");
        reloadExclude = config.getProperty(reloadKeyPrefix + ".exclude", "");

        // 6. 解析 Filter 排除配置
        filterExclude = config.getProperty("filter.exclude", "");

        // 7. 解析是否排除无扩展名路径（默认 true）
        excludeNoExt = Boolean.parseBoolean(
            config.getProperty("filter.exclude-no-ext", "true")
        );

        log.info("Vue Filter config loaded:");
        log.info("  - charset: {}", charset);
        log.info("  - vue4j.resource.root: {}", resourceRoot);
        log.info("  - vue.ext: {}", vueExt);
        log.info("  - default.index: {}", defaultIndex);
        log.info("  - reload.include: {}", reloadInclude);
        log.info("  - reload.exclude: {}", reloadExclude);
        log.info("  - filter.exclude: {}", filterExclude);
        log.info("  - filter.exclude-no-ext: {}", excludeNoExt);

        // 7. 初始化 VueCache（不再需要 reload 参数，由我们控制缓存策略）
        vueCache = new VueCache(resourceRoot, vueExt);

        // 8. 保存 ServletContext 引用
        this.servletContext = filterConfig.getServletContext();
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

        // 1. 检查是否没有扩展名，根据配置决定是否排除
        if (excludeNoExt && !hasExtension(servletPath)) {
            log.debug("No extension found and excludeNoExt=true, passing to next filter: {}", servletPath);
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 检查是否是 Filter 排除项（图片、字体等）
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
        }

        try {
            // 4. 获取文件名
            String filename = servletPath.substring(servletPath.lastIndexOf('/') + 1);

            // 5. 尝试带缓存的转换（根据 needReload 决定是否检查文件变化）
            CacheContent cacheContent = vueCache.getContent(filename, servletPath, charset, needReload);

            //存在则处理, 不存在则走 filterChain.doFilter
            if (cacheContent != null) {
                // 6. 设置 Last-Modified 头（如果有最后修改时间）
                long lastModified = cacheContent.getLastModified();
                if (lastModified > 0) {
                    response.setDateHeader("Last-Modified", lastModified);
                }

                // 7. 检查 If-Modified-Since 头，实现 304 缓存
                long ifModifiedSince = request.getDateHeader("If-Modified-Since");
                if (ifModifiedSince == lastModified) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    log.debug("Resource not modified (304): {}", servletPath);
                    return;
                }

                // 8. 设置正确的 Content-Type
                setContentType(response, filename);
                response.setCharacterEncoding(charset);
                response.getOutputStream().write(cacheContent.getContent());
                response.getOutputStream().flush();

                log.debug("Served file: {} (reload={}, lastModified={})", servletPath, needReload, lastModified);
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


        return false;
    }

    /**
     * 判断路径是否包含文件扩展名
     * @param path URL 路径
     * @return true 表示有扩展名，false 表示没有扩展名（可能是 API 接口）
     */
    private boolean hasExtension(String path) {
        // 获取最后一个 / 之后的部分（文件名）
        String filename = path.substring(path.lastIndexOf('/') + 1);
        
        // 如果文件名中包含 . ，则认为有扩展名
        // 例如：main.js -> true, user/api -> false
        return filename.contains(".");
    }

    /**
     * 设置正确的 Content-Type
     */
    private void setContentType(HttpServletResponse response, String filename) {
        // 如果容器无法识别，使用默认映射
        if (filename.endsWith(".vue") || filename.endsWith(".js") ||
                filename.endsWith(".mjs") || filename.endsWith(".ts")) {
            response.setContentType("application/javascript");
        } else if (filename.endsWith(".html")) {
            response.setContentType("text/html");
        } else if (filename.endsWith(".css")) {
            response.setContentType("text/css");
        } else if (filename.endsWith(".json")) {
            response.setContentType("application/json");
        } else {
            String contentType = servletContext.getMimeType(filename);
            if (contentType != null) {
                response.setContentType(contentType);
            } else
                // 最后兜底：使用 octet-stream
                response.setContentType("application/octet-stream");
        }

    }


    @Override
    public void destroy() {
        vueCache.clear();
        log.info("VueFilter destroyed, cache cleared");
    }


}
