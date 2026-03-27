package com.github.easy30.vue4j;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
     * Vue 文件编码，默认 UTF-8
     */
    private  String charset ;

    private VueCache vueCache ;
    private String  vueExt;
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 从初始化参数中读取编码配置
        charset = filterConfig.getInitParameter("charset");
        if(StringUtils.isBlank(charset)) charset= "UTF-8";

        String root=filterConfig.getInitParameter("resourceRoot");

        String reload= filterConfig.getInitParameter("reload");
        if(StringUtils.isBlank(reload)) reload="1";

        vueExt=filterConfig.getInitParameter("vueExt");
        if(StringUtils.isBlank(vueExt)) vueExt=".vue";

        vueCache = new VueCache(root,Integer.parseInt(reload),vueExt);
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
            byte[] content = vueCache.getContent(filename, servletPath,charset);

            if (content != null) {
                // 设置 Content-Type
                if(filename.endsWith(vueExt)) {
                    response.setContentType("application/javascript");
                    response.setCharacterEncoding(charset);
                }
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
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
