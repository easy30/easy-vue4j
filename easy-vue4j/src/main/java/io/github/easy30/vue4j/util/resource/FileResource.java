package io.github.easy30.vue4j.util.resource;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FileResource implements BaseResource{
    File file;
    public FileResource(File file) throws IOException {
        this.file=file;
        if(!file.exists()) throw new FileNotFoundException(file.getCanonicalPath());
    }
    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public byte[] getContent() throws IOException {
        try(java.io.FileInputStream fis=new java.io.FileInputStream(file)){
            if(fis==null)  return null;
            return IOUtils.toByteArray(fis);
        }
    }
}
