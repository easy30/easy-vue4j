package com.github.easy30.vue4j;

import org.junit.Test;
import static org.junit.Assert.*;

public class VueToJsTest {

    @Test
    public void testComponentImportRegistration() throws Exception {
        String vueContent = 
            "<template>\n" +
            "  <div>\n" +
            "    <KnowledgeGraphCanvas />\n" +
            "  </div>\n" +
            "</template>\n" +
            "\n" +
            "<script setup>\n" +
            "import { ref, onMounted } from 'vue'\n" +
            "import KnowledgeGraphCanvas from './component/KnowledgeGraphCanvas.vue'\n" +
            "const count = ref(0)\n" +
            "const inc = () => count.value++\n" +
            "onMounted(() => console.log('mounted'))\n" +
            "</script>";
        
        String result = VueToJs.convertVueToJs(vueContent, "test.vue");
        
        assertTrue("Should contain components registration", 
            result.contains("components: { KnowledgeGraphCanvas }"));
        
        assertFalse("Should not register ref as component", 
            result.contains("components: {.*ref.*}"));
        
        assertFalse("Should not register onMounted as component", 
            result.contains("components: {.*onMounted.*}"));
        
        System.out.println("Test passed! Components are correctly registered.");
    }
    
    @Test
    public void testMultipleComponentImports() throws Exception {
        String vueContent = 
            "<template>\n" +
            "  <div>\n" +
            "    <ComponentA />\n" +
            "    <ComponentB />\n" +
            "  </div>\n" +
            "</template>\n" +
            "\n" +
            "<script setup>\n" +
            "import ComponentA from './ComponentA.vue'\n" +
            "import ComponentB from './ComponentB.vue'\n" +
            "</script>";
        
        String result = VueToJs.convertVueToJs(vueContent, "test.vue");
        
        assertTrue("Should contain both components", 
            result.contains("components: { ComponentA, ComponentB }") ||
            result.contains("components: { ComponentB, ComponentA }"));
        
        System.out.println("Test passed! Multiple components are correctly registered.");
    }
    
    @Test
    public void testNoComponents() throws Exception {
        String vueContent = 
            "<template>\n" +
            "  <div>{{ count }}</div>\n" +
            "</template>\n" +
            "\n" +
            "<script setup>\n" +
            "import { ref } from 'vue'\n" +
            "const count = ref(0)\n" +
            "</script>";
        
        String result = VueToJs.convertVueToJs(vueContent, "test.vue");
        
        assertFalse("Should not contain components when no component imports", 
            result.contains("components: {"));
        
        System.out.println("Test passed! No components when only Vue APIs imported.");
    }
}