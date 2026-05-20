package com.github.easy30.vue4j;

import lombok.extern.slf4j.Slf4j;

import javax.script.Bindings;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * TypeScript + 装饰器语法转换器
 * 
 * 使用 Rhino 引擎调用 Babel 实现转换
 * 
 * 注意：需要 Java 11+ 才能运行完整功能（支持 ES6+）
 * Java 8 会使用简单的装饰器解析（不做完整 TS 转换）
 */
@Slf4j
public class TypeScriptToJs {
    
    private static volatile javax.script.ScriptEngine cachedEngine = null;
    private static volatile boolean initializationFailed = false;
    private static final Object engineLock = new Object();
    
    /**
     * 获取或创建 ScriptEngine（单例模式，只初始化一次）
     */
    private static javax.script.ScriptEngine getEngine() throws Exception {
        // 如果之前初始化失败，直接抛出异常
        if (initializationFailed) {
            throw new RuntimeException("ScriptEngine initialization previously failed");
        }
        
        if (cachedEngine != null) {
            return cachedEngine;
        }
        
        synchronized (engineLock) {
            // 双重检查锁定
            if (cachedEngine != null) {
                return cachedEngine;
            }
            
            // 再次检查是否初始化失败
            if (initializationFailed) {
                throw new RuntimeException("ScriptEngine initialization previously failed");
            }
            
            try {
                System.out.println("Initializing ScriptEngine...");
                javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
                javax.script.ScriptEngine engine = manager.getEngineByName("rhino");
                if (engine == null) {
                    engine = manager.getEngineByName("JavaScript");
                }
                
                if (engine == null) {
                    initializationFailed = true;
                    throw new RuntimeException("js engine not available.");
                }

                System.out.println("Engine: " + engine.getFactory().getEngineName());
                
                // Rhino 1.7.15+ 已支持 ES6+，但 Babel standalone 需要 console 对象
                engine.eval(
                    "if (typeof console === 'undefined') {\n" +
                    "    console = { log: function() {}, error: function() {}, warn: function() {} };\n" +
                    "}\n" +
                    "if (typeof btoa === 'undefined') {\n" +
                    "    btoa = function(str) {\n" +
                    "        var Base64 = java.util.Base64;\n" +
                    "        var StringClass = java.lang.String;\n" +
                    "        var bytes = new StringClass(str).getBytes('UTF-8');\n" +
                    "        return StringClass(Base64.getEncoder().encodeToString(bytes));\n" +
                    "    };\n" +
                    "}\n"
                );
                
                System.out.println("Loading Babel...");
                try {
                    InputStream babelIs = TypeScriptToJs.class.getResourceAsStream("/server-js/babel/babel.min.js");
                    if (babelIs != null) {
                        engine.eval(new InputStreamReader(babelIs, StandardCharsets.UTF_8));
                        System.out.println("Babel eval done");
                    } else {
                        initializationFailed = true;
                        throw new RuntimeException("Babel script NOT FOUND");
                    }
                } catch (Exception e) {
                    System.out.println("Babel eval error: " + e.getMessage());
                    initializationFailed = true;
                    throw e;
                }
                
                // 检查 Babel 是否正确加载
                Object babelVersion = engine.eval("typeof Babel !== 'undefined' ? Babel.version : 'not loaded'");
                System.out.println("Babel version: " + babelVersion);
                
                cachedEngine = engine;
                System.out.println("ScriptEngine initialized successfully");
                return engine;
            } catch (Exception e) {
                initializationFailed = true;
                throw e;
            }
        }
    }

    /**
     * 将 TypeScript 代码转换为 JavaScript
     * 
     * @param source TypeScript 源代码
     * @param filename 文件名（用于错误提示）
     * @return 转换后的 JavaScript 代码
     */
    public static String convertTypeScriptToJs(String source, String filename) {
        return convertTypeScriptToJs(source, filename, false);
    }

    /**
     * 将 TypeScript 代码转换为 JavaScript
     * 
     * @param source TypeScript 源代码
     * @param filename 文件名（用于错误提示）
     * @param enableSourceMap 是否启用 sourceMap（会在结果尾部附加 sourceMap）
     * @return 转换后的 JavaScript 代码
     */
    public synchronized static String convertTypeScriptToJs(String source, String filename, boolean enableSourceMap) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        try {
            // 获取缓存的 ScriptEngine（首次调用时会初始化）
            javax.script.ScriptEngine engine = getEngine();

            String sourceMapConfig = enableSourceMap ? "true" : "false";

            // 设置输入变量
            engine.put("input", source);
            engine.put("filename", filename);
            engine.put("sourceMaps", enableSourceMap);

            // 执行转换
            //  Object output = engine.eval("Babel.transform(input, { presets: ['es2015'] }).code", bindings);
            String transformScript =
                "var result = Babel.transform(input, {" +
                "presets: ['typescript', ['es2015', { modules: false }]]," +
                "plugins: [" +
                "['proposal-decorators', { legacy: true }]," +
                "['transform-class-properties', { loose: true }]" +
                "]," +
                "filename: filename," +
                "sourceType: 'module'," +
                "sourceMaps: sourceMaps," +
                "compact: false" +
                "});" +
                "var output = result.code;" +
                "if (sourceMaps && result.map) {" +
                "output += '\\n//# sourceMappingURL=data:application/json;base64,' + btoa(JSON.stringify(result.map));" +
                "}" +
                "output;";

            log.debug("Transforming TypeScript...");
            try {
                Object result = engine.eval(transformScript);
                log.debug("Transform result type: {}", result != null ? result.getClass().getName() : "null");
                if (result != null) {
                    return result.toString();
                }
            } catch (Exception te) {
                log.error("Transform error: {}", te.getMessage());
                te.printStackTrace();
            }
            
        } catch (Exception e) {
            log.error("Babel transform failed", e);
        }
        
        return source;
    }
    
    private static String toJSString(String str) {
        if (str == null) return "null";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
