package com.github.easy30.vue4j.util;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Acorn AST 解析器 - 一次解析，多次提取
 * 使用外部 JavaScript 文件进行解析，代码更清晰易维护
 */
@Slf4j
public class AstParser {

    private static final Gson GSON = new Gson();
    private static String parseScript;

    static {
        loadParseScript();
    }

    /**
     * 加载外部的 acorn-parse.js 脚本
     */
    private static void loadParseScript() {
        try (InputStream is = AstParser.class.getResourceAsStream("/server-js/acorn-parse.js");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            parseScript = sb.toString();
            log.debug("Loaded acorn-parse.js script successfully");
        } catch (Exception e) {
            log.warn("Failed to load acorn-parse.js: {}", e.getMessage());
            parseScript = "";
        }
    }

    @Data
    public static class AstResult {
        private java.util.List<String> topLevelNames;
        private java.util.List<String> componentNames;
        private String propsDef;
        private String emitsDef;
        private String propsRaw;
        private String emitsRaw;
        private boolean hasProps;
        private boolean hasEmits;
        private boolean hasExpose;

        public AstResult() {
            this.topLevelNames = new java.util.ArrayList<>();
            this.componentNames = new java.util.ArrayList<>();
            this.propsDef = "";
            this.emitsDef = "";
            this.propsRaw = "";
            this.emitsRaw = "";
        }
    }

    /**
     * 一次性解析 JS 代码，提取所有需要的信息
     */
    public static AstResult parseOnce(String code) {
        AstResult result = new AstResult();
        if (code == null || code.isEmpty()) {
            return result;
        }

        if (parseScript.isEmpty()) {
            log.warn("parseScript is empty, cannot parse");
            return result;
        }

        try {
            ScriptEngine eng = ScriptUtil.getEngine();
            eng.put("input", code);

            // 先执行外部脚本定义 parseVueSetup 函数
            eng.eval(parseScript);

            // 调用 parseVueSetup 函数解析代码
            Object r = eng.eval("parseVueSetup(input);");

            if (r != null) {
                AstResult parsed = GSON.fromJson(r.toString(), AstResult.class);
                if (parsed != null) {
                    result = parsed;
                }
            }
        } catch (Exception e) {
            log.warn("AstParser.parseOnce failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 提取顶层名称（兼容旧接口）
     */
    public static Set<String> parseTopLevelNames(String code) {
        Set<String> names = new LinkedHashSet<>();
        AstResult result = parseOnce(code);
        if (result.getTopLevelNames() != null) {
            names.addAll(result.getTopLevelNames());
        }
        return names;
    }

    /**
     * 提取组件名称（兼容旧接口）
     */
    public static Set<String> extractComponentNames(String code) {
        Set<String> names = new LinkedHashSet<>();
        AstResult result = parseOnce(code);
        if (result.getComponentNames() != null) {
            names.addAll(result.getComponentNames());
        }
        return names;
    }
}
