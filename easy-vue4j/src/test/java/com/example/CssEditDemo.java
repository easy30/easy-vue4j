package com.example;

import com.helger.css.decl.*;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;

import java.io.StringWriter;
import java.util.List;

public class CssEditDemo {
    public static void main(String[] args) throws Exception {
        String cssText = "body , .table-container th { background: red; color: white; } ";
        
        CascadingStyleSheet styleSheet = CSSReader.readFromString(
                cssText,
                com.helger.css.ECSSVersion.LATEST
        );

        for (int i = 0; i < styleSheet.getRuleCount(); i++) {
            if (styleSheet.getRuleAtIndex(i) instanceof CSSStyleRule) {
                CSSStyleRule styleRule = (CSSStyleRule) styleSheet.getRuleAtIndex(i);
                
                // 删除所有声明
                styleRule.removeAllDeclarations();
                
                // 示例 1: 使用 CSSExpression.valueOf() 创建简单值
                CSSExpression expr1 = CSSExpression.createSimple("blue");
                CSSDeclaration decl1 = new CSSDeclaration("background", expr1, false);
                styleRule.addDeclaration(decl1);
                
                // 示例 2: 使用 parseInline() 解析内联样式表达式
                CSSExpression expr2 = CSSExpression.createSimple("16px");
                CSSDeclaration decl2 = new CSSDeclaration("font-size", expr2, false);
                styleRule.addDeclaration(decl2);

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
                            originalValue + "[data-v-123456]"
                    );
                    selector.addMember(newMember);
                }
                
                // 示例 3: 复杂值（如 margin: 10px 20px）
                // CSSExpression expr3 = CSSExpression.parseInline("10px 20px");
                // styleRule.addDeclaration(new CSSDeclaration("margin", expr3, false));
            }
        }

        StringWriter writer = new StringWriter();
        new CSSWriter().writeCSS(styleSheet, writer);
        System.out.println(writer.toString());
    }
}