package io.github.easy30.vue4j;

/**
 * TypeScriptToJs 转换测试（独立运行）
 */
public class TypeScriptToJsTest {

    public static void main(String[] args) {
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println();
        
        testDecorators();
        System.out.println("\n\n");
        testSimpleClass();
        System.out.println("\n\n");
        testInterface();
    }

    private static void testDecorators() {
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

    private static void testSimpleClass() {
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

    private static void testInterface() {
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
}
