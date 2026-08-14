package io.github.easy30.vue4jdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP AOP 测试 Controller
 * 
 * 用于测试前端 TypeScript + 装饰器生成的 API 调用
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class ApiDemoController {
    
    /**
     * POST JSON - 单参数
     * 对应: @post("/save")
     */
    @PostMapping("/save")
    public Map<String, Object> saveUser(@RequestBody Map<String, Object> user) {
        log.info("收到保存用户请求: {}", user);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "保存成功");
        result.put("data", user);
        return result;
    }
    
    /**
     * POST JSON - 带 token 参数
     * 对应: @post("/update", "data") @json
     */
    @PostMapping("/update")
    public Map<String, Object> updateUser(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false) String token) {
        log.info("收到更新用户请求: data={}, token={}", data, token);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "更新成功");
        result.put("data", data);
        result.put("token", token);
        return result;
    }
    
    /**
     * POST Form - 全量打平
     * 对应: @post("/submit") @form
     */
    @PostMapping("/submit")
    public Map<String, Object> submitForm(
            @RequestParam String name,
            @RequestParam Integer age,
            @RequestParam String email) {
        log.info("收到表单提交: name={}, age={}, email={}", name, age, email);
        
        Map<String, Object> formData = new HashMap<>();
        formData.put("name", name);
        formData.put("age", age);
        formData.put("email", email);
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "提交成功");
        result.put("data", formData);
        return result;
    }
    
    /**
     * GET 请求 - 查询列表
     * 对应: @get("/list")
     */
    @GetMapping("/list")
    public Map<String, Object> getList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("收到查询列表请求: page={}, size={}", page, size);
        
        // 模拟数据
        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("name", "张三");
        item1.put("email", "zhangsan@example.com");
        
        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2);
        item2.put("name", "李四");
        item2.put("email", "lisi@example.com");
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "查询成功");
        result.put("data", new Object[]{item1, item2});
        result.put("page", page);
        result.put("size", size);
        result.put("total", 2);
        return result;
    }
}
