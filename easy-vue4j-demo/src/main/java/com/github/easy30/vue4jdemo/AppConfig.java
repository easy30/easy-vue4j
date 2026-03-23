package com.github.easy30.vue4jdemo;

import com.github.easy30.vue4j.VueFilter;
import com.github.easy30.vue4j.VueFilter2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class AppConfig implements WebMvcConfigurer {
    @Bean
    public FilterRegistrationBean<VueFilter2> vueFilterRegistrationBean() {
        FilterRegistrationBean<VueFilter2> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new VueFilter2());
        registrationBean.addUrlPatterns("*.vue");
        registrationBean.setOrder(1); // 确保在其他过滤器之前执行
        return registrationBean;
    }




}