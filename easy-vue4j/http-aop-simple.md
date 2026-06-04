# HTTP AOP 装饰器快速参考

## 配置
```properties
http-aop.body-type=json  # POST默认body类型: json/form
filter.client-js.path=/client-js
```

## 装饰器语法

### 类装饰器
```typescript
@api("/api/user")  // 路径前缀
class UserApi {}
```

### 方法装饰器
| 装饰器 | 说明 |
|--------|------|
| `@post(path, bodyNames?)` | POST请求 |
| `@get(path)` | GET请求(参数自动转query) |
| `@put(path, bodyNames?)` | PUT请求 |
| `@delete(path)` | DELETE请求 |
| `@json` | JSON body(对应@RequestBody) |
| `@form` | Form body(表单提交) |
| `@postJson(path, bodyNames?)` | POST + JSON 快捷方式(推荐) |
| `@postForm(path, bodyNames?)` | POST + Form 快捷方式(推荐) |

## 使用示例

```typescript
@api("/api/user")
class UserApi {
    // JSON body - 单参数自动推断
    @post("/save") save(user: User) {}
    
    // JSON body - 显式指定
    @post("/save", "user") @json save(user, token) {}
    
    // JSON body - 快捷方式(推荐)
    @postJson("/save") save(user: User) {}
    @postJson("/save", "user") save(user, token) {}
    
    // Form body - 全部参数转 key=value
    @post("/save") @form save(name, age) {}
    
    // Form body - 指定参数
    @post("/update", "name,email") @form save(name, email, logId) {}
    
    // Form body - 快捷方式(推荐)
    @postForm("/save") save(name, age) {}
    @postForm("/update", "name,email") save(name, email, logId) {}
    
    // 参数值为对象时自动 JSON.stringify
    @postForm("/config") save(config: object) {}  // config={"key":"val"}
    
    // GET请求
    @get("/list") list(page, size) {}
    
    // 路径参数
    @delete("/{id}") remove(id: number) {}
}
```

## 优先级
方法装饰器(`@json/@form`) > 全局配置(`http-aop.body-type`)

## 路径拼接
- `@api("/api/user")` + `@post("/save")` → `/api/user/save`
- 支持绝对路径跨域: `@api("http://other.com/api")`
