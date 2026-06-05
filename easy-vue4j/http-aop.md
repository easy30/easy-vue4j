# HTTP AOP 装饰器

## 装饰器总览

| 装饰器 | 说明 |
|--------|------|
| `@api("/prefix")` | 类级路径前缀 |
| `@postJson(path, bodyNames?)` | POST + JSON body（推荐） |
| `@postForm(path, bodyNames?)` | POST + Form body（推荐） |
| `@get(path)` | GET，参数自动进 query |
| `@post(path, bodyNames?)` | POST（配合 `@json`/`@form`） |
| `@json` | 标记为 JSON body |
| `@form` | 标记为 Form body |

## 数据类型序列化规则

所有参数（form body 和 query）统一走以下规则：

| 类型 | 处理 | 示例 |
|------|------|------|
| 基本类型 | `key=value` | `"abc"` → `page=abc` |
| **对象** | **展开第一级**，子值递归 | `{a:1,b:[2,3],c:{d:4}}` → `a=1&b=2&b=3&c={"d":4}` |
| **数组** | 按元素展开（同名重复 key） | `[1,2,3]` → `ids=1&ids=2&ids=3` |
| **子对象**（一级下的对象） | JSON.stringify | `{extra:{note:"x"}}` → `extra={"note":"x"}` |
| null/undefined | 空字符串 | `null` → `key=` |

## Form body（`@postForm`）

**无 bodyNames** → 全部参数进 body：

```typescript
@postForm("/save") save(name, age, filter) {}
// 传参: save('水泵', 10, { status: 1, tags: ['a','b'] })
// body: name=水泵&age=10&status=1&tags=a&tags=b
```

**有 bodyNames** → 指定参数进 body，其余进 query：

```typescript
@postForm("/search", "filter") search(page, size, filter) {}
// 传参: search(1, 20, { status: 1, tags: ['a'] })
// body: status=1&tags=a
// query: page=1&size=20
```

## JSON body（`@postJson`）

**无 bodyNames** → 第一个参数作 JSON body：

```typescript
@postJson("/save") save(user) {}
// body: {"name":"水泵","type":"设备"}
```

**有 bodyNames** → 指定参数作 JSON body，其余进 query：

```typescript
@postJson("/search", "body") search(page, size, body) {}
// body: {"keyword":"abc"}
// query: page=1&size=20
```

## GET 请求（`@get`）

所有参数进 query：

```typescript
@get("/list") list(page, size, filter) {}
// query: page=1&size=20&status=1&tags=a&tags=b
```

## 完整示例

```typescript
@api("/api/knowledge-graph")
class KnowledgeGraphApi {

    @postForm("/node/save")
    saveNode(params): KnowledgeNode {}

    @postForm("/node/delete")
    deleteNode(params): void {}

    @postForm("/subgraph")
    getSubgraph(params): GraphData {}

    @postJson("/search")
    search(params: SearchRequest): KnowledgeNode[] {}

    @postJson("/batch/import")
    batchImport(params): BatchImportResult {}
}
```

## 后端对照

```java
// @postForm → 单个参数接收
@PostMapping("/search")
public Result search(Integer page, Integer size, String status) {}

// @postJson → @RequestBody
@PostMapping("/save")
public Result save(@RequestBody User user) {}

// @postJson + bodyNames → query + @RequestBody
@PostMapping("/search")
public Result search(Integer page, Integer size, @RequestBody SearchBody body) {}
```
