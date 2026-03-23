<template>
    <base-card title="系统监控5" icon="fas fa-desktop">
        <el-row :gutter="20">
            <el-col :span="12">
                <el-statistic title="CPU 使用率" :value="cpuUsage" suffix="%">
                    <template #prefix>
                        <i class="fas fa-microchip"></i>
                    </template>
                </el-statistic>
                <el-progress :percentage="cpuUsage" :stroke-width="10"></el-progress>
            </el-col>
            <el-col :span="12">
                <el-statistic title="内存使用率" :value="memoryUsage" suffix="%">
                    <template #prefix>
                        <i class="fas fa-memory"></i>
                    </template>
                </el-statistic>
                <el-progress :percentage="memoryUsage" :stroke-width="10"></el-progress>
            </el-col>
        </el-row>
        <el-divider></el-divider>
        <el-empty description="更多监控指标开发中..." image-size="200"></el-empty>
    </base-card>
</template>

<script>
import BaseCard from './card.vue';

export default {
    name: 'SystemMonitor',
    components: {
        BaseCard
    },
    data() {
        return {
            cpuUsage: 45,
            memoryUsage: 62
        }
    }
}
</script>
