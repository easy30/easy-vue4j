<template>
    <el-card shadow="hover">
        <template #header>
            <h3 style="margin: 0;">⚙️ Appender 配置</h3>
        </template>
        <el-table :data="appenderList" border style="width: 100%;">
            <el-table-column prop="name" label="名称" width="200"></el-table-column>
            <el-table-column prop="type" label="类型" width="150">
                <template #default="scope">
                    <el-tag>{{ scope.row.type }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="layout" label="布局"></el-table-column>
            <el-table-column label="操作" width="150">
                <template #default="scope">
                    <el-button size="small" @click="editAppender(scope.row)">
                        <i class="fas fa-edit"></i> 编辑
                    </el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-empty v-if="appenderList.length === 0" description="暂无配置" image-size="150"></el-empty>
    </el-card>
</template>

<script>
export default {
    name: 'AppenderConfig',
    data() {
        return {
            appenderList: [
                { name: 'Console', type: 'ConsoleAppender', layout: '%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n' },
                { name: 'File', type: 'RollingFileAppender', layout: '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n' }
            ]
        }
    },
    methods: {
        editAppender(appender) {
            ElementPlus.ElMessage.info(`编辑 Appender: ${appender.name}`);
        }
    }
}
</script>
