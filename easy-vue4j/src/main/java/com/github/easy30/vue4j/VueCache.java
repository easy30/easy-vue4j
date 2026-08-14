package com.github.easy30.vue4j;

import com.github.easy30.vue4j.util.resource.CacheContent;
import com.github.easy30.vue4j.util.resource.BaseResource;
import com.github.easy30.vue4j.util.resource.ClassPathResource;
import com.github.easy30.vue4j.util.resource.FileResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
public class VueCache {
    /**
     * 缓存转换后的内容：key=文件名，value=CachedContent
     */
    private final ConcurrentHashMap<String, CacheContent> cache = new ConcurrentHashMap<>();

    private String root;
    private boolean isResource;
    private String vueExt;
    // 简单正则：匹配 @api 或 @api(
    private static final Pattern API_DECORATOR_PATTERN = Pattern.compile("@api\\b");
    /**
     *
     * @param root  缺省是 classpath:/static ; 文件路径：/root/web ; 资源路径：classpath:/static/web
     * @param vueExt  Vue 文件扩展名
     */
    public VueCache(String root, String vueExt){
        if(StringUtils.isBlank(root)) {
            log.info("set default resource path: /static");
            isResource=true;
            root="/static";
        }else if(root.startsWith("classpath:")){
            isResource=true;
            root = root.substring(10);
        }
        this.root = root;
        this.vueExt=vueExt;

    }

    /**
     * 带缓存的转换逻辑，避免重复读取和转换。
     * <p>
     * 热更新自动决定：仅当 resource.root 为本地文件路径（非 classpath）时才检查文件变化；
     * classpath / jar 内资源直接使用缓存。
     *
     * @param filename    资源文件名
     * @param servletPath 资源路径
     * @param charset     字符编码
     * @return CacheContent 对象（包含内容和最后修改时间）
     * @throws IOException 当读取资源或转换失败时抛出
     */
    public CacheContent getContent(String filename, String servletPath, String charset) throws IOException {
        BaseResource resource =  isResource?  new ClassPathResource(root+ servletPath)
                : new FileResource(new File(root, servletPath));

        // 本地文件路径才做热更新（能拿到真实 lastModified）；classpath/jar 直接用缓存
        boolean hotReload = !isResource;

        // 检查缓存
        CacheContent  cacheContent= cache.get(servletPath);
        long lastModified=-1 ;
        if (cacheContent != null) {
            if(!hotReload) return cacheContent;
            lastModified = resource.getLastModified();
            if (!hasChanged(cacheContent, lastModified)) {
                log.debug("Cache hit for Vue file: {}", filename);
                return cacheContent;
            }
        }
        lastModified = resource.getLastModified();
        byte[] bytes= getContent(resource,filename,charset);
        if(bytes==null)return null;
        cacheContent = new CacheContent(bytes, lastModified);
        cache.put(servletPath, cacheContent);
        return cacheContent;


    }

    private byte[] getContent( BaseResource resource  ,String filename,String charset ) throws IOException{
        // 读取文件内容
        byte[] bytes=resource.getContent();
        if(bytes==null)return null;
        if (filename.endsWith(vueExt) || filename.endsWith(".js") ||
                filename.endsWith(".mjs") || filename.endsWith(".ts")) {
            String source = new String(bytes, charset);
            String  convertSource=null;
            // 调用转换器转换为 JS
            if (filename.endsWith(vueExt)) {
                // Vue 文件转换
                convertSource = VueToJs.convertVueToJs(source, filename);
            } else if (filename.endsWith(".ts")) {
                // TypeScript 文件：使用 Babel 转换（支持装饰器语法）
                convertSource = TypeScriptToJs.convertJs(source, filename);
            } else if (filename.endsWith(".js") || filename.endsWith(".mjs")) {
                //是否有@api要转换
                if (source.contains("api-aop") && API_DECORATOR_PATTERN.matcher(source).find()) {
                    convertSource = TypeScriptToJs.convertJs(source, filename);
                }
            }
            return convertSource!=null?convertSource.getBytes(charset):bytes;
        }else  return bytes;


    }


    /**
     * 检查资源是否已修改（仅适用于磁盘文件）
     *
     * @param cached              缓存的内容对象
     * @param currentLastModified 资源当前的最后修改时间戳
     * @return true 表示资源已修改，需要重新加载；false 表示未修改
     */
    private boolean hasChanged(CacheContent cached, long currentLastModified) {
        if (currentLastModified < 0) {
            return false;
        }
        return currentLastModified > cached.getLastModified();
    }


    public void clear(){
        cache.clear();
    }
}
