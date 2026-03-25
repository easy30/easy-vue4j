package com.github.easy30.vue4j;

import com.github.easy30.vue4j.object.FileContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
public class VueCache {
    /**
     * 缓存转换后的内容：key=文件名，value=CachedContent
     */
    private final ConcurrentHashMap<String, FileContent> cache = new ConcurrentHashMap<>();

    /**
     * 带缓存的转换逻辑，避免重复读取和转换
     *
     * @param filename    资源文件名
     * @param servletPath 资源路径
     * @return 转换后的 ByteArrayResource 对象
     * @throws IOException 当读取资源或转换失败时抛出
     */
    public byte[] get(String filename, String servletPath, String charset) throws IOException {
        URL fileUrl = this.getClass().getResource("/static" + servletPath);
        if(fileUrl==null) return null;
        long lastModified = getLastModified(fileUrl);

        // 检查缓存
        FileContent fileContent = cache.get(servletPath);
        if (fileContent != null) {
            if (!hasChanged(fileContent, lastModified)) {
                log.debug("Cache hit for Vue file: {}", filename);
                return fileContent.getBytes();
            }
        }

        // 读取文件内容
        byte[] bytes;
        try (InputStream inputStream = fileUrl.openStream()) {
            if(inputStream==null) return null;
            bytes = IOUtils.toByteArray(inputStream);
        }

        String source = new String(bytes, charset);

        // 调用转换器转换为 JS
        String target = VueToJs.convertVueToJs(source, filename);

        byte[] targetBytes = target.getBytes(charset);

        // 放入缓存
        fileContent = new FileContent(targetBytes, lastModified);
        cache.put(servletPath, fileContent);
        return targetBytes;
    }

    private long getLastModified(URL fileUrl) {
        // 获取最后修改时间
        long lastModified = -1;
        String filePath= fileUrl.getFile();
        if (!isJarResource(filePath)) {
            File file = new File(filePath);
            if (file.exists()) {
                lastModified = file.lastModified();
            }
        }
        return lastModified;
    }

    /**
     * 检查资源是否已修改（仅适用于磁盘文件）
     *
     * @param cached              缓存的内容对象
     * @param currentLastModified 资源当前的最后修改时间戳
     * @return true 表示资源已修改，需要重新加载；false 表示未修改
     */
    private boolean hasChanged(FileContent cached, long currentLastModified) {
        if (currentLastModified < 0) {
            return false;
        }
        return currentLastModified > cached.getLastModified();
    }

    /**
     * 判断资源是否在 JAR 包内（JAR 包内的资源不会变化）
     *
     * @return true 表示资源在 JAR 包内；false 表示不在 JAR 包内或判断失败
     */
    private boolean isJarResource(String url) {

        return url.startsWith("jar:") || url.startsWith("wsjar:");

    }

    public void clear(){
        cache.clear();
    }
}
