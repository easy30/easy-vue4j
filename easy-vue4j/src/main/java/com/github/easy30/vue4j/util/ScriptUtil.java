package com.github.easy30.vue4j.util;

import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rhino ScriptEngine + Babel/Acorn 管理
 * <p>
 * 启动时一次性加载，后续复用。Acorn 用于快速 AST 解析提取变量名，Babel 用于完整 TS 转换（回退）。
 */
@Slf4j
public class ScriptUtil {

    private static volatile ScriptEngine engine = null;
    private static volatile boolean initFailed = false;
    private static final Object lock = new Object();

    /** 获取 ScriptEngine（已加载 babel.min.js + acorn.min.js） */
    public static ScriptEngine getEngine() throws Exception {
        if (initFailed) throw new RuntimeException("ScriptEngine init previously failed");
        if (engine != null) return engine;

        synchronized (lock) {
            if (engine != null) return engine;
            if (initFailed) throw new RuntimeException("ScriptEngine init previously failed");

            try {
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine eng = manager.getEngineByName("rhino");
                if (eng == null) eng = manager.getEngineByName("JavaScript");
                if (eng == null) { initFailed = true; throw new RuntimeException("No JS engine available"); }

                // 基础 polyfill
                eng.eval(
                        "if (typeof console === 'undefined') {" +
                        "  console = { log: function() {}, error: function() {}, warn: function() {} };" +
                        "}" +
                        "if (typeof btoa === 'undefined') {" +
                        "  btoa = function(s) { return java.util.Base64.getEncoder().encodeToString(new java.lang.String(s).getBytes('UTF-8')); };" +
                        "}"
                );

                // 加载 Babel（完整转换用）
                InputStream babel = ScriptUtil.class.getResourceAsStream("/server-js/babel/babel.min.js");
                if (babel != null) {
                    eng.eval(new InputStreamReader(babel, StandardCharsets.UTF_8));
                } else {
                    log.warn("babel.min.js not found, Babel transform will fail");
                }

                // 加载 Acorn（轻量 AST 解析用）
                InputStream acorn = ScriptUtil.class.getResourceAsStream("/server-js/acorn/acorn.min.js");
                if (acorn != null) {
                    eng.eval(new InputStreamReader(acorn, StandardCharsets.UTF_8));
                } else {
                    log.warn("acorn.min.js not found, Acorn parse will fail");
                }

                engine = eng;
                return eng;
            } catch (Exception e) {
                initFailed = true;
                throw e;
            }
        }
    }

    /** 用 Babel 转换 TypeScript → JavaScript */
    public static String transformWithBabel(String source, String filename, boolean enableSourceMap) throws Exception {
        ScriptEngine eng = getEngine();
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
    }

    /** 用 Acorn 解析 JS/TS AST，提取顶层 const/let/var/function/class 名 */
    public static Set<String> parseTopLevelNames(String code) throws Exception {
        ScriptEngine eng = getEngine();
        eng.put("input", code);
        String js = "var ast = acorn.parse(input, { sourceType: 'module', ecmaVersion: 2022 });" +
                "var names = [];" +
                "for (var i = 0; i < ast.body.length; i++) {" +
                "  var n = ast.body[i];" +
                "  if (n.type === 'VariableDeclaration') {" +
                "    for (var j = 0; j < n.declarations.length; j++) {" +
                "      if (n.declarations[j].id && n.declarations[j].id.type === 'Identifier') names.push(n.declarations[j].id.name);" +
                "    }" +
                "  } else if (n.type === 'FunctionDeclaration' && n.id) { names.push(n.id.name); }" +
                "  else if (n.type === 'ClassDeclaration' && n.id) { names.push(n.id.name); }" +
                "}" +
                "names.join(',');";
        Object result = eng.eval(js);
        Set<String> names = new LinkedHashSet<>();
        if (result != null) {
            for (String n : result.toString().split(",")) {
                n = n.trim();
                if (!n.isEmpty()) names.add(n);
            }
        }
        return names;
    }
}
