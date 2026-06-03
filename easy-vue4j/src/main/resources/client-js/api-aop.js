/**
 * easy-vue4j API AOP 装饰器核心
 *
 * 装饰器语法：
 * - @api("/api/prefix")          类级路径前缀
 * - @get(path) / @post(path, bodyNames?) / @put(path) / @delete(path)  方法装饰器
 * - @json                         JSON body 格式（不带参数）
 * - @form                         Form body 格式（不带参数）
 */

// 存储类级 api 前缀
const _apiPrefixMap = new Map();
// 全局默认 body 类型（可通过 setDefaultBodyType 修改）
let _defaultBodyType = 'json';

export function setDefaultBodyType(type) {
    _defaultBodyType = type;
}

// 存储方法级装饰器配置
const _methodConfigMap = new Map();
// 存储 @json/@form 标记（新格式下先于 createMethodDecorator 执行）
const _typeFlagMap = new Map();
// 请求发送函数（由 api-aop-axios.js 注入）
let _sendRequest = null;

/**
 * 设置请求发送函数
 */
export function setRequestHandler(fn) {
    _sendRequest = fn;
}

/**
 * 获取参数名列表
 */
function getArgumentNames(fn) {
    if (typeof fn !== 'function') return [];
    const COMMENTS = /((\/\/.*$)|(\/\*[\s\S]*?\*\/))/mg;
    const DEFAULT_PARAMS = /=[^,)]+/mg;
    const FAT_ARROWS = /=>.*$/mg;
    let code = fn.toString();
    code = code
        .replace(COMMENTS, '')
        .replace(FAT_ARROWS, '')
        .replace(DEFAULT_PARAMS, '');
    let result = code.slice(code.indexOf('(') + 1, code.indexOf(')')).match(/([^\s,]+)/g);
    return result === null ? [] : result;
}

/**
 * 将参数数组转换为对象
 */
function convertParams(args, names) {
    const ret = {};
    for (let i = 0; i < args.length; i++) {
        if (args[i] !== null && typeof args[i] === 'object' && !Array.isArray(args[i])) {
            Object.assign(ret, args[i]);
        } else if (names[i]) {
            ret[names[i]] = args[i];
        }
    }
    return ret;
}

/**
 * 递归打平对象属性
 */
function flattenObject(obj, prefix = '', result = {}) {
    for (const key in obj) {
        const value = obj[key];
        const newKey = prefix ? `${prefix}.${key}` : key;
        if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
            flattenObject(value, newKey, result);
        } else {
            result[newKey] = value;
        }
    }
    return result;
}

// ==================== 装饰器实现 ====================

/**
 * @api 类装饰器 - 定义路径前缀
 * 兼容旧格式（Babel）：(target) → 返回 target
 * 兼容新格式（esbuild）：(target, context) → 返回 target
 */
export function api(prefix) {
    return function(target, context) {
        const clazzName = context && context.kind ? context.name : (target.name || target.constructor?.name);
        _apiPrefixMap.set(clazzName, prefix);
        return target;
    };
}

/**
 * 获取完整 URL
 */
function getFullUrl(clazz, methodPath) {
    const clazzName = clazz.name || clazz.constructor?.name;
    const prefix = _apiPrefixMap.get(clazzName) || '';
    
    // 如果方法路径是绝对 URL，直接返回
    if (methodPath.startsWith('http://') || methodPath.startsWith('https://')) {
        return methodPath;
    }
    
    // 拼装路径：prefix + methodPath
    const baseUrl = prefix.endsWith('/') ? prefix.slice(0, -1) : prefix;
    const methodPart = methodPath.startsWith('/') ? methodPath : '/' + methodPath;
    return baseUrl + methodPart;
}

/**
 * 获取 body 参数索引
 * @param bodyNames body 参数名（逗号分隔），为空时返回第一个参数
 */
function getBodyParamIndex(argNames, bodyNames) {
    if (!bodyNames) {
        // bodyNames 为空，默认取第一个参数
        return argNames.length > 0 ? 0 : -1;
    }
    
    const names = bodyNames.split(',').map(n => n.trim());
    for (let i = 0; i < argNames.length; i++) {
        if (names.includes(argNames[i])) {
            return i;
        }
    }
    return -1;
}

/**
 * 通用方法装饰器工厂
 * 兼容旧格式（Babel）：(target, name, descriptor) → 返回 descriptor 对象
 * 兼容新格式（esbuild）：(target, context) → 返回 descriptor 对象
 */
function createMethodDecorator(method) {
    return function(path, bodyNames) {
        return function(target, name, descriptor) {
            // 兼容新旧格式：新格式无 descriptor 参数，而是 name 是 context 对象
            const ctx = descriptor === undefined ? name : null;
            const methodName = ctx ? ctx.name : name;
            if (ctx) {
                descriptor = { value: target[methodName], writable: true, enumerable: false, configurable: true };
            }
            const func = descriptor.value;
            // 新格式下 target 是函数体，class 名在调用时才知（从 this 取）
            const configKey = ctx ? '__' + methodName : target.constructor.name + '.' + name;
            // 从 _typeFlagMap 读取 @json/@form 标记
            // 新格式下 esbuild 从右往左执行，@json/@form 先于此处执行，所以标志位已就绪
            const flag = _typeFlagMap.get(methodName);
            _methodConfigMap.set(configKey, {
                method: method,
                path: path,
                bodyNames: bodyNames || null,
                json: flag === 'json' || undefined,
                form: flag === 'form' || undefined
            });

            const newFunc = function(...args) {
                const argNames = getArgumentNames(func);
                const realKey = ctx ? this.constructor.name + '.' + methodName : configKey;
                const config = _methodConfigMap.get(realKey) || _methodConfigMap.get(configKey);
                const fullUrl = getFullUrl(this.constructor, config.path);
                
                let data = null;
                let params = null;
                let isJson = false;
                
                // 检查是否有 @json/@form 装饰器标记（存于 _methodConfigMap 中）
                const hasJson = config.json;
                const hasForm = config.form;
                
                if (hasJson) {
                    // @json 显式声明
                    isJson = true;
                } else if (hasForm) {
                    // @form 显式声明
                    isJson = false;
                } else if (method === 'get') {
                    // GET 无 body
                    isJson = false;
                } else {
                    // 无显式装饰器，使用全局默认
                    isJson = _defaultBodyType === 'json';
                }

                if (isJson) {
                    // JSON body 模式：未指定 bodyNames 时，整个第一个参数作为 body
                    if (config.bodyNames) {
                        const bodyIndex = getBodyParamIndex(argNames, config.bodyNames);
                        if (bodyIndex >= 0 && bodyIndex < args.length) {
                            data = args[bodyIndex];
                        }
                        // 其他参数放入 query
                        const otherParams = {};
                        for (let i = 0; i < args.length; i++) {
                            if (i !== bodyIndex) {
                                if (typeof args[i] === 'object') {
                                    Object.assign(otherParams, args[i]);
                                } else {
                                    otherParams[argNames[i]] = args[i];
                                }
                            }
                        }
                        if (Object.keys(otherParams).length > 0) {
                            params = otherParams;
                        }
                    } else {
                        // 未指定 bodyNames，第一个参数作为整个 JSON body
                        data = args.length > 0 ? args[0] : null;
                    }
                } else {
                    // Form body 模式
                    isJson = false;

                    if (config.bodyNames) {
                        // 指定了 body 参数名
                        const bodyIndex = getBodyParamIndex(argNames, config.bodyNames);
                        if (bodyIndex >= 0 && bodyIndex < args.length) {
                            const bodyArg = args[bodyIndex];
                            if (typeof bodyArg === 'object') {
                                data = flattenObject(bodyArg);
                            } else {
                                data = { [argNames[bodyIndex]]: bodyArg };
                            }
                        }
                    } else {
                        // 未指定 bodyNames，全量打平
                        data = flattenObject(convertParams(args, argNames));
                    }
                }
                
                return _sendRequest({
                    url: fullUrl,
                    method: method,
                    type: isJson ? 'json' : 'form',
                    params: params,
                    data: data
                });
            };
            
            return ctx ? newFunc : { ...descriptor, value: newFunc };
        };
    };
}

/**
 * @get 装饰器
 */
export const get = createMethodDecorator('get');

/**
 * @post 装饰器
 * @param path 请求路径
 * @param bodyNames body 参数名（可选，逗号分隔）
 */
export function post(path, bodyNames) {
    return createMethodDecorator('post')(path, bodyNames);
}

/**
 * @put 装饰器
 */
export const put = createMethodDecorator('put');

/**
 * @delete 装饰器
 */
export const del = createMethodDecorator('delete');
// 别名
export { del as delete };

/**
 * @json 装饰器 - 标记为 JSON body
 */
/**
 * @json 装饰器 - 标记为 JSON body
 * 注意：esbuild 新格式下装饰器从右往左执行，@json 先于 @post/@get 执行，
 * 所以不能直接操作 _methodConfigMap（那时还不存在），改为存入 _typeFlagMap，
 * 由 createMethodDecorator 在创建配置时读取。
 */
export function json(target, name, descriptor) {
    const ctx = descriptor === undefined ? name : null;
    const methodName = ctx ? ctx.name : name;
    _typeFlagMap.set(methodName, 'json');
    return ctx ? target : descriptor;
}

/**
 * @form 装饰器 - 标记为 Form body
 * 同 @json，标记存入 _typeFlagMap，由 createMethodDecorator 读取。
 */
export function form(target, name, descriptor) {
    const ctx = descriptor === undefined ? name : null;
    const methodName = ctx ? ctx.name : name;
    _typeFlagMap.set(methodName, 'form');
    return ctx ? target : descriptor;
}

// ==================== 测试工具 ====================

/**
 * 打印装饰器配置（用于调试）
 */
export function printDecorators() {
    console.log('=== API 前缀 ===');
    for (const [k, v] of _apiPrefixMap) {
        console.log(`  ${k}: ${v}`);
    }
    console.log('\n=== 方法配置 ===');
    for (const [k, v] of _methodConfigMap) {
        console.log(`  ${k}: ${v.method} ${v.path} bodyNames=${v.bodyNames}`);
    }
}
