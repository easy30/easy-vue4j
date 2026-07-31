package com.github.easy30.vue4j;

import com.github.easy30.vue4j.object.ClientException;
import com.github.easy30.vue4j.object.TemplateResult;
import com.github.easy30.vue4j.util.VueGlobal;
import com.helger.css.decl.*;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vue 模板和样式处理器 - 处理一个或多个 <style> 标签，并修改 template
 */
public class VueTemplate {

    private static final Pattern CLASS_SELECTOR_PATTERN = Pattern.compile("\\.([a-zA-Z_-][a-zA-Z0-9_-]*)");

    /**
     * :deep() / ::v-deep() 的正则 — 用于 CSS 预处理
     */
    private static final Pattern DEEP_PATTERN = Pattern.compile("(?:::v-deep|:deep)\\(([^)]+)\\)");

    private static final Logger log = LoggerFactory.getLogger(VueTemplate.class);

    private final String componentId;
    private final String scopedAttribute; // "data-v-" + componentId

    private boolean hasModule = false;
    private boolean hasScoped = false;
    private static String STYLE_INJECT_TEMPLATE;

    static {
        try {
            STYLE_INJECT_TEMPLATE = IOUtils.resourceToString("/vue-style-inject-template.js", Charset.forName("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public VueTemplate(String componentId) {
        this.componentId = componentId;
        this.scopedAttribute = "data-v-" + componentId;
    }

    /**
     * 处理所有 style 标签，并返回包含 template 和 styles 的 JS 代码片段
     *
     * @param doc jsoup 解析后的 Document（包含 template 和 style）
     * @return JavaScript 代码片段（包含 template: `...` 和 styles: `...` 以及 $style 对象）
     * <p>
     * // 返回示例（有 style）：
     * "    template: `<div>...</div>`,\n" +
     * "    styles: `.button { ... }`,\n" +
     * "    $style: { button: '_button_xxx' }\n"
     * <p>
     * // 返回示例（无 style）：
     * "    template: `<div>...</div>`\n"
     */
    public TemplateResult process(Document doc) {

        // 从 doc 中提取 template
        Element templateElement = doc.selectFirst("template");
        if (templateElement == null) {
            throw new RuntimeException("Invalid Vue file: missing <template>");
        }

        List<Element> styleElements = doc.select("style");
        if (styleElements.isEmpty()) {
            String template = unescapeVueTemplate(templateElement.html()).trim();
            // 没有 style，只返回 template
            return new TemplateResult("    template: `" + escape(template) + "`,\n", null,false);
        }


        StringBuilder allCss = new StringBuilder();
        String processedCss = null;
        Map<String, String> moduleClassMapping =new HashMap<>();
        // 遍历所有 style 标签
        for (Element styleElement : styleElements) {
            String cssContent = styleElement.html();
            boolean scoped = styleElement.hasAttr("scoped");
            boolean module = styleElement.hasAttr("module");
            String moduleName = null;

            if (module) {
                 moduleName = styleElement.attr("module");
                if (StringUtils.isBlank(moduleName)) moduleName=VueGlobal.DEFAULT_MODULE_NAME;

                hasModule = true;
            }

            if (scoped) {
                hasScoped = true;
            }

            // 处理 CSS
            processedCss = processCss(cssContent, scoped, module, moduleName,moduleClassMapping);
            allCss.append(processedCss);
        }

        // 如果使用了 scoped，给 template 所有元素添加 data-v-xxx 属性
        if (hasScoped) {
            processTemplateScoped(templateElement);
        }


        // 如果有 module，需要更新 template 中的 class（在 CSS 处理之后）
        if (hasModule && !moduleClassMapping.isEmpty()) {
            processTemplateModule(templateElement, moduleClassMapping);
        }


        // 重新获取处理后的 HTML（需取消转义 &gt; 和 &amp;，保留 &quot; 防止属性值引号冲突）
        String template = unescapeVueTemplate(templateElement.html()).trim();

        // 检查：模板使用了 $style 但没有 <style module> → 常见错误，抛 DisplayException 让前端可见
        if (!hasModule && template.contains("$style.")) {
            throw new ClientException("模板中使用了 $style 但缺少 <style module>。"
                    + "如需使用 CSS Modules 请将 <style scoped> 改为 <style module>。");
        }

        // 构建返回结果
        StringBuilder result = new StringBuilder();
        result.append("    template: `");
        result.append(escape(template));
        result.append("`,\n");

        String styleInjectScript = null;
        boolean hasModuleStyle=false;
        if (allCss.length() > 0) {

            String styleClassMapping="";
            // 如果有 module，添加 $style 对象
            if (hasModule && !moduleClassMapping.isEmpty()) {
                hasModuleStyle=true;
                styleClassMapping="\n"+generateStyleObject(moduleClassMapping)+"\n";
            }
            styleInjectScript = styleClassMapping+STYLE_INJECT_TEMPLATE.replace("{{css}}", escape(allCss.toString())).replace("{{id}}", componentId);


        }

        return new TemplateResult(result.toString(), styleInjectScript,hasModuleStyle);
    }

    // ========================================================================
    // CSS 处理：预处理 :deep() → 占位符 → ph-css 解析 → 加 scoped → 恢复占位符
    // ========================================================================

    /**
     * 处理单个 CSS — 在交给 ph-css 解析前先预处理 :deep() / ::v-deep()
     *
     * <p>ph-css 6.5 不认识 :deep()/::v-deep() 这些 Vue 特有的选择器伪类，
     * 解析会失败并返回 null。所以先替换为临时占位符让 ph-css 能正常解析，
     * 然后在写回时把占位符恢复为原始选择器内容。</p>
     */
    private String processCss(String cssContent, boolean scoped, boolean module, String moduleName, Map<String, String> moduleClassMapping) {
        try {
            // 预处理：收集 :deep(...) 并替换为临时占位符 __DEEP_0__ 等
            List<String> deepInnerSelectors = new ArrayList<>();
            String preprocessedCss = preprocessDeep(cssContent, deepInnerSelectors);

            CascadingStyleSheet cssParsed = CSSReader.readFromString(preprocessedCss, com.helger.css.ECSSVersion.LATEST);

            if (cssParsed == null) {
                return cssContent;
            }

            for (int i = 0; i < cssParsed.getRuleCount(); i++) {
                Object rule = cssParsed.getRuleAtIndex(i);

                if (rule instanceof CSSStyleRule) {
                    CSSStyleRule styleRule = (CSSStyleRule) rule;

                    for (int j = 0; j < styleRule.getSelectorCount(); j++) {
                        CSSSelector selector = styleRule.getSelectorAtIndex(j);

                        if (module) {
                            processSelectorForModule(selector, moduleClassMapping, moduleName);
                        }

                        if (scoped) {
                            // 按选择器维度判断是否含有 deep 占位符
                            if (deepInnerSelectors.isEmpty() || !selectorContainsDeepPlaceholder(selector)) {
                                // 没有 :deep() — 原逻辑：直接追加 [data-v-xxx] 到最后一个 member
                                addScopedToSelector(selector);
                            } else {
                                // 跳出 :deep() 占位符，scoped 加到最后一个非 deep 的 member 上
                                addScopedToSelectorSkippingDeep(selector);
                            }
                        }
                    }
                }
            }

            StringWriter writer = new StringWriter();
            CSSWriter cssWriter = new CSSWriter();
            cssWriter.setWriteHeaderText(false);
            cssWriter.writeCSS(cssParsed, writer);

            String result = writer.toString();

            // 后处理：把占位符恢复（同时去掉占位符上可能追加的 [data-v-xxx]）
            if (!deepInnerSelectors.isEmpty()) {
                result = restoreDeepPlaceholders(result, deepInnerSelectors);
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return cssContent;
        }
    }

    /**
     * 预处理 CSS：把 :deep(.foo) 替换为唯一占位符 __DEEP_0__ 等
     */
    private static String preprocessDeep(String css, List<String> innerSelectors) {
        StringBuffer sb = new StringBuffer();
        Matcher m = DEEP_PATTERN.matcher(css);
        int idx = 0;
        while (m.find()) {
            String inner = m.group(1).trim();
            innerSelectors.add(inner);
            m.appendReplacement(sb, Matcher.quoteReplacement("__DEEP_" + (idx++) + "__"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 后处理：把 __DEEP_N__[data-v-xxx] 恢复为原始选择器内容（去掉 scoped 属性）
     * 或 __DEEP_N__（没加 scoped 的情况）也恢复
     */
    private static String restoreDeepPlaceholders(String css, List<String> innerSelectors) {
        Pattern restorePattern = Pattern.compile("__DEEP_(\\d+)__(\\[data-v-[a-z0-9]+\\])?");
        StringBuffer sb = new StringBuffer();
        Matcher m = restorePattern.matcher(css);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            String replacement = idx < innerSelectors.size() ? innerSelectors.get(idx) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ========================================================================
    // Scoped 选择器处理
    // ========================================================================

    /**
     * 没有 :deep() 时的原逻辑：在最后一个 member 上追加 [data-v-xxx]
     */
    private void addScopedToSelector(CSSSelector selector) {
        int lastIdx = selector.getMemberCount() - 1;
        if (lastIdx >= 0) {
            ICSSSelectorMember lastMember = selector.getMemberAtIndex(lastIdx);
            String originalValue = lastMember.getAsCSSString();

            selector.removeMember(lastIdx);
            selector.addMember(lastIdx, new CSSSelectorSimpleMember(
                    originalValue + "[" + scopedAttribute + "]"
            ));
        }
    }

    /**
     * 有 :deep() 时选择器加 scoped 属性：跳过占位符 member，只对普通 member 加 [data-v-xxx]
     *
     * <p>因为 :deep(.foo) 在预处理阶段被替换成了 __DEEP_N__ 占位符，
     * 所以需要：</p>
     * <ul>
     *   <li>:deep() 在尾部（如 .foo :deep(.bar)） → scoped 加到最后一个 deep 前面的 member</li>
     *   <li>:deep() 在中间（如 .foo :deep(.bar) .baz） → scoped 加到 deep 前面的最后一个 member</li>
     *   <li>仅有 :deep() → 不加 scoped</li>
     * </ul>
     */
    private void addScopedToSelectorSkippingDeep(CSSSelector selector) {
        int memberCount = selector.getMemberCount();
        if (memberCount == 0) return;

        // 找到 deep 占位符前面最近的普通 member
        int scopedTargetIndex = -1;
        for (int i = 0; i < memberCount; i++) {
            String v = selector.getMemberAtIndex(i).getAsCSSString();
            if (isDeepPlaceholder(v)) {
                // 遇到 deep 占位符，跳出循环。scopedTargetIndex 就是它前面最后一个非 deep member
                break;
            }
            if (!v.trim().isEmpty()
                    && !">".equals(v.trim()) && !"+".equals(v.trim()) && !"~".equals(v.trim())) {
                scopedTargetIndex = i;
            }
        }

        if (scopedTargetIndex < 0) {
            // 选择器以 :deep() 开头，整个不加 scoped
            return;
        }

        // 在 deep 前面的最后一个非 deep member 上加 scoped
        ICSSSelectorMember member = selector.getMemberAtIndex(scopedTargetIndex);
        String origValue = member.getAsCSSString();
        selector.removeMember(scopedTargetIndex);
        selector.addMember(scopedTargetIndex, new CSSSelectorSimpleMember(
                origValue + "[" + scopedAttribute + "]"
        ));
    }

    private static boolean isDeepPlaceholder(String value) {
        return value.startsWith("__DEEP_") && value.endsWith("__");
    }

    /**
     * 检查选择器的 member 中是否包含 deep 占位符
     */
    private static boolean selectorContainsDeepPlaceholder(CSSSelector selector) {
        for (int i = 0; i < selector.getMemberCount(); i++) {
            if (isDeepPlaceholder(selector.getMemberAtIndex(i).getAsCSSString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理 module 选择器 - 替换类名
     */
    private void processSelectorForModule(CSSSelector selector, Map<String, String> classMapping, String moduleName) {
        int memberCount = selector.getMemberCount();

        for (int i = 0; i < memberCount; i++) {
            ICSSSelectorMember member = selector.getMemberAtIndex(i);
            String memberStr = member.getAsCSSString();

            // 提取类名
            Matcher matcher = CLASS_SELECTOR_PATTERN.matcher(memberStr);
            if (matcher.matches()) {
                String className = matcher.group(1);
                String hashClassName = generateModuleClassName(moduleName, className);

                // 记录映射关系
                String mappingKey = moduleName != null ? moduleName + "." + className : className;
                classMapping.put(mappingKey, hashClassName);

                // 替换 member
                selector.removeMember(i);
                selector.addMember(new CSSSelectorSimpleMember("." + hashClassName));

                // 由于删除了元素，索引需要回退
                i--;
                memberCount--;
            }
        }
    }


    /**
     * 更新 template 中所有元素的 class（真正的 DOM 处理）
     */
    private void processTemplateModule(Element templateElement, Map<String, String> classMapping) {
        // 遍历 template 内所有元素
        for (Element element : templateElement.getAllElements()) {
            if (element.hasAttr("class")) {
                String classNames = element.attr("class");
                String[] classes = classNames.split("\\s+");

                StringBuilder newClasses = new StringBuilder();
                for (String cls : classes) {
                    if (!cls.isEmpty()) {
                        // 检查是否在映射表中
                        if (classMapping.containsKey(cls)) {
                            // 替换为 hash 类名
                            newClasses.append(classMapping.get(cls)).append(" ");
                        } else {
                            // 保持不变
                            newClasses.append(cls).append(" ");
                        }
                    }
                }

                // 更新 class 属性
                if (newClasses.length() > 0) {
                    element.attr("class", newClasses.toString().trim());
                }
            }

            // 处理 :class 绑定，将 $style. 替换为 def_m_style.
            if (element.hasAttr(":class")) {
                String dynamicClass = element.attr(":class");
                // 替换所有的 $style. 为 def_m_style.
                String updatedClass = dynamicClass.replace("$style.", VueGlobal.DEFAULT_MODULE_NAME + ".");
                if (!updatedClass.equals(dynamicClass)) {
                    element.attr(":class", updatedClass);
                }
            }
        }
    }

    /**
     * 为 template 所有元素添加 scoped 属性（使用 jsoup 遍历）
     */
    private void processTemplateScoped(Element templateElement) {
        // 添加 scoped 属性到 template 根元素
        templateElement.attr(scopedAttribute, "");

        // 遍历 template 内所有元素
        for (Element element : templateElement.getAllElements()) {
            // 排除 template 自身（已经添加过了）
            if (element != templateElement) {
                element.attr(scopedAttribute, "");
            }
        }
    }

    /**
     * 生成 hash 类名
     */
    private String generateModuleClassName(String moduleName, String className) {
        return "_" + className + "_" + GenId.gen(componentId + ":" + StringUtils.trimToEmpty(moduleName), 8);
    }

    private static final Gson GSON = new Gson();

    /**
     * 生成 $style 对象字符串
     */
    private String generateStyleObject(Map<String, String> classMapping) {
        // 使用 Map 来聚合相同 module 前缀的类名
        Map<String, Object> styleObject = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : classMapping.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                String moduleName = parts[0];
                String className = parts[1];

                // 如果该 module 已存在，添加到现有对象；否则创建新对象
                Map<String, String> childMap = (Map<String, String>) styleObject.computeIfAbsent(moduleName, k -> new LinkedHashMap<String, String>());
                childMap.put(className, value);
            } else {
                // 全局类名直接添加
                styleObject.put(key, value);
            }
        }


        return " const   " + VueGlobal.ALL_STYLES_NAME + " = " + GSON.toJson(styleObject) + ";\n";
    }


    private static String escape(String template) {
        return template
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${", "\\${");
    }

    /**
     * 取消 Jsoup html() 输出的部分 HTML 转义，确保 Vue 模板编译器能正确解析。
     * Jsoup 在输出属性值时会将 &gt; 和 &amp; 转为 &amp;gt; 和 &amp;amp;，
     * 但 Vue 的模板编译器不识别这些实体，导致箭头函数 (=&gt;) 和逻辑与 (&amp;&amp;) 失效。
     * 注意保留 &amp;quot; 不反转，防止属性值中的双引号破坏属性结构。
     */
    private static String unescapeVueTemplate(String html) {
        return html
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }


}
