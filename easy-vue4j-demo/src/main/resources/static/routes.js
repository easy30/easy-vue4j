// 路由配置文件
const routes = [
    {
        path: '/log-level',
        name: 'SetLogLevel',
        component: () => import('./setLogLevel.vue'),
        meta: { title: '日志级别设置' }
    },
    {
        path: '/system-monitor',
        name: 'SystemMonitor',
        component: () => import('./systemMonitor.vue'),
        meta: { title: '系统监控' }
    },
    {
        path: '/log-query',
        name: 'LogQuery',
        component: () => import('./logQuery.vue'),
        meta: { title: '日志查询' }
    },
    {
        path: '/appender-config',
        name: 'AppenderConfig',
        component: () => import('./appenderConfig.vue'),
        meta: { title: 'Appender 配置' }
    },
    {
        path: '/',
        redirect: '/log-level'
    }
];

export default routes;
