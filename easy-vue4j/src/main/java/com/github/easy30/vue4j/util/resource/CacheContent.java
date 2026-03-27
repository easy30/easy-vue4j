package com.github.easy30.vue4j.util.resource;

/**
 * 缓存内部类
 */
public class CacheContent implements BaseResource {
    private final byte[] content;
    private final long lastModified;

    public CacheContent(byte[] content, long lastModified) {
        this.content = content;
        this.lastModified = lastModified;
    }

    public byte[] getContent() {
        return content;
    }

    public long getLastModified() {
        return lastModified;
    }
}