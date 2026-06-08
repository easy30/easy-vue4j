package com.github.easy30.vue4j;


import com.github.easy30.vue4j.object.TemplateResult;
import com.github.easy30.vue4j.util.AstParser;
import com.github.easy30.vue4j.util.ScriptUtil;
import com.github.easy30.vue4j.util.VueGlobal;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
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

    // ===== 新增宏处理正则 =====
    private static final Pattern DEFINE_PROPS_STMT = Pattern.compile(
            "const\\s+\\w+\\s*=\\s*defineProps\\s*\\(", Pattern.DOTALL);
    private static final Pattern DEFINE_EMITS_STMT = Pattern.compile(
            "const\\s+\\w+\\s*=\\s*defineEmits\\s*\\(", Pattern.DOTALL);

    // 提取结果存储，按 Vue 官方规范编译
    private static String extractedPropsDef = null;
    private static String extractedEmitsDef = null;
    private static boolean extractedHasProps = false;
    private static boolean extractedHasEmits = false;
    private static boolean extractedHasExpose = false;
    // ===== 新增结束 =====
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
        
        // 预处理：保存自定义组件标签的原始大小写（Jsoup会转为小写）
        Map<String, String> tagCaseMap = extractCustomTagNames(vueContent);
        
        Document doc = Jsoup.parse(vueContent);
        // 生成组件唯一 ID
        String componentId = generateComponentId(fullName);

        // 使用 VueTemplate 处理所有 style 标签（直接从 doc 提取 template）
        VueTemplate vueTemplate = new VueTemplate(componentId);
        TemplateResult vueTemplateResult = vueTemplate.process(doc);
        
        // 恢复 template 中自定义标签的原始大小写
        String restoredTemplate = restoreTagCase(vueTemplateResult.getTemplate(), tagCaseMap);
        vueTemplateResult = new TemplateResult(restoredTemplate, 
                vueTemplateResult.getStyleInjectScript(), 
                vueTemplateResult.getHasModuleStyle());

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
        // 重置提取状态（防止 .vue 文件间泄漏）
        extractedPropsDef = null;
        extractedEmitsDef = null;
        extractedHasProps = false;
        extractedHasEmits = false;
        extractedHasExpose = false;

        StringBuilder setupImports = null;
        Set<String> importedNames = new LinkedHashSet<>(); // 存储 import 的名称
        
        if (StringUtils.isNotBlank(setupScript)) {
            setupImports = new StringBuilder();
            Matcher importMatcher = IMPORT_PATTERN.matcher(setupScript);

            while (importMatcher.find()) {
                String importStmt = importMatcher.group();
                setupImports.append(importStmt).append("\n");
                // 从 import 语句中提取名称
                importedNames.addAll(extractNamesFromImport(importStmt));
            }

            if (setupImports.length() > 0) {
                setupScript = IMPORT_PATTERN.matcher(setupScript).replaceAll("");
            }
            // 统一处理 defineProps / defineEmits / defineExpose
            setupScript = processSetupScript(setupScript, vueTemplateResult.getHasModuleStyle(), importedNames);
        }

        Matcher matcher = EXPORT_DEFAULT_PATTERN.matcher(script);
        if (!matcher.find()) {
            return script;
        }


        StringBuilder result = new StringBuilder(script);
        StringBuilder insertCode=new StringBuilder(vueTemplateResult.getTemplate());

        // 插入提取出的 props 选项（Vue 3 官方规范：defineProps -> props:{}）
        if (extractedPropsDef != null && !extractedPropsDef.isEmpty()) {
            insertCode.append("    props: { ").append(extractedPropsDef).append(" },\n");
        }
        // 插入提取出的 emits 选项（Vue 3 官方规范：defineEmits -> emits:[]）
        if (extractedEmitsDef != null && !extractedEmitsDef.isEmpty()) {
            insertCode.append("    emits: ").append(extractedEmitsDef).append(",\n");
        }
        
        // 插入 components 选项：将 import 的组件名称注册到 components（Vue 3 <script setup> 自动注册行为）
        if (!importedNames.isEmpty()) {
            StringBuilder componentsStr = new StringBuilder("    components: { ");
            boolean first = true;
            for (String name : importedNames) {
                // 过滤掉 Vue 的响应式 API 和已知的非组件导入
                if (!isBuiltInVueName(name)) {
                    if (!first) componentsStr.append(", ");
                    componentsStr.append(name);
                    first = false;
                }
            }
            if (!first) {
                componentsStr.append(" },\n");
                insertCode.append(componentsStr);
            }
        }

        if (setupScript != null) {
            // 按 Vue 3 官方规范构建 setup 签名：只包含实际需要的参数
            String setupSig = buildSetupSignature();
            insertCode.append("    ").append(setupSig).append(" {\n");
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
     * 判断是否是 Vue 内置的响应式 API 名称（不需要注册为组件）
     */
    private static boolean isBuiltInVueName(String name) {
        Set<String> builtIns = new HashSet<>(Arrays.asList(
            "ref", "reactive", "computed", "watch", "watchEffect",
            "onMounted", "onUnmounted", "onCreated", "onBeforeMount",
            "onBeforeUnmount", "onUpdated", "onBeforeUpdate",
            "nextTick", "defineProps", "defineEmits", "defineExpose",
            "provide", "inject", "readonly", "shallowRef", "shallowReactive"
        ));
        return builtIns.contains(name);
    }

    /**
     * 按 Vue 3 官方规范构建 setup 函数签名。
     * 只包含实际需要的参数：defineProps->props、defineEmits->emit、defineExpose->expose
     */
    private static String buildSetupSignature() {
        List<String> firstParam = new ArrayList<>();
        List<String> contextProps = new ArrayList<>();
        if (extractedHasProps) firstParam.add("props");
        if (extractedHasEmits) contextProps.add("emit");
        if (extractedHasExpose) contextProps.add("expose");

        StringBuilder sig = new StringBuilder("setup(");
        if (!firstParam.isEmpty()) {
            sig.append(String.join(", ", firstParam));
        }
        if (!contextProps.isEmpty()) {
            if (!firstParam.isEmpty()) sig.append(", ");
            sig.append("{ ").append(String.join(", ", contextProps)).append(" }");
        }
        sig.append(")");
        return sig.toString();
    }


    /**
     * 按 Vue 3 官方规范处理 <script setup> 中的三个编译时宏：
     *   1. defineProps({...}) -> 提取到组件 props: {} 选项
     *   2. defineEmits([...]) -> 提取到组件 emits: [] 选项
     *   3. defineExpose({...}) -> 编译为 expose({...}) 调用
     *   4. 自动提取所有顶层声明生成 return（模板绑定）
     *   5. components: { ... } -> 提取组件名加入导出（支持模板使用）
     *
     * @param setupCode    setup 函数中的代码
     * @param style        是否包含样式模块
     * @param importedNames 从 import 语句中提取的名称（组件、函数等）
     * @return 处理后的代码（三个宏已被正确处理）
     */
    private static String processSetupScript(String setupCode, boolean style, Set<String> importedNames) {
        // 一次性解析，提取所有需要的信息（替代原来的三次独立解析）
        AstParser.AstResult astResult = AstParser.parseOnce(setupCode);
        
        // 0. 获取 components: { ... } 中的组件名（如果用户显式写了）
        Set<String> componentNames = new HashSet<>(astResult.getComponentNames());
        
        // 合并 import 的名称到组件名（import 的组件也需要导出）
        if (importedNames != null) {
            componentNames.addAll(importedNames);
        }

        // 1. defineExpose({...}) -> expose({...})
        Matcher exposeMatcher = DEFINE_EXPOSE_PATTERN.matcher(setupCode);
        if (exposeMatcher.find()) {
            String exposeArgs = exposeMatcher.group(1).trim();
            setupCode = exposeMatcher.replaceAll("expose(" + exposeArgs + ")");
            extractedHasExpose = true;
        }

        // 2. const props = defineProps({...}) -> 提取到 extractedPropsDef，移除语句
        // 直接从一次解析结果中获取 props 定义（JavaScript端已处理好格式）
        if (astResult.isHasProps()) {
            extractedPropsDef = astResult.getPropsDef();
            extractedHasProps = true;
            // 移除 defineProps 语句，使用原始参数内容计算移除长度
            Matcher pm = DEFINE_PROPS_STMT.matcher(setupCode);
            if (pm.find()) {
                int parenStart = pm.end();
                setupCode = removeStatementAt(setupCode, pm.start(), parenStart, astResult.getPropsRaw());
            }
        }

        // 3. const emit = defineEmits([...]) -> 提取到 extractedEmitsDef，移除语句
        // 直接从一次解析结果中获取 emits 定义（JavaScript端已处理好格式）
        if (astResult.isHasEmits()) {
            extractedEmitsDef = astResult.getEmitsDef();
            extractedHasEmits = true;
            // 移除 defineEmits 语句，使用原始参数内容计算移除长度
            Matcher em = DEFINE_EMITS_STMT.matcher(setupCode);
            if (em.find()) {
                int parenStart = em.end();
                setupCode = removeStatementAt(setupCode, em.start(), parenStart, astResult.getEmitsRaw());
            }
        }

        // 4. 自动提取所有顶层声明，生成 return { ... }（模板绑定）
        //    同时将 components: {...} 中的组件名加入导出
        //    传递 astResult 避免重复解析
        return extractSetupExportsWithRhino(setupCode, style, componentNames, astResult);
    }

    /**
     * 已废弃：使用 AstParser.parseOnce() 替代
     * 原有的 extractComponentNamesWithAcorn 和 extractBalancedParenWithAcorn 方法
     * 已合并到 AstParser 类中，实现一次解析多次提取，提升性能
     */

    /**
     * 从 import 语句中提取导入的名称
     * 支持：
     * - import Foo from './foo.vue'
     * - import { a, b } from 'xxx'
     * - import * as ns from 'xxx'
     * - import { a as b } from 'xxx'
     */
    private static Set<String> extractNamesFromImport(String importStmt) {
        Set<String> names = new LinkedHashSet<>();
        
        // 移除 import 关键字和末尾的分号
        String s = importStmt.replaceAll("^import\\s+", "").replaceAll(";\\s*$", "");
        
        // 处理 import * as ns from 'xxx'
        Pattern namespacePattern = Pattern.compile("\\*\\s+as\\s+(\\w+)");
        Matcher nm = namespacePattern.matcher(s);
        if (nm.find()) {
            names.add(nm.group(1));
            return names;
        }
        
        // 处理 import Foo from 'xxx' 或 import { a, b } from 'xxx'
        // 找到 from 之前的部分
        int fromIndex = s.indexOf(" from ");
        if (fromIndex > 0) {
            String importPart = s.substring(0, fromIndex).trim();
            
            // 如果有花括号，说明是命名导入
            if (importPart.startsWith("{") && importPart.endsWith("}")) {
                // 提取 { a, b } 或 { a as b } 中的名称
                String insideBraces = importPart.substring(1, importPart.length() - 1).trim();
                String[] parts = insideBraces.split(",");
                for (String part : parts) {
                    part = part.trim();
                    // 处理 { a as b } 格式，提取 b
                    if (part.contains(" as ")) {
                        String[] asParts = part.split("\\s+as\\s+");
                        if (asParts.length == 2) {
                            names.add(asParts[1].trim());
                        }
                    } else {
                        names.add(part);
                    }
                }
            } else {
                // 默认导入：import Foo from 'xxx'
                names.add(importPart);
            }
        }
        
        return names;
    }

    /**
     * 从指定位置开始提取平衡括号内的内容（含字符串/模板字符串处理）。
     * 如 extractBalancedParen("abc(de(f)gh)", 3) -> "de(f)gh"
     */
    private static String extractBalancedParen(String code, int fromIndex) {
        if (fromIndex >= code.length() || code.charAt(fromIndex) != '(') return null;
        int depth = 0;
        int i = fromIndex;
        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return code.substring(fromIndex + 1, i);
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < code.length() && code.charAt(i) != quote) {
                    if (code.charAt(i) == '\\') i++;
                    i++;
                }
            } else if (c == '`') {
                i++;
                while (i < code.length() && code.charAt(i) != '`') {
                    if (code.charAt(i) == '\\') i++;
                    if (code.charAt(i) == '$' && i + 1 < code.length() && code.charAt(i + 1) == '{') {
                        i += 2;
                        int braceDepth = 1;
                        while (i < code.length() && braceDepth > 0) {
                            if (code.charAt(i) == '{') braceDepth++;
                            else if (code.charAt(i) == '}') braceDepth--;
                            i++;
                        }
                    }
                    i++;
                }
            }
            i++;
        }
        return null;
    }

    /**
     * 移除从指定位置开始的函数调用语句。
     */
    private static String removeStatementAt(String code, int stmtStart, int parenStart, String argsContent) {
        int end = parenStart + 1 + (argsContent != null ? argsContent.length() : 0);
        while (end < code.length() && (code.charAt(end) == ';' || code.charAt(end) == ' ' || code.charAt(end) == '\t')) {
            end++;
        }
        return code.substring(0, stmtStart) + code.substring(end);
    }


    /**
     * 使用 Rhino AST 解析器提取 &lt;script setup&gt; 顶层声明，自动生成 return 语句。
     * 先通过 Babel (preset-env) 将代码转换为 ES5，确保 Rhino 能正确解析。
     * Babel 会注入 helper（如 _typeof、_objectSpread 等），通过交叉比对原始代码过滤。
     */
    private static String extractSetupExportsWithRhino0(String setupCode, boolean style) {
        return extractSetupExportsWithRhino(setupCode, style, null, null);
    }

    private static String extractSetupExportsWithRhino(String setupCode, boolean style, Set<String> extraExports) {
        return extractSetupExportsWithRhino(setupCode, style, extraExports, null);
    }

    private static String extractSetupExportsWithRhino(String setupCode, boolean style, Set<String> extraExports, AstParser.AstResult astResult) {
        // 如果没有传入 astResult，才进行解析（兼容旧调用方式）
        if (astResult == null) {
            astResult = AstParser.parseOnce(setupCode);
        }
        Set<String> exports = new LinkedHashSet<>(astResult.getTopLevelNames());
        
        // 过滤掉不在代码中实际使用的名称
        exports.removeIf(name ->
                !Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(setupCode).find()
        );

        // 合并 extraExports（包含 import 的组件名和 components 对象中的组件名）
        if (extraExports != null && !extraExports.isEmpty()) {
            exports.addAll(extraExports);
        }

        // 如果没有任何导出，至少导出 import 的组件（防止模板找不到组件）
        if (exports.isEmpty() && extraExports != null && !extraExports.isEmpty()) {
            exports.addAll(extraExports);
        }

        String vars = String.join(", ", exports);
        String returnStatement = !style
                ? "return { " + vars + " };"
                : "return { " + vars + ", ..." + VueGlobal.ALL_STYLES_NAME + " };";
        return setupCode + "\n" + returnStatement;
    }




    /**
     * 用 Rhino AST 解析 JS 代码，提取顶层变量和函数名
     */
    private static Set<String> parseExportsFromJs(String code) {
        CompilerEnvirons compilerEnv = new CompilerEnvirons();
        compilerEnv.setLanguageVersion(Context.VERSION_ES6);  // 加上这行
        compilerEnv.setAllowMemberExprAsFunctionName(true); // 允许某些 ES6 语法
        compilerEnv.setStrictMode(true);         // 强制严格模式（ES6 模块等需要）
        compilerEnv.setRecordingComments(false); // 可选，减少干扰
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
     * 从 Vue 文件内容中提取自定义组件标签的原始大小写
     * Jsoup 会将所有标签名转为小写，需要在解析前保存原始大小写
     */
    private static Map<String, String> extractCustomTagNames(String vueContent) {
        Map<String, String> tagCaseMap = new HashMap<>();
        
        // 匹配自定义组件标签（首字母大写或包含连字符）
        Pattern tagPattern = Pattern.compile("<([A-Z][a-zA-Z0-9]*|[a-z]+-[a-z-]+)(\\s|>)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = tagPattern.matcher(vueContent);
        
        while (matcher.find()) {
            String originalTag = matcher.group(1);
            String lowerTag = originalTag.toLowerCase();
            // 只保存首字母大写的组件标签（排除原生 HTML 标签）
            if (Character.isUpperCase(originalTag.charAt(0))) {
                tagCaseMap.put(lowerTag, originalTag);
            }
        }
        
        return tagCaseMap;
    }

    /**
     * 恢复 template 中自定义标签的原始大小写
     */
    private static String restoreTagCase(String template, Map<String, String> tagCaseMap) {
        if (tagCaseMap.isEmpty()) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, String> entry : tagCaseMap.entrySet()) {
            String lowerTag = entry.getKey();
            String originalTag = entry.getValue();
            
            // 替换开始标签
            result = result.replace("<" + lowerTag, "<" + originalTag);
            // 替换结束标签
            result = result.replace("</" + lowerTag, "</" + originalTag);
        }
        
        return result;
    }

    public static void main(String[] args) {
        String code=

                 "const importExcel = (e) => __async(null, null, function* () {\n" +
                         "  const file = e.target.files[0];\n" +
                         "  if (!file) return;\n" +
                         "  e.target.value = \"\";\n" +
                         "  const reader = new FileReader();\n" +
                         "  reader.onload = (evt) => __async(null, null, function* () {\n" +
                         "    var _a, _b, _c, _d;\n" +
                         "    try {\n" +
                         "      const wb = XLSX.read(evt.target.result, { type: \"array\" });\n" +
                         "      const nodeSheet = wb.Sheets[\"\\u8282\\u70B9\"];\n" +
                         "      const edgeSheet = wb.Sheets[\"\\u5173\\u7CFB\"];\n" +
                         "      if (!nodeSheet) {\n" +
                         "        ElMessage.error('Excel \\u4E2D\\u672A\\u627E\\u5230\"\\u8282\\u70B9\"\\u5DE5\\u4F5C\\u8868');\n" +
                         "        return;\n" +
                         "      }\n" +
                         "      const nodeRows = XLSX.utils.sheet_to_json(nodeSheet);\n" +
                         "      const edgeRows = edgeSheet ? XLSX.utils.sheet_to_json(edgeSheet) : [];\n" +
                         "      const nodes = [];\n" +
                         "      for (const row of nodeRows) {\n" +
                         "        const name = (row[\"\\u540D\\u79F0\"] || \"\").trim();\n" +
                         "        if (!name) continue;\n" +
                         "        nodes.push({\n" +
                         "          name,\n" +
                         "          description: row[\"\\u63CF\\u8FF0\"] || \"\",\n" +
                         "          nodeType: row[\"\\u7C7B\\u578B\"] || \"entity\"\n" +
                         "        });\n" +
                         "      }\n" +
                         "      const edges = [];\n" +
                         "      for (const row of edgeRows) {\n" +
                         "        const fromName = (row[\"\\u6E90\\u8282\\u70B9\"] || \"\").trim();\n" +
                         "        const toName = (row[\"\\u76EE\\u6807\\u8282\\u70B9\"] || \"\").trim();\n" +
                         "        if (!fromName || !toName) continue;\n" +
                         "        edges.push({\n" +
                         "          fromName,\n" +
                         "          toName,\n" +
                         "          type: row[\"\\u5173\\u7CFB\\u7C7B\\u578B\"] || \"\\u5173\\u8054\",\n" +
                         "          description: row[\"\\u63CF\\u8FF0\"] || \"\"\n" +
                         "        });\n" +
                         "      }\n" +
                         "      const result = yield knowledgeGraphApi.batchImport({ nodes, edges });\n" +
                         "      const nc = (_a = result == null ? void 0 : result.nodeCount) != null ? _a : 0;\n" +
                         "      const ec = (_b = result == null ? void 0 : result.edgeCount) != null ? _b : 0;\n" +
                         "      const nf = (_c = result == null ? void 0 : result.nodeFailCount) != null ? _c : 0;\n" +
                         "      const ef = (_d = result == null ? void 0 : result.edgeFailCount) != null ? _d : 0;\n" +
                         "      let msg = `\\u5BFC\\u5165\\u5B8C\\u6210\\uFF1A\\u65B0\\u5EFA ${nc} \\u4E2A\\u8282\\u70B9\\uFF0C${ec} \\u6761\\u5173\\u7CFB`;\n" +
                         "      if (nf || ef) msg += `\\uFF1B\\u5931\\u8D25 \\u8282\\u70B9${nf} \\u5173\\u7CFB${ef}\\uFF08\\u8BE6\\u60C5\\u89C1\\u540E\\u7AEF\\u65E5\\u5FD7\\uFF09`;\n" +
                         "      ElMessage.success(msg);\n" +
                         "      refreshGraph();\n" +
                         "    } catch (err) {\n" +
                         "      ElMessage.error(\"\\u5BFC\\u5165\\u5931\\u8D25\\uFF1A\" + (err.message || \"\\u6587\\u4EF6\\u89E3\\u6790\\u9519\\u8BEF\"));\n" +
                         "    }\n" +
                         "  });\n" +
                         "  reader.readAsArrayBuffer(file);\n" +
                         "});\n";
               // "let resizeStartX = 0;\n" +
               // "let resizeStartW = 0;"
             try {
                 ;
                 System.out.println(parseExportsFromJs(code));
             }catch (Exception e){
                 e.printStackTrace();
             }
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

    /** 用 Babel.parse（已加载的 Rhino 引擎）解析 ES6 代码，提取顶层变量/函数名 */
    /** 处理 script 内容，移除 export default 关键字并添加 template 属性 */
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
