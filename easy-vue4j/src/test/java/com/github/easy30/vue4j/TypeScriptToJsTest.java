package com.github.easy30.vue4j;

import org.junit.Test;

/**
 * TypeScriptToJs 转换测试
 */
public class TypeScriptToJsTest {

    @Test
    public void testDecorators() {
        String tsCode = 
            "@api(\"/api/user\")\n" +
            "class UserApi {\n" +
            "    @post(\"/save\")\n" +
            "    save(user) {}\n" +
            "    \n" +
            "    @post(\"/save2\", \"user\")\n" +
            "    @json\n" +
            "    save2(user, token) {}\n" +
            "    \n" +
            "    @post(\"/update\")\n" +
            "    @form\n" +
            "    update(name, age, email) {}\n" +
            "    \n" +
            "    @get(\"/list\")\n" +
            "    list(page, size) {}\n" +
            "}";
        
        System.out.println("========== 输入 TypeScript ==========");
        System.out.println(tsCode);
        System.out.println();
        
        String jsCode = TypeScriptToJs.convertJs(tsCode, "test.ts", true);
        
        System.out.println("========== 输出 JavaScript ==========");
        System.out.println(jsCode);
    }

    @Test
    public void testSimpleClass() {
        String tsCode = 
            "class Person {\n" +
            "    name: string;\n" +
            "    age: number;\n" +
            "    \n" +
            "    constructor(name: string, age: number) {\n" +
            "        this.name = name;\n" +
            "        this.age = age;\n" +
            "    }\n" +
            "    \n" +
            "    greet(): string {\n" +
            "        return `Hello, ${this.name}`;\n" +
            "    }\n" +
            "}";
        
        System.out.println("========== 输入 TypeScript ==========");
        System.out.println(tsCode);
        System.out.println();
        
        String jsCode = TypeScriptToJs.convertJs(tsCode, "person.ts");
        
        System.out.println("========== 输出 JavaScript ==========");
        System.out.println(jsCode);
    }

    @Test
    public void testInterface() {
        String tsCode = 
            "interface User {\n" +
            "    id: number;\n" +
            "    name: string;\n" +
            "    email?: string;\n" +
            "}\n" +
            "\n" +
            "type Status = 'active' | 'inactive';\n" +
            "\n" +
            "const user: User = {\n" +
            "    id: 1,\n" +
            "    name: '张三'\n" +
            "};";
        
        System.out.println("========== 输入 TypeScript ==========");
        System.out.println(tsCode);
        System.out.println();
        
        String jsCode = TypeScriptToJs.convertJs(tsCode, "interface.ts");
        
        System.out.println("========== 输出 JavaScript ==========");
        System.out.println(jsCode);
    }

    @Test
    public void testAsyncFunction() {
        String tsCode = 
            "async function fetchUser(id: number): Promise<User> {\n" +
            "    const response = await fetch(`/api/user/${id}`);\n" +
            "    return response.json();\n" +
            "}\n" +
            "\n" +
            "const createUser = async (name: string): Promise<void> => {\n" +
            "    await fetch('/api/user', {\n" +
            "        method: 'POST',\n" +
            "        body: JSON.stringify({ name })\n" +
            "    });\n" +
            "};";
        
        System.out.println("========== 输入 TypeScript ==========");
        System.out.println(tsCode);
        System.out.println();
        
        String jsCode = TypeScriptToJs.convertJs(tsCode, "async.ts");
        
        System.out.println("========== 输出 JavaScript ==========");
        System.out.println(jsCode);
    }
}
