package io.github.easy30.vue4j.servlet;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Javax 版 HttpServletResponse 适配器
 */
public class JavaxResponse implements BridgeResponse {

    private final HttpServletResponse response;

    public JavaxResponse(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public void setHeader(String name, String value) {
        response.setHeader(name, value);
    }

    @Override
    public void setDateHeader(String name, long value) {
        response.setDateHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        response.setStatus(sc);
    }

    @Override
    public void setContentType(String type) {
        response.setContentType(type);
    }

    @Override
    public void setCharacterEncoding(String charset) {
        response.setCharacterEncoding(charset);
    }

    @Override
    public void writeBody(byte[] data) throws IOException {
        response.getOutputStream().write(data);
    }

    @Override
    public void flushBody() throws IOException {
        response.getOutputStream().flush();
    }

    @Override
    public void sendError(int sc) throws IOException {
        response.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        response.sendError(sc, msg);
    }
}
