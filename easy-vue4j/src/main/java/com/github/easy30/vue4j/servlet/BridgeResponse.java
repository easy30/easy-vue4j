package com.github.easy30.vue4j.servlet;

import java.io.IOException;

/**
 * Servlet Response 桥接接口，统一 javax 和 jakarta 的 HttpServletResponse
 */
public interface BridgeResponse {

    void setHeader(String name, String value);

    void setDateHeader(String name, long value);

    void setStatus(int sc);

    void setContentType(String type);

    void setCharacterEncoding(String charset);

    void writeBody(byte[] data) throws IOException;

    void flushBody() throws IOException;

    void sendError(int sc) throws IOException;

    void sendError(int sc, String msg) throws IOException;
}
