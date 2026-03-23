package com.github.easy30.vue4j;

/**
     * 缓存内部类
     */
    public  class FileContent {
        private final byte[] bytes;
        private final long lastModified;

        FileContent(byte[] resource, long lastModified) {
            this.bytes = resource;
            this.lastModified = lastModified;
        }

        byte[] getBytes() {
            return bytes;
        }

        long getLastModified() {
            return lastModified;
        }
    }