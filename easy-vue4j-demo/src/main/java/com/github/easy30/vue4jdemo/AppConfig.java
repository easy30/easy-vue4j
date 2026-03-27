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
        registrationBean.addUrlPatterns("*.vue","*.html");
        registrationBean.setOrder(1); // 确保在其他过滤器之前执行
        registrationBean.addInitParameter("reload","1");
        registrationBean.addInitParameter("resourceRoot","/Users/apple/cyber/easy-vue4j/easy-vue4j-demo/src/main/resources/static");
        return registrationBean;
    }




}