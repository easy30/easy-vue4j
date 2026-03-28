package com.github.easy30.vue4j.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Vue 全局常量定义和工具方法
 */
@Slf4j
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
    
    /**
     * 从 classpath 加载 properties 文件（使用 UTF-8 编码）
     * @param filename 文件名（如：easy-vue4j.properties）
     * @return Properties 对象
     */
    public static Properties loadProperties(String filename) {
        Properties props = new Properties();
        try (InputStream is = VueGlobal.class.getClassLoader().getResourceAsStream(filename)) {
            if (is != null) {
                // 使用 UTF-8 编码读取配置文件
                java.io.Reader reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8);
                props.load(reader);
                log.info("Loaded {} successfully with UTF-8 encoding", filename);
            } else {
                log.warn("{} not found, using defaults", filename);
            }
        } catch (IOException e) {
            log.error("Failed to load {}", filename, e);
        }
        return props;
    }
}
