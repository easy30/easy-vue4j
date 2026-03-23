package com.github.easy30.vue4j;

import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.*;

import java.io.IOException;


/**
 * Vue 文件过滤器 - 集成 VueResourceTransformer 的所有功能
 * 动态读取 .vue 文件，转换为 JS，设置 Content-Type 为 application/javascript
 * 支持缓存机制，自动检测文件修改并重新加载（仅磁盘文件）
 *
 * @author CyberWater
 */
@Slf4j
public class VueFilter2 implements Filter {

    /**
     * Vue 文件编码，默认 UTF-8
     */
    private  String charset = "UTF-8";

    VueCache vueCache = new VueCache();
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
            byte[] content = vueCache.get(filename, servletPath,charset);

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



    @Override
    public void destroy() {
        vueCache.clear();
        log.info("VueFilter destroyed, cache cleared");
    }


}
