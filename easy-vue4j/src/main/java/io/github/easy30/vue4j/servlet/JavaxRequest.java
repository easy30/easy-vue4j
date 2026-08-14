package io.github.easy30.vue4j.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * Javax 版 HttpServletRequest 适配器
 */
public class JavaxRequest implements BridgeRequest {

    private final HttpServletRequest request;

    public JavaxRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getServletPath() {
        return request.getServletPath();
    }

    @Override
    public long getDateHeader(String name) {
        return request.getDateHeader(name);
    }
}
