<template>
    <el-card shadow="hover">
        <template #header>
            <h3 style="margin: 0;">🔍 日志查询</h3>
        </template>
        <el-form :inline="true" :model="queryForm" class="demo-form-inline">
            <el-form-item label="日志级别">
                <el-select v-model="queryForm.level" placeholder="选择级别">
                    <el-option label="DEBUG" value="DEBUG"></el-option>
                    <el-option label="INFO" value="INFO"></el-option>
                    <el-option label="WARN" value="WARN"></el-option>
                    <el-option label="ERROR" value="ERROR"></el-option>
                </el-select>
            </el-form-item>
            <el-form-item label="关键字">
                <el-input v-model="queryForm.keyword" placeholder="搜索关键字"></el-input>
            </el-form-item>
            <el-form-item>
                <el-button type="primary" @click="onSearch">
                    <i class="fas fa-search"></i> 查询
                </el-button>
            </el-form-item>
        </el-form>
        <el-table :data="tableData" border style="width: 100%; margin-top: 20px;">
            <el-table-column prop="timestamp" label="时间" width="180"></el-table-column>
            <el-table-column prop="level" label="级别" width="80">
                <template #default="scope">
                    <el-tag :type="getLevelType(scope.row.level)">{{ scope.row.level }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="message" label="消息"></el-table-column>
        </el-table>
    </el-card>
</template>

<script>
export default {
    name: 'LogQuery',
    data() {
        return {
            queryForm: {
                level: '',
                keyword: ''
            },
            tableData: []
        }
    },
    methods: {
        onSearch() {
            ElementPlus.ElMessage.info('查询功能开发中...');
        },
        getLevelType(level) {
            const typeMap = {
                'DEBUG': 'info',
                'INFO': 'success',
                'WARN': 'warning',
                'ERROR': 'danger'
            };
            return typeMap[level] || 'info';
        }
    }
}
</script>
