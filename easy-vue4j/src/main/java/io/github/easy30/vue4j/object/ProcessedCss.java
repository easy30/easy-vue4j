package io.github.easy30.vue4j.object;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
     * 内部类：保存处理后的 CSS 和类名映射
     */
    @Getter
    @Setter
    public class ProcessedCss {
        private  String css;
        private Map<String, String> moduleClassMapping;

        public ProcessedCss(String css, Map<String, String> moduleClassMapping) {
            this.css = css;
            this.moduleClassMapping = moduleClassMapping;
        }
    }