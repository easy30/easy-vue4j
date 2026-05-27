package com.github.easy30.vue4j;


import com.github.easy30.vue4j.object.TemplateResult;
import com.github.easy30.vue4j.util.VueGlobal;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vue 单文件组件转换器 - 将 .vue 文件转换为浏览器可用的 JS 文件
 */
public class VueToJs {

    private static final Logger log = LoggerFactory.getLogger(VueToJs.class);

    // 移除了用于匹配 template 和 script 标签的正则表达式，改用 jsoup 解析
    //private static final Pattern TEMPLATE_PATTERN = Pattern.compile("<template\\b[^>]*>([\\s\\S]*)</template>", Pattern.DOTALL);
    //private static final Pattern SCRIPT_PATTERN = Pattern.compile( "<script\\b[^>]*>([\\s\\S]*?)</script>", Pattern.DOTALL);
    //private static final Pattern NAME_PATTERN = Pattern.compile("name:\\s*['"](\\w+)['"]");
    private static final Pattern EXPORT_DEFAULT_PATTERN = Pattern.compile("export\\s+default\\s+(\\{)", Pattern.DOTALL);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+.*?(?:;|$)", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern DEFINE_EXPOSE_PATTERN = Pattern.compile("defineExpose\\(([^)]*)\\)");
    // 匹配自定义元素标签（含连字符）的自闭合语法，如 <el-input />, <el-empty />
    private static final Pattern SELF_CLOSING_CUSTOM_TAG = Pattern.compile("<(\\w+-[\\w-]*)([^>]*?)\\s*/>");

    /**
     * 将 .vue 文件转换为 .js 文件并保存到指定路径
     *
     * @param vuePath .vue 源文件的完整路径
     * @param jsPath  转换后 .js 文件的目标保存路径
     * @param charset 文件编码字符集，如 "UTF-8"
     * @throws IOException 当读取源文件或写入目标文件失败时抛出
     */
    public static void convertVueToJs(Path vuePath, Path jsPath, String charset) throws IOException {
        String vueContent = new String(Files.readAllBytes(vuePath), charset);
        String jsContent = convertVueToJs(vueContent, vuePath.toString());
        Files.write(jsPath, jsContent.getBytes(charset));
    }

    /**
     * 将 .vue 文件内容转换为 .js 文件内容（字符串形式）
     *
     * @param vueContent .vue 文件的原始内容
     * @param fullName   含路径的文件名
     * @return 转换后的 JavaScript 代码字符串
     * @throws IOException 当解析 Vue 文件格式错误时抛出（缺少 template 或 script 标签）
     */
    public static String convertVueToJs(String vueContent, String fullName) throws IOException {
        // 预处理：将自定义标签自闭合转为显式开闭标签
        // Jsoup HTML parser 不识别 <el-input /> 等非 HTML 标准自闭合标签，
        // 会将其视为未闭合的开始标签，导致后续内容被嵌套其中。
        // 此步骤在 Jsoup 解析前完成，保证 Jsoup 得到正确的 DOM 结构。
        vueContent = fixSelfClosingCustomTags(vueContent);
        Document doc = Jsoup.parse(vueContent);
        // 生成组件唯一 ID
        String componentId = generateComponentId(fullName);

        // 使用 VueTemplate 处理所有 style 标签（直接从 doc 提取 template）
        VueTemplate vueTemplate = new VueTemplate(componentId);
        TemplateResult vueTemplateResult = vueTemplate.process(doc);

        // 处理 <script> 和 <script setup>
        Element scriptElement = doc.selectFirst("script:not([setup])");
        Element scriptSetupElement = doc.selectFirst("script[setup]");

        String script = null;
        String setupScript = null;

        // 处理普通 <script>
        if (scriptElement != null) {
            script = scriptElement.html().trim();
            // lang="ts" 时转换
            if ("ts".equals(scriptElement.attr("lang")) && StringUtils.isNotBlank(script)) {
                script = TypeScriptToJs.convertJs(script, fullName);
            }
        }
        if (StringUtils.isBlank(script)) {
            script = "export default { }";
        }

        // 处理 <script setup>
        if (scriptSetupElement != null) {
            setupScript = scriptSetupElement.html().trim();
            // lang="ts" 时转换
            if ("ts".equals(scriptSetupElement.attr("lang")) && StringUtils.isNotBlank(setupScript)) {
                setupScript = TypeScriptToJs.convertJs(setupScript, fullName);
            }
        }



        return convertContent(vueTemplateResult, script, setupScript);
    }

    /**
     * 将自定义元素标签的自闭合语法转换为显式开闭标签
     * 如 &lt;el-input v-model="x" /&gt; → &lt;el-input v-model="x"&gt;&lt;/el-input&gt;
     * 只处理含连字符的标签名（el-input、el-empty 等），不影响原生 HTML 标签
     */
    private static String fixSelfClosingCustomTags(String content) {
        Matcher matcher = SELF_CLOSING_CUSTOM_TAG.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String attrs = matcher.group(2) != null ? matcher.group(2) : "";
            String replacement = "<" + tagName + attrs + "></" + tagName + ">";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 根据文件名生成组件唯一 ID
     *
     * @param fullName 文件名
     * @return 唯一 ID 字符串（不含前缀）
     */
    private static String generateComponentId(String fullName) {
        String baseName = fullName.replace(".vue", "");
        // 使用文件名的 hash 值，保证唯一性且简短
        int hash = Math.abs(fullName.hashCode());
        return Integer.toHexString(hash);
    }

    /**
     * 生成最终的 JavaScript 文件内容，包含注释头和导出语句
     *
     * @param componentName 组件名称
     * @param script 处理后的 script 内容
     * @param template 模板 HTML 字符串
     * @param setupScript <script setup> 中的代码（如果有）
     * @return 完整的 JavaScript 代码（可直接在浏览器中使用）
     */


    /**
     * 生成最终的 JavaScript 文件内容（向后兼容，仅处理无 setup 的情况）
     */
//    private static String generateJsContent(String componentName, String scriptContent, String template) {
//        return generateJsContent(componentName, scriptContent, template, null);
//    }

    private static String convertContent(TemplateResult vueTemplateResult,String script, String setupScript) {
        StringBuilder setupImports = null;
        boolean hasStyle=StringUtils.isNotBlank(vueTemplateResult.getStyleInjectScript());
        if (StringUtils.isNotBlank(setupScript)) {
            setupImports = new StringBuilder();
            Matcher importMatcher = IMPORT_PATTERN.matcher(setupScript);

            while (importMatcher.find()) {
                setupImports.append(importMatcher.group()).append("\n");
            }

            if (setupImports.length() > 0) {
                setupScript = IMPORT_PATTERN.matcher(setupScript).replaceAll("");
            }
            setupScript = processDefineExpose(setupScript,vueTemplateResult.getHasModuleStyle());
        }

        Matcher matcher = EXPORT_DEFAULT_PATTERN.matcher(script);
        if (!matcher.find()) {
            return script;
        }


        StringBuilder result = new StringBuilder(script);
        StringBuilder insertCode=new StringBuilder(vueTemplateResult.getTemplate());
        if (setupScript != null) {
            insertCode.append("    setup() {\n");
            insertCode.append(setupScript).append("\n");
            insertCode.append("    }\n");
        }else if(vueTemplateResult.getHasModuleStyle()){
            insertCode.append("    setup() {\n return { " + VueGlobal.DEFAULT_MODULE_NAME + "} }\n");
        }

        int bracePos = matcher.start(1);
        // processedResult 已经包含完整的 template、styles、$style 代码
        result.insert(bracePos + 1, "\n" + insertCode);

        if (setupImports != null) result.insert(0, setupImports);

        return StringUtils.trimToEmpty(vueTemplateResult.getStyleInjectScript())+"\n"+ result.toString();
    }

    /**
     * 处理 defineExpose，将其转换为 return 语句
     * <p>
     * 优先使用显式的 defineExpose({...})，否则用 Rhino AST 解析器自动提取顶层声明。
     *
     * @param setupCode setup 函数中的代码
     * @param style     是否包含样式模块
     * @return 处理后的代码（defineExpose 被替换为 return，或自动追加 return）
     */
    private static String processDefineExpose(String setupCode, boolean style) {
        // 1. 先尝试匹配显式的 defineExpose
        Matcher matcher = DEFINE_EXPOSE_PATTERN.matcher(setupCode);
        if (matcher.find()) {
            String exposeArgs = matcher.group(1).trim();
            String returnStatement = !style ?
                    "return " + exposeArgs + ";"
                    : "return { ..." + exposeArgs + ", ..." + VueGlobal.ALL_STYLES_NAME + " };";
            return matcher.replaceAll(returnStatement);
        }

        // 2. 没有 defineExpose → 用 Rhino AST 自动提取顶层声明
       return extractSetupExportsWithRhino(setupCode, style);

    }

    /**
     * 使用 Rhino AST 解析器提取 &lt;script setup&gt; 顶层声明，自动生成 return 语句。
     * 先通过 Babel (preset-env) 将代码转换为 ES5，确保 Rhino 能正确解析。
     * Babel 会注入 helper（如 _typeof、_objectSpread 等），通过交叉比对原始代码过滤。
     */
    private static String extractSetupExportsWithRhino(String setupCode, boolean style) {
        try {
            // Babel 转换为 ES5，处理所有 Rhino 不支持的语法
            String transpiled = TypeScriptToJs.convertJs(setupCode, "setup.vue", false);
            // 从 Babel 输出中提取所有顶层声明
            Set<String> exports = parseExportsFromJs(transpiled);

            // 过滤掉 Babel 注入的 helper（不在原始代码中出现的名字即为 helper）
            exports.removeIf(name ->
                    !Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(setupCode).find()
            );

            String vars = String.join(", ", exports);
            String returnStatement = !style
                    ? "return { " + vars + " };"
                    : "return { " + vars + ", ..." + VueGlobal.ALL_STYLES_NAME + " };";
            return setupCode + "\n" + returnStatement;
        } catch (Exception e) {
            log.warn("extractSetupExportsWithRhino failed: {}", e.getMessage());
            return setupCode + "\nreturn {};";
        }
    }

    /**
     * 用 Rhino AST 解析 JS 代码，提取顶层变量和函数名
     */
    private static Set<String> parseExportsFromJs(String code) {
        CompilerEnvirons compilerEnv = new CompilerEnvirons();
        Parser parser = new Parser(compilerEnv);
        AstRoot root = parser.parse(code, null, 1);

        Set<String> exports = new LinkedHashSet<>();
        for (Node child : root) {
            if (child instanceof VariableDeclaration) {
                for (VariableInitializer vi : ((VariableDeclaration) child).getVariables()) {
                    extractNamesFromTarget(vi.getTarget(), exports);
                }
            } else if (child instanceof FunctionNode) {
                FunctionNode fn = (FunctionNode) child;
                if (fn.getFunctionType() == FunctionNode.FUNCTION_STATEMENT
                        && fn.getFunctionName() != null) {
                    exports.add(fn.getFunctionName().getIdentifier());
                }
            }
        }
        return exports;
    }

    /**
     * 从声明目标节点递归提取变量名。
     * 处理：简单变量、对象解构、数组解构、嵌套解构。
     */
    private static void extractNamesFromTarget(AstNode node, Set<String> names) {
        if (node == null) return;
        if (node instanceof Name) {
            names.add(((Name) node).getIdentifier());
        } else if (node instanceof ArrayLiteral) {
            for (AstNode el : ((ArrayLiteral) node).getElements()) {
                extractNamesFromTarget(el, names);
            }
        } else if (node instanceof ObjectLiteral) {
            for (ObjectProperty prop : ((ObjectLiteral) node).getElements()) {
                // 解构 { a }   → left=a, right=null → 暴露 a（从 left 拿）
                // 解构 { a: b } → left=a, right=b    → 暴露 b（从 right 拿）
                // 解构 { a: { b } } → right 是嵌套 ObjectLiteral → 递归
                AstNode binding = prop.getRight();
                if (binding != null) {
                    extractNamesFromTarget(binding, names);
                } else {
                    extractNamesFromTarget(prop.getLeft(), names);
                }
            }
        }
        // rest element (...) 或其他复杂模式直接忽略
    }

    /**
     * 处理 script 内容，移除 export default 关键字并添加 template 属性
     *
     * @param scriptContent Vue 组件的 script 内容
     * @param template      模板 HTML 字符串
     * @return 处理后的对象字面量字符串（不含 export default 前缀）
     */
    private static String processScript(String scriptContent, String template) {
        // 检查是否已经有 template
//        if (scriptContent.contains("template:")) {
//            return scriptContent;
//        }

        // 使用正则匹配 export default {
        Matcher matcher = EXPORT_DEFAULT_PATTERN.matcher(scriptContent);
        if (!matcher.find()) {
            return scriptContent;
        }

        // 找到 { 的位置
        int bracePos = matcher.start(1);

        // 在 { 后面插入 template:
        StringBuilder result = new StringBuilder(scriptContent);
        result.insert(bracePos + 1, "\n    template: `" + escapeTemplate(template) + "`,");

        return result.toString();
    }


    /**
     * 转义模板字符串中的特殊字符，使其可以安全地嵌入到 JavaScript 模板字符串中
     *
     * @param template 原始模板字符串
     * @return 转义后的字符串（反斜杠、反引号、${占位符} 都会被转义）
     */
    private static String escapeTemplate(String template) {
        return template
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${", "\\${");
    }


}
