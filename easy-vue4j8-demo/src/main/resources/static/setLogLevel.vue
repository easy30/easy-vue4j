<template>
    <div class="set-log-level-container">
        <el-form label-width="120px" size="default">
            <el-form-item label="Logger 名称">
                <el-input v-model="loggerName" placeholder="请输入 logger 名称，留空表示 root logger" clearable></el-input>
            </el-form-item>
            <el-form-item label="日志级别">
                <el-select v-model="logLevel" style="width: 100%;" placeholder="请选择日志级别">
                    <el-option value="TRACE" label="TRACE"></el-option>
                    <el-option value="DEBUG" label="DEBUG"></el-option>
                    <el-option value="INFO" label="INFO"></el-option>
                    <el-option value="WARN" label="WARN"></el-option>
                    <el-option value="ERROR" label="ERROR"></el-option>
                </el-select>
            </el-form-item>
            <el-form-item>
                <el-button type="primary" @click="setLogLevel" :loading="loading">
                    <i class="fas fa-check"></i> 设置
                </el-button>
                <el-button @click="resetForm">
                    <i class="fas fa-redo"></i> 重置
                </el-button>
            </el-form-item>
            <el-divider v-if="appenders.length > 0" content-position="left">
                <i class="fas fa-layer-group"></i> Appenders 列表
            </el-divider>
            <div v-if="appenders.length > 0">
                <el-tag
                        v-for="(appender, index) in appenders"
                        :key="index"
                        type="success"
                        style="margin: 5px;"
                        size="large"
                        effect="dark"
                        round>
                    <i class="fas fa-file"></i> {{ appender.name }}
                </el-tag>
            </div>
            <el-empty v-else-if="hasSearched" description="暂无数据" image-size="120"></el-empty>
            <el-alert
                    v-if="!hasSearched"
                    title="操作提示"
                    type="info"
                    description="请点击【设置】按钮查看当前 logger 的 appender 配置"
                    show-icon
                    closable>
            </el-alert>
        </el-form>
    </div>
</template>

<script>
import { ElMessage } from 'element-plus';
import axios from 'axios';
export default {
    name: 'SetLogLevel',
    components: {
    },
    data() {
        return {
            loggerName: localStorage.getItem('recent_logger_name') || '',
            logLevel: 'DEBUG',
            appenders: [],
            loading: false,
            hasSearched: false
        }
    },
    methods: {
        async setLogLevel() {
            this.loading = true;
            this.hasSearched = false;
            try {
                const { data } = await axios.get('setLogLevel', {
                    params: { name: this.loggerName, level: this.logLevel }
                });
                this.appenders = data?.data?.appenders ?? [];
                this.hasSearched = true;
                if (this.loggerName) {
                    localStorage.setItem('recent_logger_name', this.loggerName);
                }
                if (this.appenders.length === 0) {
                    ElMessage.info('未找到 appender');
                } else {
                    ElMessage.success(`找到 ${this.appenders.length} 个 appender`);
                }
            } catch (error) {
                ElMessage.error('请求失败：' + error.message);
                this.appenders = [];
            } finally {
                this.loading = false;
            }
        },
        resetForm() {
            this.loggerName = '';
            this.logLevel= 'DEBUG';
            this.appenders = [];
            this.hasSearched = false;
        }
    }
}
</script>

<style scoped>
.set-log-level-container {
    padding: 20px;
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
</style>
