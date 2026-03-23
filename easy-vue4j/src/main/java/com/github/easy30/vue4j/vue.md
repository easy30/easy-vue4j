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
