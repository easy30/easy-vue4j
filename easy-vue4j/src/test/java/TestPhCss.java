import com.helger.css.decl.*;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;
import com.helger.css.ECSSVersion;
import java.io.StringWriter;

public class TestPhCss {
    public static void main(String[] args) {
        String[] tests = {
            ".foo { color: red; }",
            ":deep(.xterm) { height: 100%; }",
            ".wrapper :deep(.xterm) { height: 100%; }",
            ".wrapper > :deep(.xterm) { height: 100%; }",
            ".a :deep(.b) > .c { height: 100%; }",
            ".wrapper ::v-deep(.xterm) { height: 100%; }",
        };
        for (String css : tests) {
            System.out.println("=== Input: " + css + " ===");
            try {
                CascadingStyleSheet parsed = CSSReader.readFromString(css, ECSSVersion.LATEST);
                if (parsed == null) {
                    System.out.println("  -> PARSER RETURNED NULL (parse error)");
                    continue;
                }
                System.out.println("  Rules: " + parsed.getRuleCount());
                for (int i = 0; i < parsed.getRuleCount(); i++) {
                    Object rule = parsed.getRuleAtIndex(i);
                    if (rule instanceof CSSStyleRule) {
                        CSSStyleRule sr = (CSSStyleRule) rule;
                        System.out.println("  Selectors: " + sr.getSelectorCount());
                        for (int j = 0; j < sr.getSelectorCount(); j++) {
                            CSSSelector sel = sr.getSelectorAtIndex(j);
                            System.out.println("    Selector " + j + " members: " + sel.getMemberCount());
                            for (int k = 0; k < sel.getMemberCount(); k++) {
                                ICSSSelectorMember m = sel.getMemberAtIndex(k);
                                System.out.println("      [" + k + "] '" + m.getAsCSSString() + "' type=" + m.getClass().getSimpleName());
                            }
                        }
                    }
                }
                StringWriter sw = new StringWriter();
                CSSWriter w = new CSSWriter();
                w.setWriteHeaderText(false);
                w.writeCSS(parsed, sw);
                System.out.println("  Serialized: " + sw.toString().trim());
            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            System.out.println();
        }
    }
}
