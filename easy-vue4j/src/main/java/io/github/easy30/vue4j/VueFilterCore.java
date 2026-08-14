package io.github.easy30.vue4j;

import io.github.easy30.vue4j.object.ClientException;
import io.github.easy30.vue4j.servlet.*;
import io.github.easy30.vue4j.servlet.BridgeRequest;
import io.github.easy30.vue4j.servlet.BridgeResponse;
import io.github.easy30.vue4j.servlet.FilterForward;
import io.github.easy30.vue4j.servlet.MimeTypeLookup;
import io.github.easy30.vue4j.util.PathMatcher;
import io.github.easy30.vue4j.util.VuePreloader;
import io.github.easy30.vue4j.util.resource.CacheContent;
import io.github.easy30.vue4j.util.resource.ClassPathResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Properties;

/**
 * Vue Filter 核心业务逻辑，不依赖任何 javax/jakarta servlet 类型。
 * 通过 BridgeRequest / BridgeResponse / FilterForward / MimeTypeLookup 与 servlet 环境解耦。
 * VueJkFilter(jakarta) 和 VueFilter(javax) 均为其薄包装。
 */
@Slf4j
public class VueFilterCore {

    private String charset;
    private String resourceRoot;
    private String vueExt;
    private String defaultIndex;
    private String filterExclude;
    private boolean excludeNoExt;
    private String filterClientJsPath;
    private boolean preloadEnabled;
    private String esbuildPath;
    private VueCache vueCache;
    private MimeTypeLookup mimeLookup;
    private boolean noCache;

    /**
     * 统一的配置取值：优先级 -D 系统属性 > easy-vue4j.properties > 缺省值
     * <p>
     * 所有配置键统一为 <code>vue4j.xxx</code> 前缀，properties 与 -D 写法完全一致。
     * 如配置 <code>vue4j.resource.root</code>，既可在 easy-vue4j.properties 里写
     * <code>vue4j.resource.root=...</code>，也可用 <code>-Dvue4j.resource.root=...</code> 覆盖。
     * 为了向后兼容，仍容忍旧的无前缀写法（如 <code>resource.root</code> / <code>-Dresource.root</code>）。
     *
     * @param config easy-vue4j.properties 配置
     * @param key    配置键名（形如 vue4j.resource.root、vue4j.vue.ext）
     * @param def    缺省值
     */
    private static String resolve(Properties config, String key, String def) {
        String sys = System.getProperty(key);
        // 兼容旧写法 -Dresource.root（不带 vue4j. 前缀）
        if (sys == null && key.startsWith("vue4j.")) {
            sys = System.getProperty(key.substring("vue4j.".length()));
        }
        if (sys != null) {
            return sys;
        }
        String val = config.getProperty(key);
        // 兼容旧 properties 无前缀裸键写法 resource.root
        if (val == null && key.startsWith("vue4j.")) {
            val = config.getProperty(key.substring("vue4j.".length()));
        }
        return val != null ? val : def;
    }

    /**
     * 初始化配置、缓存和预热线程
     *
     * @param config    easy-vue4j.properties 配置
     * @param mimeLookup MIME 类型查找函数（由 ServletContext.getMimeType 实现）
     */
    public void init( Properties config, MimeTypeLookup mimeLookup) {
        this.mimeLookup = mimeLookup;

        // 所有配置统一优先级：-D 系统属性 > easy-vue4j.properties > 缺省值
        // 键名统一为 vue4j. 前缀，properties 与 -D 写法一致（兼容旧的无前缀写法）。
        charset = resolve(config, "vue4j.charset", "UTF-8");
        vueExt = resolve(config, "vue4j.vue.ext", ".vue");
        defaultIndex = resolve(config, "vue4j.default.index", "index.html");

        // 资源根路径
        resourceRoot = resolve(config, "vue4j.resource.root", "classpath:/static");

        filterExclude = resolve(config, "vue4j.filter.exclude", "");
        excludeNoExt = Boolean.parseBoolean(resolve(config, "vue4j.filter.exclude-no-ext", "true"));
        filterClientJsPath = resolve(config, "vue4j.filter.client-js.path", "/client-js");
        preloadEnabled = Boolean.parseBoolean(resolve(config, "vue4j.preload.enabled", "true"));
        esbuildPath = resolve(config, "vue4j.esbuild.path", "");

        noCache = Boolean.parseBoolean(resolve(config, "vue4j.no-cache", "true"));

        log.info("VueFilterCore config loaded:");
        log.info("  - vue4j.charset: {}", charset);
        log.info("  - vue4j.resource.root: {}", resourceRoot);
        log.info("  - vue4j.vue.ext: {}", vueExt);
        log.info("  - vue4j.default.index: {}", defaultIndex);
        log.info("  - vue4j.filter.exclude: {}", filterExclude);
        log.info("  - vue4j.filter.exclude-no-ext: {}", excludeNoExt);
        log.info("  - vue4j.filter.client-js.path: {}", filterClientJsPath);
        log.info("  - vue4j.preload.enabled: {}", preloadEnabled);
        log.info("  - vue4j.esbuild.path: {}", esbuildPath);
        log.info("  - vue4j.no-cache: {}", noCache);

        vueCache = new VueCache(resourceRoot, vueExt);

        // 异步预热
        Thread initThread = new Thread(() -> {
            TypeScriptToJs.preInitialize(new File(esbuildPath));
            TypeScriptToJs.convertJs("const a=2", "test.js");

            if (preloadEnabled) {
                try {
                    VuePreloader preloader = new VuePreloader(vueCache, charset);
                    preloader.preload(resourceRoot, vueExt);
                } catch (Exception e) {
                    log.warn("Vue preloading failed", e);
                }
            }
        }, "VueFilterCore-Init");
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * 处理请求，替代 Filter.doFilter()
     */
    public void doFilter(BridgeRequest request, BridgeResponse response, FilterForward chain)
            throws Exception {

        String servletPath = request.getServletPath();

        // 根路径 -> 默认首页
        if ("/".equals(servletPath)) {
            servletPath = "/" + defaultIndex;
            log.debug("Redirecting root path to: {}", servletPath);
        }

        // 1. 无扩展名路径排除
        if (excludeNoExt && !hasExtension(servletPath)) {
            log.debug("No extension, excludeNoExt=true, forward: {}", servletPath);
            chain.forward();
            return;
        }

        // 2. filter.exclude 匹配
        if (PathMatcher.matches(filterExclude, servletPath)) {
            log.debug("Resource excluded by filter, forward: {}", servletPath);
            chain.forward();
            return;
        }

        // 3. client-js 静态资源
        if (servletPath.startsWith(filterClientJsPath)) {
            String path =   ("/client-js/" + StringUtils.substring(servletPath, filterClientJsPath.length())).replace("//","/");
            response.setContentType(resolveContentType(path));
            response.setCharacterEncoding(charset);
            response.writeBody(new ClassPathResource(path).getContent());
            response.flushBody();
            return;
        }

        // 4. 缓存控制头：统一使用 no-cache 让浏览器每次请求都向服务器验证文件是否变化（通过 Last-Modified / 304），
        //    确保发布新版本后浏览器能及时获取最新文件，同时未变化的文件仍可走 304 减少带宽
        //如果 noCache=false,则需要前端自己控制缓存,如通过v=version参数来控制vue和js的缓存
        if(noCache) {
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }

        try {
            String filename = servletPath.substring(servletPath.lastIndexOf('/') + 1);
            CacheContent cacheContent = vueCache.getContent(filename, servletPath, charset);

            if (cacheContent != null) {
                long lastModified = cacheContent.getLastModified();
                if (lastModified > 0) {
                    response.setDateHeader("Last-Modified", lastModified);
                }

                // 304 缓存
                long ifModifiedSince = request.getDateHeader("If-Modified-Since");
                if (ifModifiedSince == lastModified) {
                    response.setStatus(304);
                    log.debug("Resource not modified (304): {}", servletPath);
                    return;
                }

                response.setContentType(resolveContentType(filename));
                response.setCharacterEncoding(charset);
                response.writeBody(cacheContent.getContent());
                response.flushBody();

                log.debug("Served file: {} (lastModified={})", servletPath, lastModified);
                return;
            }

        } catch (FileNotFoundException e) {
            log.debug("File not found: {}", servletPath);
            response.sendError(404, "Resource not found: " + servletPath);
            return;
        } catch (Exception e) {
            log.error("Error processing file: {}", servletPath, e);
            if (e instanceof ClientException) {
                response.sendError(400, e.getMessage());
            } else {
                response.sendError(500, "Internal server error");
            }
            return;
        }

        // 未处理 -> 继续过滤器链
        chain.forward();
    }

    /**
     * 清理缓存
     */
    public void destroy() {
        vueCache.clear();
        log.info("VueFilterCore destroyed, cache cleared");
    }

    // ========== 私有方法 ==========

    private boolean hasExtension(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        return filename.contains(".");
    }

    private String resolveContentType(String filename) {
        if (filename.endsWith(vueExt) || filename.endsWith(".js")
                || filename.endsWith(".mjs") || filename.endsWith(".ts")) {
            return "application/javascript";
        } else if (filename.endsWith(".html")) {
            return "text/html";
        } else if (filename.endsWith(".css")) {
            return "text/css";
        } else if (filename.endsWith(".json")) {
            return "application/json";
        } else {
            String ct = mimeLookup.getMimeType(filename);
            return ct != null ? ct : "application/octet-stream";
        }
    }
}
