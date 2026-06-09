package com.github.easy30.vue4j.util;

import com.github.easy30.vue4j.VueCache;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Vue 文件预热器 - 在应用启动时预编译 Vue 文件到缓存
 * 
 * 直接调用 VueCache.getContent() 方法进行预热，简单高效
 * 支持 classpath:（开发模式文件系统 + JAR 模式）和文件系统两种路径格式
 */
@Slf4j
public class VuePreloader {

    private final VueCache vueCache;
    private final String charset;

    public VuePreloader(VueCache vueCache, String charset) {
        this.vueCache = vueCache;
        this.charset = charset;
        log.info("VuePreloader initialized");
    }

    /**
     * 预热指定目录下的 Vue 文件
     * @param baseDir 基础目录路径（支持 classpath:/static 或文件系统路径）
     * @param vueExt Vue 文件扩展名
     * @return 预热的文件数量
     */
    public int preload(String baseDir, String vueExt) {
        if (baseDir == null || baseDir.isEmpty()) {
            log.warn("baseDir is empty, skipping preload");
            return 0;
        }

        log.info("Starting Vue file preloading from: {}", baseDir);

        if (baseDir.startsWith("classpath:")) {
            return preloadFromClasspath(baseDir, vueExt);
        } else {
            return preloadFromFileSystem(baseDir, vueExt);
        }
    }

    /**
     * 从文件系统扫描预热
     */
    private int preloadFromFileSystem(String baseDir, String vueExt) {
        List<File> vueFiles = scanVueFiles(baseDir, vueExt);
        log.info("Found {} Vue files to preload", vueFiles.size());

        int successCount = 0;
        for (File vueFile : vueFiles) {
            try {
                String relativePath = getRelativePath(baseDir, vueFile);
                String filename = vueFile.getName();
                vueCache.getContent(filename, relativePath, charset, false);
                successCount++;
                log.debug("Preloaded Vue file: {}", relativePath);
            } catch (Exception e) {
                log.warn("Failed to preload Vue file: {}", vueFile.getAbsolutePath(), e);
            }
        }

        log.info("Vue preloading completed: {} success", successCount);
        return successCount;
    }

    /**
     * 从 classpath 扫描预热（支持开发模式文件系统 + JAR 模式）
     */
    private int preloadFromClasspath(String baseDir, String vueExt) {
        // classpath:/static -> /static
        String classpathLocation = baseDir.substring("classpath:".length());
        // 去掉开头的 /
        String resourcePath = classpathLocation.startsWith("/") ? classpathLocation.substring(1) : classpathLocation;
        
        try {
            URL url = getClass().getClassLoader().getResource(resourcePath);
            if (url == null) {
                url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            }
            
            if (url == null) {
                log.warn("Classpath resource not found: {}", baseDir);
                return 0;
            }

            String protocol = url.getProtocol();
            
            if ("file".equals(protocol)) {
                // 开发模式：文件系统目录
                File dir = new File(url.toURI());
                if (!dir.exists() || !dir.isDirectory()) {
                    log.warn("Classpath directory not found: {}", dir.getAbsolutePath());
                    return 0;
                }
                String fileBaseDir = dir.getAbsolutePath();
                List<File> vueFiles = scanVueFiles(fileBaseDir, vueExt);
                log.info("Found {} Vue files to preload from classpath (file): {}", vueFiles.size(), fileBaseDir);

                int successCount = 0;
                for (File vueFile : vueFiles) {
                    try {
                        String relativePath = getRelativePath(fileBaseDir, vueFile);
                        String filename = vueFile.getName();
                        vueCache.getContent(filename, relativePath, charset, false);
                        successCount++;
                        log.debug("Preloaded Vue file: {}", relativePath);
                    } catch (Exception e) {
                        log.warn("Failed to preload Vue file: {}", vueFile.getAbsolutePath(), e);
                    }
                }
                return successCount;
                
            } else if ("jar".equals(protocol)) {
                // JAR 模式：遍历 JAR 内部文件
                return preloadFromJar(url, classpathLocation, vueExt);
                
            } else {
                log.warn("Unsupported protocol: {}, skipping preload: {}", protocol, baseDir);
                return 0;
            }
        } catch (Exception e) {
            log.warn("Failed to preload from classpath: {}", baseDir, e);
            return 0;
        }
    }

    /**
     * 从 JAR 文件内部扫描预热
     */
    private int preloadFromJar(URL jarUrl, String classpathLocation, String vueExt) {
        try {
            // jar:file:/path/to/app.jar!/static
            // 创建 JAR 文件系统
            URI jarUri = URI.create("jar:" + jarUrl.getFile().split("!")[0]);
            FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap());
            
            // JAR 内的路径，去掉开头的 /
            String jarPath = classpathLocation.startsWith("/") ? classpathLocation.substring(1) : classpathLocation;
            Path rootPath = fs.getPath(jarPath);
            
            if (!Files.exists(rootPath)) {
                log.warn("JAR path not found: {}", jarPath);
                fs.close();
                return 0;
            }

            int successCount = 0;
            try (Stream<Path> paths = Files.walk(rootPath)) {
                List<Path> vueFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(vueExt))
                    .collect(Collectors.toList());
                
                log.info("Found {} Vue files to preload from JAR: {}", vueFiles.size(), jarUri);

                for (Path vueFile : vueFiles) {
                    try {
                        // 相对路径：/static/views/Test.vue
                        String relativePath = "/" + jarPath + "/" + rootPath.relativize(vueFile).toString();
                        relativePath = relativePath.replace('\\', '/');
                        String filename = vueFile.getFileName().toString();
                        
                        // 预热（vueCache.getContent 会自己读取文件）
                        vueCache.getContent(filename, relativePath, charset, false);
                        successCount++;
                        log.debug("Preloaded Vue file from JAR: {}", relativePath);
                    } catch (Exception e) {
                        log.warn("Failed to preload Vue file from JAR: {}", vueFile, e);
                    }
                }
            }
            
            fs.close();
            log.info("Vue preloading from JAR completed: {} success", successCount);
            return successCount;
            
        } catch (IOException e) {
            log.warn("Failed to scan JAR file: {}", jarUrl, e);
            return 0;
        }
    }

    /**
     * 扫描指定目录下的 Vue 文件
     */
    private List<File> scanVueFiles(String baseDir, String vueExt) {
        List<File> result = new ArrayList<>();
        File dir = new File(baseDir);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Base directory not found: {}", baseDir);
            return result;
        }
        scanDirectory(dir, baseDir, vueExt, result);
        return result;
    }

    /**
     * 递归扫描目录
     */
    private void scanDirectory(File currentDir, String baseDir, String vueExt, List<File> result) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, baseDir, vueExt, result);
            } else if (file.getName().endsWith(vueExt)) {
                result.add(file);
            }
        }
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(String baseDir, File file) {
        String basePath = new File(baseDir).getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(basePath)) {
            return filePath.substring(basePath.length()).replace(File.separatorChar, '/');
        }
        return filePath;
    }
}