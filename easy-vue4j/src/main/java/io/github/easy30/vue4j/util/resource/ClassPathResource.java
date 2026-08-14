package io.github.easy30.vue4j.util.resource;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class ClassPathResource implements BaseResource {
    private URL fileUrl;

    private long DEFAULT_LAST_MODIFIED = System.currentTimeMillis();
  
    public ClassPathResource(String path) throws  IOException{
          fileUrl =   this.getClass().getResource(path);
          if (fileUrl == null) {
              throw new FileNotFoundException("classpath:" + path);
          }
    }

   
    public long getLastModified() {
        return  getLastModified(fileUrl);
    }

   
    public byte[] getContent() throws IOException {
        try (InputStream inputStream = fileUrl.openStream()) {
            if(inputStream==null)   return null;
            return IOUtils.toByteArray(inputStream);
        }
    }
    private long getLastModified(URL fileUrl) {
        // 获取最后修改时间
        long lastModified = DEFAULT_LAST_MODIFIED;
        if (fileUrl == null) {
            return lastModified;
        }
        String filePath= fileUrl.getFile();
        if (!isJarResource(filePath)) {
            File file = new File(filePath);
            if (file.exists()) {
                lastModified = file.lastModified();
            }
        }
        return lastModified;
    }
    /**
     * 判断资源是否在 JAR 包内（JAR 包内的资源不会变化）
     *
     * @return true 表示资源在 JAR 包内；false 表示不在 JAR 包内或判断失败
     */
    private boolean isJarResource(String url) {

        return url.startsWith("jar:") || url.startsWith("wsjar:");

    }

}
