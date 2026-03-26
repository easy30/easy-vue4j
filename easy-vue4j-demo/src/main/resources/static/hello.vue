<template>
  <div  :class="$style.container">
    <h1 :class="m1.title">{{ title }}</h1>
    <p class="description">This is scoped style.</p>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const title = ref('Hello World')
defineExpose({ title })
</script>

<style scoped>
.description {
  color: gray;
  font-size: 14px;
}
</style>

<style module>
.container {
  padding: 20px;
  background-color: #f0f0f0;
}
</style>
<style module="m1">
.title {
  color: blue;
  font-weight: bold;
}
</style>
