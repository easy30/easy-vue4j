/**
 * easy-vue4j API AOP axios 适配器
 *
 * 前端统一通过此模块导入：
 *
 *   import { setup } from 'api-aop-axios';
 *
 *   setup({ baseURL: '/api', headers: { Authorization: 'xxx' } });
 */

import axios from 'axios';
import { setRequestHandler, setDefaultBodyType } from './api-aop.js';

// 配置
let _errorHandler = null;
let _pathPrefix = '';
let _defaultHeaders = {};

/**
 * 统一初始化配置（推荐方式）
 *
 * @param {Object} options
 * @param {string} [options.baseURL]        API 路径前缀，如 '/api'
 * @param {string} [options.defaultBodyType] 默认 body 类型 'json' | 'form'
 * @param {Object} [options.headers]        默认请求头，如 { Authorization: 'xxx' }
 * @param {Function} [options.onError]      全局错误处理函数
 * @param {number}  [options.timeout]       请求超时时间（ms）
 *
 * 示例：
 *   setup({
 *     baseURL: '/api',
 *     defaultBodyType: 'json',
 *     headers: { Authorization: 'Bearer ' + token },
 *     onError: (err) => console.error(err)
 *   })
 */
export function setup(options = {}) {
    // 注册请求发送函数到 api-aop 装饰器
    setRequestHandler(sendRequest);

    if (options.baseURL) _pathPrefix = options.baseURL;
    if (options.defaultBodyType) setDefaultBodyType(options.defaultBodyType);
    if (options.headers) Object.assign(_defaultHeaders, options.headers);
    if (options.onError) _errorHandler = options.onError;
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

    // 合并默认请求头（方法级 > 全局）
    const headers = { ..._defaultHeaders, ...config.headers };

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

// 注：不再自动注册，由 setup() 统一初始化

/**
 * axios 请求函数
 */
function request(config) {
    const instance = axios.create({
        timeout: 60000
    });

    // 请求拦截器
    instance.interceptors.request.use(config => {
        console.log('[HTTP AOP] Request:', config);
        return config;
    }, err => {
        throw new Error(err);
    });

    // 响应拦截器
    instance.interceptors.response.use(
        res => {
            console.log('[HTTP AOP] Response:', res);
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
            console.error('[HTTP AOP] Error:', err);
            if (_errorHandler) _errorHandler(err);
            return Promise.reject(err);
        }
    );

    return instance(config);
}

// request 和 axiosInstance 不导出，由 setup() 统一初始化。
