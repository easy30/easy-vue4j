package com.github.easy30.vue4j;

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
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+.*?;", Pattern.MULTILINE);
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
        String jsContent = convertVueToJs(vueContent, vuePath.getFileName().toString());
        Files.write(jsPath, jsContent.getBytes(charset));
    }

    /**
     * 将 .vue 文件内容转换为 .js 文件内容（字符串形式）
     *
     * @param vueContent .vue 文件的原始内容
     * @param fileName   文件名（用于提取组件名，当 script 中未定义 name 时使用）
     * @return 转换后的 JavaScript 代码字符串
     * @throws IOException 当解析 Vue 文件格式错误时抛出（缺少 template 或 script 标签）
     */
    public static String convertVueToJs(String vueContent, String fileName) throws IOException {
        Document doc = Jsoup.parse(vueContent);
        String template = extractTemplate(doc, vueContent);

        // 处理 <script> 和 <script setup>
        Element scriptElement = doc.selectFirst("script:not([setup])");
        Element scriptSetupElement = doc.selectFirst("script[setup]");

        String script=null;
        String setupScript = null;

        // 处理普通 <script>
        if (scriptElement != null) {
            script = scriptElement.html().trim();
        }
        if(StringUtils.isBlank(script)){
            script = "export default { }";
        }

        // 处理 <script setup>
        if (scriptSetupElement != null) {
            setupScript = scriptSetupElement.html().trim();
        }

        return convertContent(script, template, setupScript);
    }

    /**
     * 从 Vue 文件中提取 template 标签内的内容
     *
     * @param vueContent Vue 文件的完整内容
     * @return 提取的模板 HTML 字符串（已去除首尾空白）
     * @throws IOException 当文件中缺少 template 标签时抛出
     */
    private static String extractTemplate(Document doc, String vueContent) throws IOException {

        Element templateElement = doc.selectFirst("template");
        if (templateElement == null) {
            throw new IOException("Invalid Vue file: missing <template>");
        }
        return templateElement.html().trim();
    }

    /**
     * 从 Vue 文件中提取 script 标签内的内容（仅处理非 setup 的 script）
     *
     * @param vueContent Vue 文件的完整内容
     * @return 提取的 JavaScript 代码字符串（已去除首尾空白）
     * @throws IOException 当文件中缺少 script 标签时抛出
     * @deprecated 改用内联处理方式
     */
//    @Deprecated
//    private static String extractScript(Document doc, String vueContent) throws IOException {
//        Element scriptElement = doc.selectFirst("script");
//        if (scriptElement == null) {
//            throw new IOException("Invalid Vue file: missing <script>");
//        }
//        return scriptElement.html().trim();
//    }

    /**
     * 从 script 内容中提取组件名称，如果未定义则根据文件名生成
     *
     * @param scriptContent Vue 组件的 script 部分内容
     * @param fileName 原始文件名（不含路径）
     * @return 组件名称（首字母大写）
     */
//    private static String extractComponentName(String scriptContent, String fileName) {
//        Matcher matcher = NAME_PATTERN.matcher(scriptContent);
//        if (matcher.find()) {
//            return matcher.group(1);
//        }
//        String baseName = fileName.replace(".vue", "");
//        return capitalizeFirstLetter(baseName);
//    }

    /**
     * 将字符串的首字母转换为大写
     *
     * @param str 待处理的字符串
     * @return 首字母大写后的字符串；如果输入为 null 或空字符串则原样返回
     */
//    private static String capitalizeFirstLetter(String str) {
//        if (str == null || str.isEmpty()) {
//            return str;
//        }
//        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
//    }

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

    /**
     * 处理包含 <script setup> 的脚本内容
     *
     * @param script      普通 <script> 的内容（可能为空或默认值）
     * @param template    模板 HTML 字符串
     * @param setupScript <script setup> 中的代码
     * @return 处理后的完整 JavaScript 代码
     */
    private static String convertContent(String script, String template, String setupScript) {


        ///setupScript处理:提取import, 转化返回对象.
        StringBuilder setupImports = null;
        if (StringUtils.isNotBlank(setupScript)) {
            // 提取所有 import 语句
            setupImports = new StringBuilder();
            Matcher importMatcher = IMPORT_PATTERN.matcher(setupScript);

            while (importMatcher.find()) {
                setupImports.append(importMatcher.group()).append("\n");
            }

            if (setupImports.length() > 0) {
                // 移除所有 import 语句
                setupScript = IMPORT_PATTERN.matcher(setupScript).replaceAll("");
            }
            // 处理 defineExpose 转为 return
            setupScript = processDefineExpose(setupScript);

        }

        ///以script为基础,找到插入点,插入 template 和 setup(). 最后把setupImports放入头部.
        Matcher matcher = EXPORT_DEFAULT_PATTERN.matcher(script);
        if (!matcher.find()) {
            return script;
        }

        // 找到 { 的位置
        int bracePos = matcher.start(1);

        // 在 { 后面插入 template:
        StringBuilder result = new StringBuilder(script);


        //template
        StringBuilder insertCode = new StringBuilder("\n    template: `" + escapeTemplate(template) + "`,\n");
        // setup()
        if (setupScript != null) {
            insertCode.append("    setup() {\n");
            insertCode.append(setupScript).append("\n");
            insertCode.append("    }\n");
        }
        //insert template and setup
        result.insert(bracePos + 1, insertCode);

        //insert setup imports
        if (setupImports != null) result.insert(0, setupImports);

        return result.toString();


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
