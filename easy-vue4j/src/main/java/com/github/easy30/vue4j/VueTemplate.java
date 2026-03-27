package com.github.easy30.vue4j;

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
            String template = templateElement.html().trim();
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


        // 重新获取处理后的 HTML
        String template = templateElement.html().trim();

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

    /**
     * 处理单个 CSS
     */
    private String processCss(String cssContent, boolean scoped, boolean module, String moduleName,Map<String, String> moduleClassMapping) {
        try {
            CascadingStyleSheet cssParsed = CSSReader.readFromString(cssContent);

            if (cssParsed == null) {
                return cssContent;
            }


            for (int i = 0; i < cssParsed.getRuleCount(); i++) {
                Object rule = cssParsed.getRuleAtIndex(i);

                if (rule instanceof CSSStyleRule) {
                    CSSStyleRule styleRule = (CSSStyleRule) rule;

                    // 处理选择器
                    for (int j = 0; j < styleRule.getSelectorCount(); j++) {
                        CSSSelector selector = styleRule.getSelectorAtIndex(j);

                        if (module) {
                            // Module: 替换类名
                            processSelectorForModule(selector, moduleClassMapping, moduleName);
                        }

                        if (scoped) {
                            // Scoped: 添加 [data-v-xxx]
                            addScopedToSelector(selector);
                        }
                    }
                }
            }

            StringWriter writer = new StringWriter();
            CSSWriter cssWriter=  new CSSWriter();
            cssWriter.setWriteHeaderText( false);
            cssWriter.writeCSS(cssParsed, writer);

            return writer.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return cssContent;
        }
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
     * 为选择器添加 scoped 属性
     */
    private void addScopedToSelector(CSSSelector selector) {
        int lastIdx = selector.getMemberCount() - 1;
        if (lastIdx >= 0) {
            ICSSSelectorMember lastMember = selector.getMemberAtIndex(lastIdx);
            String originalValue = lastMember.getAsCSSString();

            selector.removeMember(lastIdx);
            ICSSSelectorMember newMember = new CSSSelectorSimpleMember(
                    originalValue + "[" + scopedAttribute + "]"
            );
            selector.addMember(newMember);
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


}
