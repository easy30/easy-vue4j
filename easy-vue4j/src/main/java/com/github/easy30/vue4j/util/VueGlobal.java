package com.github.easy30.vue4j.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Vue 全局常量定义
 */
public class VueGlobal {
    
    /**
     * CSS Modules 样式对象变量名（用于 setup() 返回）
     */
    public static final String DEFAULT_MODULE_NAME = "d_m_sty_ae3dc";
    
    /**
     * 所有样式集合变量名（用于存储所有 module 的样式）
     */
    public static final String ALL_STYLES_NAME = "all_styles_ae3dc";


    public String   getBlankToDefault(String s, String def){
        return StringUtils.isNotBlank(s)?s.trim():def;
    }
}
