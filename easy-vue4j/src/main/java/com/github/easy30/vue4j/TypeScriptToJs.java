package com.github.easy30.vue4j;

import com.github.easy30.vue4j.util.EsbuildUtil;
import com.github.easy30.vue4j.util.ScriptUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * TypeScript + 装饰器语法转换器
 * <p>
 * 默认使用 esbuild 转换（快 50 倍），失败时回退到 Babel
 */
@Slf4j
public class TypeScriptToJs {

    public static String convertJs(String source, String filename) {
        return convertJs(source, filename, false);
    }

    /** 预初始化（可在应用启动时调用，预热 esbuild + ScriptEngine） */
    public static boolean preInitialize(File esbuildFile) {
        try {
            EsbuildUtil.init(esbuildFile);
            log.info("esbuild pre-initialized");
            return true;
        } catch (Exception e) {
            log.warn("esbuild not available: {}", e.getMessage());
        }
        try {
            ScriptUtil.getEngine();
            return true;
        } catch (Exception e) {
            log.error("ScriptEngine pre-init failed", e);
            return false;
        }
    }

    /** 转 TS/JS → ES2020（默认，给浏览器用） */
    public synchronized static String convertJs(String source, String filename, boolean enableSourceMap) {
        return convertJs(source, filename, enableSourceMap, "es2020");
    }

    /** 转 TS/JS → ES6（给 Acorn/Rhino 解析用） */
    public synchronized static String convertJsToEs6(String source, String filename) {
        return convertJs(source, filename, false, "es2015");
    }

    private synchronized static String convertJs(String source, String filename, boolean enableSourceMap, String target) {
        if (source == null || source.isEmpty()) return source;

        long t = System.currentTimeMillis();
        try {
            String result = EsbuildUtil.transpile(source, target, "es2020".equals(target));
            log.info("convert_js {} cost={}ms (esbuild-{})", filename, System.currentTimeMillis() - t, target);
            return result;
        } catch (Exception e) {
            log.debug("esbuild failed, fallback Babel: {}", e.getMessage());
        }

        try {
            String result = ScriptUtil.transformWithBabel(source, filename, enableSourceMap);
            log.info("convert_js {} cost={}ms (babel)", filename, System.currentTimeMillis() - t);
            return result;
        } catch (Exception e) {
            log.error("Babel transform failed", e);
            return source;
        }
    }
}
