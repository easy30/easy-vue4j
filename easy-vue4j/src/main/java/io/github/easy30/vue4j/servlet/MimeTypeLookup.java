package io.github.easy30.vue4j.servlet;

/**
 * ServletContext.getMimeType() 的函数式桥接
 */
@FunctionalInterface
public interface MimeTypeLookup {

    String getMimeType(String filename);
}
