<template>
  <div :class="$style.container">
    <el-card>
      <template #header>
        <span>Script Setup 自动暴露测试（无 defineExpose）</span>
      </template>

      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="ref(count)">{{ count }}</el-descriptions-item>
        <el-descriptions-item label="reactive(user)">{{ JSON.stringify(user) }}</el-descriptions-item>
        <el-descriptions-item label="computed(double)">{{ double }}</el-descriptions-item>
        <el-descriptions-item label="computed(greeting)">{{ greeting }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-space>
        <el-button type="primary" @click="increment">+1</el-button>
        <el-button @click="reset">重置</el-button>
        <el-tag>{{ statusText }}</el-tag>
      </el-space>

      <el-divider content-position="left">数组解构测试</el-divider>
      <el-tag v-for="item in list" :key="item" style="margin-right:6px">{{ item }}</el-tag>

      <el-divider content-position="left">对象解构测试</el-divider>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="解构 name">{{ userName }}</el-descriptions-item>
        <el-descriptions-item label="解构 age">{{ userAge }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'

// ref
const count = ref(0)

// reactive
const user = reactive({ name: '张三', age: 28 })

// computed
const double = computed(() => count.value * 2)
const greeting = computed(() => `你好, ${user.name}!`)

// function 声明
function increment() {
  count.value++
}

async function reset() {
  count.value = 0
  user.name = '张三'
  user.age = 28
  ElMessage.success('已重置')
}

// 箭头函数赋给 const
const getStatus = (val) => val > 0 ? '正数' : val < 0 ? '负数' : '零'

// computed 依赖箭头函数
const statusText = computed(() => getStatus(count.value))

// 数组解构
const [a, b, c] = [10, 20, 30]
const list = [a, b, c, count.value]

// 对象解构
const { name: userName, age: userAge } = user

// 可选链和空值合并（测试 Rhino 1.7.15.1 支持）
const optional = ref(null)
const safeValue = optional?.value?.xxx ?? 'fallback'
</script>

<style module>
.container {
  padding: 16px;
}
</style>
