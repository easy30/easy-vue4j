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
