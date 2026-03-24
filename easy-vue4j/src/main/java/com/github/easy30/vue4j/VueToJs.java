package com.github.easy30.vue4j;


import com.helger.css.decl.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        String template = extractTemplate(doc, vueContent);
        
        // 生成组件唯一 ID
        String componentId = generateComponentId(fullName);

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
        
        // 提取并处理 style
        String style = extractAndProcessStyle(doc, componentId);

        return convertContent(script, template, setupScript, style);
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
     * 提取并处理 style 标签，添加组件作用域标识
     *
     * @param doc         jsoup 解析后的文档
     * @param componentId 组件唯一 ID
     * @return 处理后的 CSS 字符串，如果没有 style 标签则返回 null
     */
    private static String extractAndProcessStyle(Document doc, String componentId) {
        Element styleElement = doc.selectFirst("style");
        if (styleElement == null) {
            return null;
        }
        
        String cssContent = styleElement.html();
        boolean scoped = styleElement.hasAttr("scoped");
        
        if (scoped) {
            return addScopedAttribute(cssContent, componentId);
        }
        
        return cssContent;
    }
    
    /**
     * 为 CSS 添加作用域属性选择器
     *
     * @param css         CSS 内容
     * @param componentId 组件唯一 ID
     * @return 添加了作用域的 CSS
     */
    private static String addScopedAttribute(String css, String componentId) {
        try {
            // 使用 ph-css 解析 CSS (ph-css 8.x)
            CascadingStyleSheet cssParsed = CSSReader.readFromString(css);
                 
            if (cssParsed == null) {
                return css; // 解析失败时返回原始 CSS
            }
                
            String scopedAttr = "[data-" + componentId + "]";
                
            // 遍历所有规则 - 参考 CssEditDemo 的方式
            for (int i = 0; i < cssParsed.getRuleCount(); i++) {
                Object rule = cssParsed.getRuleAtIndex(i);
                
                // 检查是否是样式规则 (CSSStyleRule)
                if (rule instanceof CSSStyleRule) {
                    CSSStyleRule styleRule = (CSSStyleRule) rule;

                    // 获取选择器列表并修改
                    List<CSSSelector> selectors = styleRule.getAllSelectors();
                    for (CSSSelector selector : selectors) {
                        // 获取最后一个 Member 并追加作用域属性
                        int lastIdx = selector.getMemberCount() - 1;
                        ICSSSelectorMember lastMember = selector.getMemberAtIndex(lastIdx);
                        String originalValue = lastMember.getAsCSSString();
                        
                        // 移除最后一个 Member
                        selector.removeMember(lastIdx);
                        
                        // 创建新的 Member 并添加（包含作用域属性）
                        ICSSSelectorMember newMember = new CSSSelectorSimpleMember(
                            originalValue + scopedAttr
                        );
                        selector.addMember(newMember);
                    }
                }
            }
                
            // 写回 CSS 字符串
            StringWriter writer = new StringWriter();
            CSSWriter writerObj = new CSSWriter();
            writerObj.writeCSS(cssParsed, writer);
            return writer.toString();
                
        } catch (Exception e) {
            // 如果解析失败，返回原始 CSS
            e.printStackTrace();
            return css;
        }
    }
    
    /**
     * 根据文件名生成组件唯一 ID
     *
     * @param fullName 文件名
     * @return 唯一 ID 字符串
     */
    private static String generateComponentId(String fullName) {
        String baseName = fullName.replace(".vue", "");
        // 使用文件名的 hash 值，保证唯一性且简短
        int hash = Math.abs(fullName.hashCode());
        return "data-v-" + Integer.toHexString(hash);
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

    /**
     * 处理包含 <script setup> 的脚本内容
     *
     * @param script      普通 <script> 的内容（可能为空或默认值）
     * @param template    模板 HTML 字符串
     * @param setupScript <script setup> 中的代码
     * @param style       处理后的 CSS 样式（带作用域）
     * @return 处理后的完整 JavaScript 代码
     */
    private static String convertContent(String script, String template, String setupScript, String style) {


        ///setupScript 处理：提取 import, 转化返回对象.
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

        ///以 script 为基础，找到插入点，插入 template 和 setup (). 最后把 setupImports 放入头部.
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
        
        // style
        if (style != null && !style.isEmpty()) {
            insertCode.append("    styles: `").append(escapeTemplate(style)).append("`");
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
