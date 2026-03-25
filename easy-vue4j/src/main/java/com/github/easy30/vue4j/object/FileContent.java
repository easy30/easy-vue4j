package com.github.easy30.vue4j.object;

/**
 * 缓存内部类
 */
public class FileContent {
    private final byte[] bytes;
    private final long lastModified;

    public FileContent(byte[] resource, long lastModified) {
        this.bytes = resource;
        this.lastModified = lastModified;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public long getLastModified() {
        return lastModified;
    }
}