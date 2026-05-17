package com.github.easy30.vue4jdemo;

import com.github.easy30.vue4j.VueJakartaFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class AppConfig implements WebMvcConfigurer {
    @Bean
    public FilterRegistrationBean<VueJakartaFilter> vueFilterRegistrationBean() {
        FilterRegistrationBean<VueJakartaFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new VueJakartaFilter());
        // 拦截所有请求，由 Filter 内部判断是否处理
        registrationBean.addUrlPatterns("/*");
       /* registrationBean.addUrlPatterns("*.css");
        registrationBean.addUrlPatterns("*.vue");
        registrationBean.addUrlPatterns("*.js");
        registrationBean.addUrlPatterns("*.mjs");
        registrationBean.addUrlPatterns("*.ts");
        registrationBean.addUrlPatterns("*.html");*/
        registrationBean.setOrder(1); // 确保在其他过滤器之前执行
        
        // 环境配置通过 init-parameter 传入
        registrationBean.addInitParameter("vue4j.env", "dev");
        
        // charset、resourceRoot、vueExt 等参数现在从 easy-vue4j.properties 读取
        // 如果需要覆盖配置文件，可以在这里添加
        // registrationBean.addInitParameter("charset", "UTF-8");
        // registrationBean.addInitParameter("resourceRoot", "classpath:/static");
        
        return registrationBean;
    }
}