package com.github.easy30.vue4j;

import com.github.easy30.vue4j.servlet.*;
import com.github.easy30.vue4j.util.VueGlobal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Vue 文件过滤器 - Javax 版（薄包装，委托给 VueFilterCore）
 * <p>
 * 动态读取 .vue 文件，转换为 JS，设置 Content-Type 为 application/javascript
 * 支持缓存机制，自动检测文件修改并重新加载（仅磁盘文件）
 *
 * @author CyberWater
 */
@Slf4j
public class VueFilter implements Filter {

    private VueFilterCore core;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        // 1. 加载配置(优先级低)
        Properties config = VueGlobal.loadProperties("easy-vue4j.properties");

        // 2. 加载配置(优先级高)
        Enumeration<String> initParameterNames = filterConfig.getInitParameterNames();
        while(initParameterNames!=null&&initParameterNames.hasMoreElements()){
            String name = initParameterNames.nextElement();
            config.put(name,filterConfig.getInitParameter(name));
        }

        String env = config.getProperty("vue4j.env");
        if (StringUtils.isBlank(env)) {
            env = System.getProperty("vue4j.env");
            if (StringUtils.isBlank(env)) {
                throw new ServletException("vue4j.env is required");
            }
        }
        log.info("Vue Filter initialized with env: {}", env);

        // 3. 创建核心并初始化
        MimeTypeLookup mimeLookup = filterConfig.getServletContext()::getMimeType;
        core = new VueFilterCore();
        core.init(env, config, mimeLookup);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        try {
            core.doFilter(
                    new JavaxRequest(request),
                    new JavaxResponse(response),
                    () -> filterChain.doFilter(request, response)
            );
        } catch (IOException | ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Unexpected error in VueFilterCore", e);
        }
    }

    @Override
    public void destroy() {
        if (core != null) {
            core.destroy();
        }
    }
}
