package com.github.easy30.vue4j;


import com.github.easy30.vue4j.object.TemplateResult;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vue 单文件组件转换器 - 将 .vue 文件转换为浏览器可用的 JS 文件
 */
public class VueToJs {

    // 移除了用于匹配 template 和 script 标签的正则表达式，改用 jsoup 解析
    //private static final Pattern TEMPLATE_PATTERN = Pattern.compile("<template\\b[^>]*>([\\s\\S]*)</template>", Pattern.DOTALL);
    //private static final Pattern SCRIPT_PATTERN = Pattern.compile( "<script\\b[^>]*>([\\s\\S]*?)</script>", Pattern.DOTALL);
    //private static final Pattern NAME_PATTERN = Pattern.compile("name:\\s*['"](\\w+)['"]");
    private static final Pattern EXPORT_DEFAULT_PATTERN = Pattern.compile("export\\s+default\\s+(\\{)", Pattern.DOTALL);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+.*?(?:;|$)", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern DEFINE_EXPOSE_PATTERN = Pattern.compile("defineExpose\\(([^)]*)\\)");

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
        }
        if (StringUtils.isBlank(script)) {
            script = "export default { }";
        }

        // 处理 <script setup>
        if (scriptSetupElement != null) {
            setupScript = scriptSetupElement.html().trim();
        }
        


        return convertContent(vueTemplateResult, script, setupScript);
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
        
        if (StringUtils.isNotBlank(setupScript)) {
            setupImports = new StringBuilder();
            Matcher importMatcher = IMPORT_PATTERN.matcher(setupScript);

            while (importMatcher.find()) {
                setupImports.append(importMatcher.group()).append("\n");
            }

            if (setupImports.length() > 0) {
                setupScript = IMPORT_PATTERN.matcher(setupScript).replaceAll("");
            }
            setupScript = processDefineExpose(setupScript);
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
        }

        int bracePos = matcher.start(1);
        // processedResult 已经包含完整的 template、styles、$style 代码
        result.insert(bracePos + 1, "\n" + insertCode);

        if (setupImports != null) result.insert(0, setupImports);

        return StringUtils.trimToEmpty(vueTemplateResult.getStyleInjectScript())+"\n"+ result.toString();
    }

    /**
     * 处理 defineExpose，将其转换为 return 语句
     *
     * @param setupCode setup 函数中的代码
     * @return 处理后的代码（defineExpose 被替换为 return）
     */
    private static String processDefineExpose(String setupCode) {
        Matcher matcher = DEFINE_EXPOSE_PATTERN.matcher(setupCode);
        if (matcher.find()) {
            String exposeArgs = matcher.group(1).trim();
            // 将 defineExpose({...}) 替换为 return {...};
            String returnStatement = "return " + exposeArgs + ";";
            return matcher.replaceAll(returnStatement);
        }
        return setupCode;
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
