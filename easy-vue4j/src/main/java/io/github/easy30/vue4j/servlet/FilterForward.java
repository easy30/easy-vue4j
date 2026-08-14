package io.github.easy30.vue4j.servlet;

/**
 * FilterChain.doFilter() 的桥接接口
 */
@FunctionalInterface
public interface FilterForward {

    void forward() throws Exception;
}
