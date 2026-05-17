/**
 * easy-vue4j HTTP AOP axios 适配器
 * 
 * 负责实际发起 HTTP 请求
 */

import axios from '/core-js/axios.min.js';
import qs from '/core-js/qs.min.js';
import { setSendRequest } from './http-aop.js';

// 配置
let _errorHandler = null;
let _pathPrefix = '';
let _defaultBodyType = 'json'; // 全局默认 body 类型

/**
 * 设置请求错误处理函数
 */
export function setErrorHandler(handler) {
    _errorHandler = handler;
}

/**
 * 设置路径前缀
 */
export function setPathPrefix(prefix) {
    _pathPrefix = prefix;
}

/**
 * 设置默认 body 类型
 */
export function setDefaultBodyType(type) {
    _defaultBodyType = type;
}

/**
 * 实际发送请求的函数
 */
function sendRequest(config) {
    let url = config.url;
    
    // 处理 URL
    if (!url.toLowerCase().startsWith('http:') && !url.toLowerCase().startsWith('https://')) {
        url = _pathPrefix + url;
    }
    
    // Form body 需要 qs.stringify
    let data = config.data;
    if (config.method !== 'get' && !config.isJson && data != null) {
        data = qs.stringify(data);
    }
    
    return request({
        url: url,
        method: config.method,
        params: config.params,
        data: data
    });
}

// 注册发送函数到 http-aop
setSendRequest(sendRequest);

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

// 导出 axios 实例供直接使用
export { request, instance as axiosInstance };
