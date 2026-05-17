<template>
    <div class="api-demo">
        <el-card>
            <template #header>
                <span>HTTP AOP 装饰器测试</span>
            </template>
            
            <el-tabs v-model="activeTab">
                <el-tab-pane label="JSON Body" name="json">
                    <el-form label-width="120px">
                        <el-form-item label="用户ID">
                            <el-input v-model="jsonForm.id" type="number" />
                        </el-form-item>
                        <el-form-item label="用户名">
                            <el-input v-model="jsonForm.name" />
                        </el-form-item>
                        <el-form-item label="邮箱">
                            <el-input v-model="jsonForm.email" />
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" @click="testJsonSave">@post 单参数</el-button>
                            <el-button type="primary" @click="testJsonUpdate">@post + @json</el-button>
                        </el-form-item>
                    </el-form>
                </el-tab-pane>
                
                <el-tab-pane label="Form Body" name="form">
                    <el-form label-width="120px">
                        <el-form-item label="姓名">
                            <el-input v-model="formData.name" />
                        </el-form-item>
                        <el-form-item label="年龄">
                            <el-input v-model="formData.age" type="number" />
                        </el-form-item>
                        <el-form-item label="邮箱">
                            <el-input v-model="formData.email" />
                        </el-form-item>
                        <el-form-item>
                            <el-button type="success" @click="testFormSubmit">@post @form 全量</el-button>
                        </el-form-item>
                    </el-form>
                </el-tab-pane>
                
                <el-tab-pane label="GET 请求" name="get">
                    <el-form label-width="120px">
                        <el-form-item label="页码">
                            <el-input v-model.number="queryForm.page" type="number" />
                        </el-form-item>
                        <el-form-item label="每页条数">
                            <el-input v-model.number="queryForm.size" type="number" />
                        </el-form-item>
                        <el-form-item>
                            <el-button type="warning" @click="testGetList">@get 查询列表</el-button>
                        </el-form-item>
                    </el-form>
                </el-tab-pane>
            </el-tabs>
            
            <el-divider>请求日志</el-divider>
            <el-input type="textarea" v-model="log" :rows="10" readonly />
        </el-card>
    </div>
</template>

<script>
import { ref, reactive } from 'vue';
// 导入 TypeScript 转换后的 API（使用绝对路径确保被 Filter 拦截）
import { demoApi } from '/api-demo.ts';

export default {
    name: 'ApiDemo',
    setup() {
        const activeTab = ref('json');
        const log = ref('=== HTTP AOP 测试日志 ===\n\n');
        
        const jsonForm = reactive({ id: 1, name: '张三', email: 'zhangsan@example.com' });
        const formData = reactive({ name: '李四', age: 25, email: 'lisi@example.com' });
        const queryForm = reactive({ page: 1, size: 10 });
        
        function appendLog(msg) {
            const timestamp = new Date().toLocaleTimeString();
            log.value += `[${timestamp}] ${msg}\n`;
        }
        
        async function testJsonSave() {
            appendLog('测试 @post 单参数自动推断 JSON');
            appendLog('请求: POST /api/demo/save');
            appendLog('Body: ' + JSON.stringify(jsonForm));
            try {
                const result = await demoApi.saveUser(jsonForm);
                appendLog('响应: ' + JSON.stringify(result));
            } catch (e) {
                appendLog('错误: ' + e.message);
            }
        }
        
        async function testJsonUpdate() {
            appendLog('测试 @post + @json 显式指定 body');
            appendLog('请求: POST /api/demo/update?token=test123');
            appendLog('Body: ' + JSON.stringify(jsonForm));
            try {
                const result = await demoApi.updateUser(jsonForm, 'test123');
                appendLog('响应: ' + JSON.stringify(result));
            } catch (e) {
                appendLog('错误: ' + e.message);
            }
        }
        
        async function testFormSubmit() {
            appendLog('测试 @post @form 全量打平');
            appendLog('请求: POST /api/demo/submit');
            appendLog('Form: name=' + formData.name + '&age=' + formData.age + '&email=' + formData.email);
            try {
                const result = await demoApi.submitForm(formData.name, formData.age, formData.email);
                appendLog('响应: ' + JSON.stringify(result));
            } catch (e) {
                appendLog('错误: ' + e.message);
            }
        }
        
        async function testGetList() {
            appendLog('测试 @get 查询列表');
            appendLog('请求: GET /api/demo/list?page=' + queryForm.page + '&size=' + queryForm.size);
            try {
                const result = await demoApi.getList(queryForm.page, queryForm.size);
                appendLog('响应: ' + JSON.stringify(result));
            } catch (e) {
                appendLog('错误: ' + e.message);
            }
        }
        
        return {
            activeTab,
            log,
            jsonForm,
            formData,
            queryForm,
            testJsonSave,
            testJsonUpdate,
            testFormSubmit,
            testGetList
        };
    }
};
</script>

<style scoped>
.api-demo {
    padding: 20px;
}
</style>
