/**
 * easy-vue4j API AOP axios 适配器
 *
 * 前端统一通过此模块导入：
 *
 *   import { setup, api } from 'api-aop-axios';
 *
 *   setup({ baseURL: '/api' });
 *   await api.post('/user/list', { name: 'xxx' });
 *   await api.req({ url: '/xxx', method: 'post', ... });
 */

import axios from 'axios';
import { setRequestHandler, setDefaultBodyType } from './api-aop.js';

// 配置
let _errorHandler = null;
let _pathPrefix = '';
let _defaultHeaders = () => ({});

/**
 * 统一初始化配置（推荐方式）
 *
 * @param {Object} options
 * @param {string} [options.baseURL]        API 路径前缀，如 '/api'
 * @param {string} [options.defaultBodyType] 默认 body 类型 'json' | 'form'
 * @param {Function} [options.defaultHeaders] 返回默认请求头对象的函数
 * @param {Function} [options.onError]      全局错误处理函数
 * @param {number}  [options.timeout]       请求超时时间（ms）
 *
 * 示例：
 *   setup({
 *     baseURL: '/',
 *     defaultBodyType: 'json',
 *     defaultHeaders: () => ({ cai_token: localStorage.getItem('cai_token') })
 *   })
 */
export function setup(options = {}) {
    // 注册请求发送函数到 api-aop 装饰器
    setRequestHandler(sendRequest);

    if (options.baseURL) _pathPrefix = options.baseURL;
    if (options.defaultBodyType) setDefaultBodyType(options.defaultBodyType);
    if (options.onError) _errorHandler = options.onError;
    if (options.defaultHeaders) _defaultHeaders = options.defaultHeaders;
}

/**
 * 实际发送请求的函数
 */
function sendRequest(config) {
    let url = config.url;

    // 处理 URL：拼上 _pathPrefix
    if (!url.startsWith('http://') && !url.startsWith('https://') && _pathPrefix) {
        url = _pathPrefix.replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '');
    }

    // 合并请求头：每次动态读取默认头
    const customHeaders = typeof _defaultHeaders === 'function' ? _defaultHeaders() : {};
    const headers = {
        ...customHeaders,
        ...config.headers
    };

    // Form body 序列化
    let data = config.data;
    if (config.method !== 'get' && config.type === 'form' && data != null) {
        const params = new URLSearchParams();
        for (const key in data) {
            params.append(key, data[key]);
        }
        data = params;
    }

    return request({
        url: url,
        method: config.method,
        headers: headers,
        params: config.params,
        data: data
    });
}

/**
 * axios 请求函数
 */
function request(config) {
    const instance = axios.create({
        timeout: 60000
    });

    // 请求拦截器
    instance.interceptors.request.use(config => {
        return config;
    }, err => {
        throw new Error(err);
    });

    // 响应拦截器
    instance.interceptors.response.use(
        res => {
            const result = res.data;
            // 如果返回结果有 code 字段，按业务逻辑处理
            if (result.code !== undefined) {
                if (result.code === 0 || result.code === 200) {
                    return result.data;
                } else {
                    const err = new Error(result.msg || '请求失败');
                    err.code = result.code;
                    if (_errorHandler) _errorHandler(err);
                    return Promise.reject(err);
                }
            }
            return result;
        },
        err => {
            if (_errorHandler) _errorHandler(err);
            return Promise.reject(err);
        }
    );

    return instance(config);
}

// ==================== 便捷方法 ====================

function createMethod(method) {
    return function(url, dataOrConfig, config = {}) {
        if (method === 'get' || method === 'delete') {
            // GET/DELETE: 第二个参数是 axios 配置 (params, headers 等)，不是 body
            return sendRequest({ url, method, ...dataOrConfig, ...config });
        }
        return sendRequest({ url, method, data: dataOrConfig, ...config });
    };
}

/**
 * 统一 API 调用对象
 *
 * 示例：
 *   import { api } from 'api-aop-axios';
 *
 *   await api.post('/agent/delete', null, { params: { id: 'xxx' } });
 *   await api.get('/toolDefine/list', { params: { builtin: true } });
 *   await api.req({ url: '/xxx', method: 'post', data: {...}, params: {...} });
 */
export const api = {
    post: createMethod('post'),
    get: createMethod('get'),
    put: createMethod('put'),
    delete: createMethod('delete'),
    req: sendRequest
};
