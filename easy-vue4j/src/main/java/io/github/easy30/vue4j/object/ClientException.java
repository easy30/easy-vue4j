package io.github.easy30.vue4j.object;

/**
 * 可展示给前端的异常，异常信息会直接通过 HTTP 响应返回给客户端。
 */
public class ClientException extends RuntimeException {

    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
