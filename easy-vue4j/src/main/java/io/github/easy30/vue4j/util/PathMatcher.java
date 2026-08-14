package io.github.easy30.vue4j.util;

/**
 * Servlet 原生路径匹配器
 * 
 * 支持的格式：
 *   - /* : 匹配所有路径
 *   - /app/* : 匹配 /app/ 开头的所有路径
 *   - *.js : 匹配所有.js 文件
 *   - /index.html : 精确匹配单个文件
 * 
 * 注意：不支持 Ant 风格的 ** 或中间通配符
 */
public class PathMatcher {
    
    /**
     * 判断路径是否匹配模式
     * @param pattern 模式串，如：/*, /app/*, *.js
     * @param path 实际路径，如：/app/main.js
     * @return 是否匹配
     */
    public static boolean match(String pattern, String path) {
        // 1. 空值处理
        if (pattern == null || path == null) {
            return false;
        }
        
        // 2. 精确匹配（不含*）
        if (!pattern.contains("*")) {
            return pattern.equals(path);
        }
        
        // 3. 全匹配：/*
        if ("/*".equals(pattern)) {
            return true;
        }
        
        // 4. 目录匹配：/app/*
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix);
        }
        
        // 5. 后缀匹配：*.js
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            return path.endsWith(suffix);
        }
        
        return false;
    }
    
    /**
     * 判断路径是否匹配任一模式
     * @param patterns 模式列表（逗号分隔）
     * @param path 实际路径
     * @return 是否匹配
     */
    public static boolean matches(String patterns, String path) {
        if (patterns == null || patterns.trim().isEmpty()) {
            return false;
        }
        
        String[] patternArray = patterns.split(",");
        for (String pattern : patternArray) {
            String trimmed = pattern.trim();
            if (!trimmed.isEmpty() && match(trimmed, path)) {
                return true;
            }
        }
        
        return false;
    }
}
