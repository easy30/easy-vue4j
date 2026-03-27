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
@Slf4j
public class VueCache {
    /**
     * 缓存转换后的内容：key=文件名，value=CachedContent
     */
    private final ConcurrentHashMap<String, CacheContent> cache = new ConcurrentHashMap<>();

    private String root;
    private int reload;
    private boolean isResource;
    private String vueExt;
    /**
     *
     * @param root  缺省是classpath:/static ; 文件路径: /root/web ; 资源路径:  classpath:/static/web
     * @param reload  0 表示不重新加载; 1表示修改后重新加载 ;2每次都重新加载
     */
    public VueCache(String root, int reload,String vueExt){
        if(StringUtils.isBlank(root)) {
            log.info("set default resource path: /static");
            isResource=true;
            root="/static";
        }else if(root.startsWith("classpath:")){
            isResource=true;
            root = root.substring(10);
        }
        this.root = root;
        this.reload = reload;
        this.vueExt=vueExt;

    }

    /**
     * 带缓存的转换逻辑，避免重复读取和转换
     *
     * @param filename    资源文件名
     * @param servletPath 资源路径
     * @return 转换后的 ByteArrayResource 对象
     * @throws IOException 当读取资源或转换失败时抛出
     */
    public byte[] getContent(String filename, String servletPath, String charset) throws IOException {
        BaseResource resource =  isResource?  new ClassPathResource(root+ servletPath)
                : new FileResource(new File(root, servletPath));
        if(reload!=2) {
            // 检查缓存
            CacheContent  cacheContent= cache.get(servletPath);
            long lastModified=-1 ;
            if (cacheContent != null) {
                if(reload==0) return   cacheContent.getContent();
                lastModified = resource.getLastModified();
                if (!hasChanged(cacheContent, lastModified)) {
                    log.debug("Cache hit for Vue file: {}", filename);
                    return cacheContent.getContent();
                }
            }
           byte[] bytes= getContent(resource,filename,charset);
            cacheContent = new CacheContent(bytes, lastModified);
            cache.put(servletPath, cacheContent);
            return bytes;
        }else {
            return getContent(resource,filename,charset);
        }


    }

    private byte[] getContent( BaseResource resource  ,String filename,String charset ) throws IOException{
        // 读取文件内容
        byte[] bytes=resource.getContent();
        String source = new String(bytes, charset);

        // 调用转换器转换为 JS
        if(filename.endsWith(vueExt)) {
            source = VueToJs.convertVueToJs(source, filename);
        }

        return source.getBytes(charset);
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
