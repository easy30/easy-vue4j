/**
 * easy-vue4j API AOP 装饰器核心
 *
 * 装饰器语法：
 * - @api("/api/prefix")          类级路径前缀
 * - @get(path) / @post(path, bodyNames?) / @put(path) / @delete(path)  方法装饰器
 * - @json                         JSON body 格式（不带参数）
 * - @form                         Form body 格式（不带参数）
 * - @postJson(path, bodyNames?)   POST + JSON body 快捷方式
 * - @postForm(path, bodyNames?)   POST + Form body 快捷方式
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
 * 序列化参数为键值对（用于 form body 和 query）
 *
 * 规则：
 *   基本类型 → key=value
 *   对象     → 展开第一级，子值递归本规则
 *   数组     → 按元素展开（同名重复 key）
 *   子对象   → JSON.stringify
 */
function serializeParams(args, names) {
    const ret = {};
    for (let i = 0; i < args.length; i++) {
        const name = names[i];
        if (!name && typeof args[i] === 'object' && !Array.isArray(args[i])) {
            // 无名对象参数 → 展开第一级
            for (const key in args[i]) addValue(ret, key, args[i][key]);
        } else if (name) {
            addValue(ret, name, args[i]);
        }
    }
    return ret;
}

function addValue(ret, key, val) {
    if (Array.isArray(val)) {
        // 数组 → 按元素展开（递归）
        for (const item of val) addValue(ret, key, item);
    } else if (val !== null && typeof val === 'object') {
        // 子对象 → JSON.stringify
        doAdd(ret, key, JSON.stringify(val));
    } else {
        if (val != null) doAdd(ret, key, String(val));
    }
}

function doAdd(ret, key, val) {
    if (ret.hasOwnProperty(key)) {
        if (Array.isArray(ret[key])) ret[key].push(val);
        else ret[key] = [ret[key], val];
    } else {
        ret[key] = val;
    }
}

/**
 * 替换 URL 中的 {param} 为参数值，返回 [新URL, 已消费参数索引数组]
 */
function resolvePathParams(url, args, argNames) {
    const match = url.match(/\{(\w+)\}/g);
    if (!match) return [url, []];
    let resolved = url;
    const consumed = [];
    for (const m of match) {
        const key = m.slice(1, -1);
        const idx = argNames.indexOf(key);
        if (idx >= 0) {
            resolved = resolved.replace(m, encodeURIComponent(String(args[idx])));
            consumed.push(idx);
        }
    }
    return [resolved, consumed];
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
                // 新格式下 target 已经是方法函数自身，target[methodName] 是 undefined
                descriptor = { value: target, writable: true, enumerable: false, configurable: true };
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
                const [resolvedUrl, consumed] = resolvePathParams(fullUrl, args, argNames);
                const fNames = argNames.filter((_, i) => !consumed.includes(i));
                const fArgs = args.filter((_, i) => !consumed.includes(i));

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

                // 用 fNames/fArgs（已排除路径参数）处理 body/query
                const useNames = fNames.length > 0 ? fNames : argNames;
                const useArgs = fNames.length > 0 ? fArgs : args;

                if (isJson) {
                    if (config.bodyNames) {
                        const bodyIdx = getBodyParamIndex(useNames, config.bodyNames);
                        if (bodyIdx >= 0) {
                            data = useArgs[bodyIdx];
                            const qn = useNames.filter((_, i) => i !== bodyIdx);
                            const qa = useArgs.filter((_, i) => i !== bodyIdx);
                            if (qa.length > 0) params = serializeParams(qa, qn);
                        } else { data = useArgs[0]; }
                    } else {
                        data = useArgs.length > 0 ? useArgs[0] : null;
                    }
                } else {
                    isJson = false;
                    if (config.bodyNames) {
                        const bodyIdx = getBodyParamIndex(useNames, config.bodyNames);
                        const qn = [], qa = [];
                        for (let i = 0; i < useArgs.length; i++) {
                            if (i === bodyIdx) {
                                const wrap = {}; wrap[useNames[i]] = useArgs[i];
                                data = serializeParams([wrap], ['']);
                            } else { qn.push(useNames[i]); qa.push(useArgs[i]); }
                        }
                        if (qa.length > 0) params = serializeParams(qa, qn);
                    } else {
                        data = serializeParams(useArgs, useNames);
                    }
                }

                return _sendRequest({
                    url: resolvedUrl || fullUrl,
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
 * @postJson 快捷装饰器 - POST + JSON body
 * 等价于 @post(...) + @json
 */
export function postJson(path, bodyNames) {
    return function(target, name, descriptor) {
        // 先设置 json 标记
        const ctx = descriptor === undefined ? name : null;
        const methodName = ctx ? ctx.name : name;
        _typeFlagMap.set(methodName, 'json');
        
        // 再应用 @post 装饰器
        const postDecorator = createMethodDecorator('post')(path, bodyNames);
        return postDecorator(target, name, descriptor);
    };
}

/**
 * @postForm 快捷装饰器 - POST + Form body
 * 等价于 @post(...) + @form
 */
export function postForm(path, bodyNames) {
    return function(target, name, descriptor) {
        // 先设置 form 标记
        const ctx = descriptor === undefined ? name : null;
        const methodName = ctx ? ctx.name : name;
        _typeFlagMap.set(methodName, 'form');
        
        // 再应用 @post 装饰器
        const postDecorator = createMethodDecorator('post')(path, bodyNames);
        return postDecorator(target, name, descriptor);
    };
}

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
