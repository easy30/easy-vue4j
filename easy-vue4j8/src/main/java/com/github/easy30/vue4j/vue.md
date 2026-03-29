https://cn.vuejs.org/guide/essentials/component-basics

```text

- 对于<script>的处理, 有 <script>   <script setup> 两种, 
- 先用jsoup遍历<script> , 取出第一个<script> 和  <script setup> ,没有则null.
- 如果有<script>则按照原有java逻辑处理<script>, 如果没有<script>则生成代码: export default { }
- 如果没有 <script setup> 则忽略;如果有 <script setup> 则参考template属性注入的办法, 在已有
export default {... } 中 增加  setup() 函数,把 <script setup> 中的代码处理(看下面)后放入到  setup() 函数中.
- <script setup>里面代码的处理方式: 
   把所有import 语句提出到export default 外部,例如可以直接放入提取出的代码最前面; 
   把defineExpose({count, increment}) 转为  return { count, increment }.
   
```

包含setup模块假设我们有一个 .vue 文件：
```vue
<script setup>
import { ref } from 'vue';
const count = ref(0);
function increment() { count.value++; }
</script>

<template>
  <button @click="increment">{{ count }}</button>
</template>
```
转成
```js
import { ref } from 'vue';
export default {
  setup() {
    const count = ref(0);
    function increment() { count.value++; }
    return { count, increment };
  },
  template: `<button @click="increment">{{ count }}</button>`
};

```

# <style scoped> 处理流程

输入：CSS 内容 + 组件 ID (基于文件路径 hash)
输出：添加了作用域属性的 CSS
步骤：
1. 解析 CSS 为 AST (使用 ph-css)
2. 遍历所有 CSSStyleRule
3. 对于每个选择器：
   a. 获取最后一个 Member (选择器片段)
   b. 在末尾追加 [data-v-{hash}]
   c. 更新选择器
4. 输出修改后的 CSS


输入：HTML 模板 + 组件 ID
输出：添加了 data-v-{hash} 属性的 HTML
步骤：
1. 解析 HTML (使用 jsoup)
2. 遍历所有元素
3. 为每个元素添加 data-v-{hash} 属性
4. 输出修改后的 HTML


```css
/* 原始 */
.button { color: red; }
.card .title:hover { font-size: 16px; }

/* 转换后 (scoped) */
.button[data-v-2a8f3b1c] { color: red; }
.card .title:hover[data-v-2a8f3b1c] { font-size: 16px; }

```

```html
<!-- 原始 -->
<button class="button">点击</button>
<div class="card">
  <h3 class="title">标题</h3>
</div>

<!-- 转换后 (scoped) -->
<button class="button" data-v-2a8f3b1c>点击</button>
<div class="card" data-v-2a8f3b1c>
  <h3 class="title" data-v-2a8f3b1c>标题</h3>
</div>

```
# <style module> 处理流程
```text
输入：CSS 内容 + 组件 ID (基于文件路径 hash)
输出：类名替换为 hash 的 CSS

步骤：
1. 解析 CSS 为 AST (使用 ph-css)
2. 遍历所有 CSSStyleRule
3. 对于每个选择器：
   a. 提取所有以 . 开头的类选择器
   b. 生成 hash 类名：.{name} → ._{name}_{hash}
   c. 替换选择器中的类名
4. 输出修改后的 CSS

输入：HTML 模板 + CSS 类名映射表
输出：替换类名的 HTML

步骤：
1. 从 <style module> 提取所有类名，建立映射表：
   { button: '_button_xxx', card: '_card_xxx' }
2. 解析 HTML (使用 jsoup)
3. 遍历所有元素的 class 属性
4. 对于每个 class 值：
   a. 分割为多个类名（支持空格分隔）
   b. 如果在映射表中，替换为 hash 类名
   c. 如果不在映射表中，保持不变
5. 输出修改后的 HTML

JS 导出算法
输入：CSS 类名映射表
输出：包含 $style 对象的组件代码

步骤：
1. 在组件中添加 data 或 computed 属性
2. 创建 $style 对象，键为原始类名，值为 hash 类名
3. 插入到组件定义中

```
```css
/* 原始 */
.button { color: red; }
.card .title { font-size: 16px; }
.btn-primary { background: blue; }

/* 转换后 (module) */
._button_2a8f3b1c { color: red; }
._card_2a8f3b1c ._title_2a8f3b1c { font-size: 16px; }
._btn-primary_2a8f3b1c { background: blue; }

```

```html
<!-- 原始 -->
<button class="button primary large">按钮</button>
<div class="card">
  <h3 class="title">标题</h3>
</div>

<!-- 转换后 (module) -->
<button class="_button_xxx _primary_xxx large">按钮</button>
<div class="_card_xxx">
  <h3 class="_title_xxx">标题</h3>
</div>

```

```javascript
export default {
  data() {
    return {
      $style: {
        button: '_button_2a8f3b1c',
        primary: '_primary_2a8f3b1c',
        card: '_card_2a8f3b1c',
        title: '_title_2a8f3b1c'
      }
    }
  }
}

```


```text 
Vue 单文件组件
    ↓
┌─────────────────────────────────┐
│ 1. 解析 Vue 文件                 │
│    - template                   │
│    - script                     │
│    - style (可能有多个)         │
└─────────────────────────────────┘
    ↓
┌─────────────────────────────────┐
│ 2. 生成组件唯一 ID               │
│    基于文件路径 hash             │
│    data-v-2a8f3b1c              │
└─────────────────────────────────┘
    ↓
┌─────────────────────────────────┐
│ 3. 处理每个 <style>              │
│                                 │
│ ├─ 如果是 scoped:               │
│ │   • CSS: 添加 [data-v-xxx]    │
│ │   • Template: 添加属性        │
│ │                               │
│ ├─ 如果是 module:               │
│ │   • CSS: 替换类名为 hash      │
│ │   • Template: 替换 class      │
│ │   • JS: 导出 $style 对象      │
│ │                               │
│ └─ 如果两者都有:                │
│     • 各自独立处理              │
│     • 结果叠加                  │
└─────────────────────────────────┘
    ↓
┌─────────────────────────────────┐
│ 4. 合并输出                     │
│    - CSS (styles 字段)          │
│    - Template (template 字段)   │
│    - Script (含 $style 导出)    │
└─────────────────────────────────┘
    ↓
最终 JavaScript 文件


```