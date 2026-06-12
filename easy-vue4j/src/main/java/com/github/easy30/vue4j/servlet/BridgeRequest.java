package com.github.easy30.vue4j.servlet;

/**
 * Servlet Request 桥接接口，统一 javax 和 jakarta 的 HttpServletRequest
 */
public interface BridgeRequest {

    String getServletPath();

    long getDateHeader(String name);
}
