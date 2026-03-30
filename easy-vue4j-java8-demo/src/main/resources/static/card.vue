<template>
    <el-card :shadow="shadow">
        <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <h3 style="margin: 0;">
                    <i v-if="icon" :class="icon" style="margin-right: 8px;"></i>
                    {{ title }}
                </h3>
                <slot name="header-actions"></slot>
            </div>
        </template>
        <slot></slot>
    </el-card>
</template>

<script>
export default {
    name: 'BaseCard',
    props: {
        title: {
            type: String,
            required: true
        },
        icon: {
            type: String,
            default: ''
        },
        shadow: {
            type: String,
            default: 'hover'
        }
    }
}
</script>
