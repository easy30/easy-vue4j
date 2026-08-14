package io.github.easy30.vue4j.util.resource;

import java.io.IOException;

public interface BaseResource {
    long getLastModified();
    byte[] getContent()throws IOException;
}
;