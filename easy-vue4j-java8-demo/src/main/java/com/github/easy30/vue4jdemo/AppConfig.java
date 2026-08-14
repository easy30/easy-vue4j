package com.github.easy30.vue4jdemo;

import com.github.easy30.vue4j.VueFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class AppConfig implements WebMvcConfigurer {
    @Bean
    public FilterRegistrationBean<VueFilter> vueFilterRegistrationBean() {
        FilterRegistrationBean<VueFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new VueFilter());
        // 拦截所有请求，由 Filter 内部判断是否处理
        registrationBean.addUrlPatterns("/*");
       /* registrationBean.addUrlPatterns("*.css");
        registrationBean.addUrlPatterns("*.vue");
        registrationBean.addUrlPatterns("*.js");
        registrationBean.addUrlPatterns("*.mjs");
        registrationBean.addUrlPatterns("*.ts");
        registrationBean.addUrlPatterns("*.html");*/
        registrationBean.setOrder(1); // 确保在其他过滤器之前执行
        
        // 参数均通过 easy-vue4j.properties 或 -D 系统属性配置，无需在此硬编码。
        // 例如：-Dvue4j.resource.root=...
        
        return registrationBean;
    }
}