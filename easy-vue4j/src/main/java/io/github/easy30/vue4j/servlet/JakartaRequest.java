package io.github.easy30.vue4j.servlet;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Jakarta 版 HttpServletRequest 适配器
 */
public class JakartaRequest implements BridgeRequest {

    private final HttpServletRequest request;

    public JakartaRequest(HttpServletRequest request) {
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
