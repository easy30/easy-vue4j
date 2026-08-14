import com.helger.css.decl.*;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;
import com.helger.css.ECSSVersion;
import java.util.*;
import java.io.StringWriter;
import java.util.regex.*;

public class TestFullPipeline {

    static final Pattern DEEP_PATTERN = Pattern.compile("(?:::v-deep|:deep)\\(([^)]+)\\)");
    static final Pattern RESTORE_PATTERN = Pattern.compile("__DEEP_(\\d+)__(\\[data-v-[a-z0-9]+\\])?");

    static String preprocessDeep(String css, List<String> innerSelectors) {
        StringBuffer sb = new StringBuffer();
        Matcher m = DEEP_PATTERN.matcher(css);
        int idx = 0;
        while (m.find()) {
            innerSelectors.add(m.group(1).trim());
            m.appendReplacement(sb, Matcher.quoteReplacement("__DEEP_" + (idx++) + "__"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String restoreDeep(String css, List<String> innerSelectors) {
        StringBuffer sb = new StringBuffer();
        Matcher m = RESTORE_PATTERN.matcher(css);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(innerSelectors.get(idx)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String processCss(String css, boolean scoped) {
        try {
            // 预处理
            List<String> deepInner = new ArrayList<>();
            String preprocessed = preprocessDeep(css, deepInner);

            // ph-css 解析
            CascadingStyleSheet parsed = CSSReader.readFromString(preprocessed, ECSSVersion.LATEST);
            if (parsed == null) return "PARSE_ERROR: " + css;

            // scoped
            if (scoped && deepInner.isEmpty()) {
                for (int i = 0; i < parsed.getRuleCount(); i++) {
                    Object rule = parsed.getRuleAtIndex(i);
                    if (rule instanceof CSSStyleRule) {
                        CSSStyleRule sr = (CSSStyleRule) rule;
                        for (int j = 0; j < sr.getSelectorCount(); j++) {
                            CSSSelector sel = sr.getSelectorAtIndex(j);
                            int last = sel.getMemberCount() - 1;
                            if (last >= 0) {
                                ICSSSelectorMember m = sel.getMemberAtIndex(last);
                                String v = m.getAsCSSString();
                                sel.removeMember(last);
                                sel.addMember(new CSSSelectorSimpleMember(v + "[data-v-xxx]"));
                            }
                        }
                    }
                }
            } else if (scoped) {
                for (int i = 0; i < parsed.getRuleCount(); i++) {
                    Object rule = parsed.getRuleAtIndex(i);
                    if (rule instanceof CSSStyleRule) {
                        CSSStyleRule sr = (CSSStyleRule) rule;
                        for (int j = 0; j < sr.getSelectorCount(); j++) {
                            CSSSelector sel = sr.getSelectorAtIndex(j);
                            int last = sel.getMemberCount() - 1;
                            // 找最后一个非 __DEEP_ 的 member
                            int lastNonDeep = -1;
                            for (int k = 0; k <= last; k++) {
                                String v = sel.getMemberAtIndex(k).getAsCSSString();
                                if (!v.startsWith("__DEEP_") && !v.trim().isEmpty()
                                        && !">".equals(v.trim()) && !"+".equals(v.trim()) && !"~".equals(v.trim())) {
                                    lastNonDeep = k;
                                }
                            }
                            if (lastNonDeep >= 0 && lastNonDeep < last) {
                                ICSSSelectorMember m = sel.getMemberAtIndex(lastNonDeep);
                                String v = m.getAsCSSString();
                                sel.removeMember(lastNonDeep);
                                sel.addMember(new CSSSelectorSimpleMember(v + "[data-v-xxx]"));
                            }
                        }
                    }
                }
            }

            StringWriter sw = new StringWriter();
            CSSWriter w = new CSSWriter();
            w.setWriteHeaderText(false);
            w.writeCSS(parsed, sw);
            String result = sw.toString();

            // 恢复
            if (!deepInner.isEmpty()) {
                result = restoreDeep(result, deepInner);
            }
            return result.trim();

        } catch (Exception e) {
            return "EXCEPTION: " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        String[][] tests = {
            { "无 :deep + 无 scoped", ".foo { color: red; }", "false", ".foo { color:red; }" },
            { "无 :deep + scoped", ".foo .bar { color: red; }", "true", ".foo .bar[data-v-xxx] { color:red; }" },
            { ":deep() 在末尾 + scoped", ".wrapper :deep(.xterm) { height: 100%; }", "true", ".wrapper[data-v-xxx] .xterm { height:100%; }" },
            { "仅有 :deep() + scoped", ":deep(.xterm) { height: 100%; }", "true", ".xterm { height:100%; }" },
            { "::v-deep() + scoped", ".wrapper ::v-deep(.xterm) { height: 100%; }", "true", ".wrapper[data-v-xxx] .xterm { height:100%; }" },
            { "无 :deep、无 scoped", "div { margin: 0; }", "false", "div { margin:0; }" },
            { "多个 :deep + scoped", ":deep(.a), :deep(.b) { color: red; }", "true", ".a, .b { color:red; }" },
        };

        for (String[] t : tests) {
            String name = t[0];
            String css = t[1];
            boolean scoped = Boolean.parseBoolean(t[2]);
            String expected = t[3];
            String result = processCss(css, scoped);
            boolean pass = result.equals(expected);
            System.out.println((pass ? "✅" : "❌") + " " + name);
            if (!pass) {
                System.out.println("  Input:    " + css);
                System.out.println("  Got:      " + result);
                System.out.println("  Expected: " + expected);
            }
        }
    }
}
