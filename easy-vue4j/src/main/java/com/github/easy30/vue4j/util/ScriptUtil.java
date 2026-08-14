package com.github.easy30.vue4j.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Rhino ScriptEngine + Babel/Acorn 引擎池（commons-pool2）
 * <p>
 * 启动时缺省保持 1 个空闲引擎，上限 4 个。
 * 超出 minIdle 的空闲引擎 60 秒后自动释放。
 */
@Slf4j
public class ScriptUtil {

    private static final GenericObjectPool<ScriptEngine> pool;

    static {
        GenericObjectPoolConfig<ScriptEngine> config = new GenericObjectPoolConfig<>();
        config.setMinIdle(1);
        config.setMaxIdle(1);
        config.setMaxTotal(2);
        config.setMaxWaitMillis(60*1000); // 60秒
        config.setTimeBetweenEvictionRunsMillis(Duration.ofMinutes(10).toMillis()); //  检查空闲超时
        config.setSoftMinEvictableIdleTimeMillis(Duration.ofMinutes(15).toMillis()); // 超出minIdle的部分空闲60秒后释放
        pool = new GenericObjectPool<>(new ScriptEngineFactory(), config);
    }

    /** 获取 ScriptEngine（从池中借用） */
    public static ScriptEngine getEngine() throws Exception {
        return pool.borrowObject();
    }

    /** 归还 ScriptEngine 到池中 */
    public static void returnEngine(ScriptEngine engine) {
        if (engine != null) {
            pool.returnObject(engine);
        }
    }

    /** 用 Babel 转换 TypeScript → JavaScript */
    public static String transformWithBabel(String source, String filename, boolean enableSourceMap) throws Exception {
        ScriptEngine eng = getEngine();
        try {
            eng.put("input", source);
            eng.put("filename", filename);
            eng.put("sourceMaps", enableSourceMap);

            String script =
                    "var r = Babel.transform(input, {" +
                    "  presets: ['typescript', ['env', { modules: false }]]," +
                    "  plugins: [['proposal-decorators', { legacy: true }], ['transform-class-properties', { loose: true }]]," +
                    "  filename: filename, sourceType: 'module', sourceMaps: sourceMaps, compact: false" +
                    "});" +
                    "var out = r.code;" +
                    "if (sourceMaps && r.map) out += '\\n//# sourceMappingURL=data:application/json;base64,' + btoa(JSON.stringify(r.map));" +
                    "out;";
            Object r = eng.eval(script);
            return r != null ? r.toString() : source;
        } finally {
            returnEngine(eng);
        }
    }

    /** 创建 ScriptEngine 的工厂 */
    private static class ScriptEngineFactory extends BasePooledObjectFactory<ScriptEngine> {

        @Override
        public ScriptEngine create() throws Exception {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine eng = manager.getEngineByName("rhino");
            if (eng == null) eng = manager.getEngineByName("JavaScript");
            if (eng == null) throw new RuntimeException("No JS engine available");

            eng.eval(
                    "if (typeof console === 'undefined') {" +
                    "  console = { log: function() {}, error: function() {}, warn: function() {} };" +
                    "}" +
                    "if (typeof btoa === 'undefined') {" +
                    "  btoa = function(s) { return java.util.Base64.getEncoder().encodeToString(new java.lang.String(s).getBytes('UTF-8')); };" +
                    "}"
            );

            InputStream babel = ScriptUtil.class.getResourceAsStream("/server-js/babel/babel.min.js");
            if (babel != null) {
                eng.eval(new InputStreamReader(babel, StandardCharsets.UTF_8));
            } else {
                log.warn("babel.min.js not found, Babel transform will fail");
            }

            InputStream acorn = ScriptUtil.class.getResourceAsStream("/server-js/acorn/acorn.min.js");
            if (acorn != null) {
                eng.eval(new InputStreamReader(acorn, StandardCharsets.UTF_8));
            } else {
                log.warn("acorn.min.js not found, Acorn parse will fail");
            }

            log.info("ScriptEngine created");
            return eng;
        }

        @Override
        public PooledObject<ScriptEngine> wrap(ScriptEngine engine) {
            return new DefaultPooledObject<>(engine);
        }
    }
}
