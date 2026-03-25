package com.github.easy30.vue4j;

import com.github.easy30.vue4j.object.FileContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vue 文件过滤器 - 集成 VueResourceTransformer 的所有功能
 * 动态读取 .vue 文件，转换为 JS，设置 Content-Type 为 application/javascript
 * 支持缓存机制，自动检测文件修改并重新加载（仅磁盘文件）
 *
 * @author CyberWater
 */
@Slf4j
public class VueFilterBak implements Filter {

    /**
     * Vue 文件编码，默认 UTF-8
     */
    private  String charset = "UTF-8";

    /**
     * 缓存转换后的内容：key=文件名，value=CachedContent
     */
    private final ConcurrentHashMap<String, FileContent> cache = new ConcurrentHashMap<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 从初始化参数中读取编码配置
        String configCharset = filterConfig.getInitParameter("charset");
        if (configCharset != null && !configCharset.trim().isEmpty()) {
            this.charset = configCharset.trim();
            log.info("VueFilter initialized with custom charset: {}", this.charset);
        } else {
            log.info("VueFilter initialized with default charset: {}", this.charset);
        }
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String servletPath = request.getServletPath();

        try {
            // 获取文件名
            String filename = servletPath.substring(servletPath.lastIndexOf('/') + 1);
            // 尝试带缓存的转换
            byte[] content = transformWithCache(filename, servletPath);

            if (content != null) {
                // 设置 Content-Type
                response.setContentType("application/javascript");
                response.setCharacterEncoding(charset);
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                // 写入转换后的内容

                //response.setContentLength(content.length);
                response.getOutputStream().write(content);
                response.getOutputStream().flush();

                log.debug("Served transformed Vue file: {}", servletPath);
                return;
            } else {
                // 文件不存在，返回 404
                log.debug("Vue file not found, returning 404: {}", servletPath);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Vue file not found: " + servletPath);
                return;
            }

        } catch (Exception e) {
            log.error("Error processing Vue file: {}", servletPath, e);
        }

        // 如果转换失败，继续执行过滤器链
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * 带缓存的转换逻辑，避免重复读取和转换
     *
     * @param filename    资源文件名
     * @param servletPath 资源路径
     * @return 转换后的 ByteArrayResource 对象
     * @throws IOException 当读取资源或转换失败时抛出
     */
    private byte[] transformWithCache(String filename, String servletPath) throws IOException {
        URL fileUrl = this.getClass().getResource("/static" + servletPath);
        if(fileUrl==null) return null;
        // 获取最后修改时间
        long lastModified = -1;
        String filePath=fileUrl.getFile();
        if (!isJarResource(filePath)) {
            File file = new File(filePath);
            if (file.exists()) {
                lastModified = file.lastModified();
            }
        }

        // 检查缓存
        FileContent fileContent = cache.get(servletPath);
        if (fileContent != null) {
            if (!hasChanged(fileContent, lastModified)) {
                log.debug("Cache hit for Vue file: {}", filename);
                return fileContent.getBytes();
            }
        }

        // 读取文件内容
        byte[] bytes;
        try (InputStream inputStream = fileUrl.openStream()) {
            if(inputStream==null) return null;
            bytes = IOUtils.toByteArray(inputStream);
        }

        String source = new String(bytes, charset);

        // 调用转换器转换为 JS
        String target = VueToJs.convertVueToJs(source, filename);

        byte[] targetBytes = target.getBytes(charset);

        // 放入缓存
        fileContent = new FileContent(targetBytes, lastModified);
        cache.put(filename, fileContent);
        return targetBytes;
    }

    /**
     * 检查资源是否已修改（仅适用于磁盘文件）
     *
     * @param cached              缓存的内容对象
     * @param currentLastModified 资源当前的最后修改时间戳
     * @return true 表示资源已修改，需要重新加载；false 表示未修改
     */
    private boolean hasChanged(FileContent cached, long currentLastModified) {
        if (currentLastModified < 0) {
            return false;
        }
        return currentLastModified > cached.getLastModified();
    }

    /**
     * 判断资源是否在 JAR 包内（JAR 包内的资源不会变化）
     *
     * @return true 表示资源在 JAR 包内；false 表示不在 JAR 包内或判断失败
     */
    private boolean isJarResource(String url) {

        return url.startsWith("jar:") || url.startsWith("wsjar:");

    }

    @Override
    public void destroy() {
        cache.clear();
        log.info("VueFilter destroyed, cache cleared");
    }

    private  static   class FileContent {
        private final byte[] bytes;
        private final long lastModified;

        FileContent(byte[] resource, long lastModified) {
            this.bytes = resource;
            this.lastModified = lastModified;
        }

        byte[] getBytes() {
            return bytes;
        }

        long getLastModified() {
            return lastModified;
        }
    }
}
