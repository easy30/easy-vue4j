/**
 * HTTP AOP 装饰器纯 JS 测试
 * 
 * 在浏览器控制台直接执行此代码测试装饰器功能
 */

// ==================== 模拟工具 ====================

// 存储装饰器配置
const _apiPrefixMap = new Map();
const _methodConfigMap = new Map();

// 模拟 axios
const mockAxios = {
    request: function(config) {
        console.log('[Mock Request]', {
            url: config.url,
            method: config.method,
            params: config.params,
            data: config.data,
            headers: config.headers
        });
        return Promise.resolve({ data: { code: 0, data: 'success' } });
    }
};

// ==================== 装饰器实现 ====================

function api(prefix) {
    return function(target) {
        const name = target.name || target.constructor?.name;
        _apiPrefixMap.set(name, prefix);
        return target;
    };
}

function getArgumentNames(fn) {
    if (typeof fn !== 'function') return [];
    const code = fn.toString()
        .replace(/((\/\/.*$)|(\/\*[\s\S]*?\*\/))/mg, '')
        .replace(/=>.*$/mg, '')
        .replace(/=[^,)]+/mg, '');
    const result = code.slice(code.indexOf('(') + 1, code.indexOf(')')).match(/([^\s,]+)/g);
    return result || [];
}

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

function getFullUrl(clazz, methodPath) {
    const name = clazz.name || clazz.constructor?.name;
    const prefix = _apiPrefixMap.get(name) || '';
    if (methodPath.startsWith('http')) return methodPath;
    const base = prefix.endsWith('/') ? prefix.slice(0, -1) : prefix;
    const part = methodPath.startsWith('/') ? methodPath : '/' + methodPath;
    return base + part;
}

function createMethodDecorator(method) {
    return function(path, bodyNames) {
        return function(target, name, descriptor) {
            const func = descriptor.value;
            const clazz = target.constructor;
            const key = `${clazz.name}.${name}`;
            
            _methodConfigMap.set(key, { method, path, bodyNames: bodyNames || null });
            
            descriptor.value = function(...args) {
                const argNames = getArgumentNames(func);
                const config = _methodConfigMap.get(key);
                const fullUrl = getFullUrl(clazz, config.path);
                const hasJson = this.__decoratorFlags?.[key]?.json;
                const hasForm = this.__decoratorFlags?.[key]?.form;
                
                if (hasJson || (!hasForm && method !== 'get')) {
                    // JSON 模式
                    const bodyNamesList = config.bodyNames?.split(',').map(n => n.trim()) || [];
                    let bodyIndex = bodyNamesList.length > 0 
                        ? argNames.findIndex(a => bodyNamesList.includes(a))
                        : (argNames.length > 0 ? 0 : -1);
                    
                    const data = bodyIndex >= 0 ? args[bodyIndex] : null;
                    const params = {};
                    args.forEach((arg, i) => {
                        if (i !== bodyIndex) {
                            if (typeof arg === 'object') Object.assign(params, arg);
                            else if (argNames[i]) params[argNames[i]] = arg;
                        }
                    });
                    
                    return mockAxios.request({ url: fullUrl, method, data, params });
                } else {
                    // Form 模式
                    let data = null;
                    if (config.bodyNames) {
                        const bodyNamesList = config.bodyNames.split(',').map(n => n.trim());
                        const bodyArg = args[argNames.findIndex(a => bodyNamesList.includes(a))];
                        data = typeof bodyArg === 'object' ? flattenObject(bodyArg) : { [argNames[0]]: bodyArg };
                    } else {
                        data = flattenObject(Object.fromEntries(argNames.map((n, i) => [n, args[i]])));
                    }
                    return mockAxios.request({ url: fullUrl, method, data });
                }
            };
            return descriptor;
        };
    };
}

const post = function(path, bodyNames) { return createMethodDecorator('post')(path, bodyNames); };
const get = createMethodDecorator('get');

function json(target, name, descriptor) {
    const key = `${target.constructor.name}.${name}`;
    this.__decoratorFlags = this.__decoratorFlags || {};
    this.__decoratorFlags[key] = { ...this.__decoratorFlags[key], json: true };
    return descriptor;
}

function form(target, name, descriptor) {
    const key = `${target.constructor.name}.${name}`;
    this.__decoratorFlags = this.__decoratorFlags || {};
    this.__decoratorFlags[key] = { ...this.__decoratorFlags[key], form: true };
    return descriptor;
}

// ==================== 测试用例 ====================

// 装饰器类
class UserApi {
    constructor() {
        this.__decoratorFlags = {};
    }
    
    // @api("/api/user")
    // @post("/save")  单参数自动推断为 JSON body
    @post("/save")
    save(user) {}
    
    // @api("/api/user")
    // @post("/save2", "user") @json
    @post("/save2", "user")
    @json
    save2(user, token) {}
    
    // @api("/api/user")
    // @post("/update") @form  全量打平
    @post("/update")
    @form
    update(name, age, email) {}
    
    // @api("/api/user")
    // @post("/update2", "name,email") @form  指定参数
    @post("/update2", "name,email")
    @form
    update2(name, email, logId) {}
    
    // @api("/api/user")
    // @get("/list")
    @get("/list")
    list(page, size) {}
}

// ==================== 运行测试 ====================

console.log('=== HTTP AOP 装饰器测试 ===\n');

const userApi = new UserApi();

console.log('\n【测试1】@post 单参数自动推断 JSON');
userApi.save({ id: 1, name: '张三', email: 'zhangsan@example.com' });

console.log('\n【测试2】@post("/save2", "user") @json');
userApi.save2({ id: 1, name: '李四' }, 'token123');

console.log('\n【测试3】@post @form 全量打平');
userApi.update('王五', 30, 'wangwu@example.com');

console.log('\n【测试4】@post("name,email") @form 指定参数');
userApi.update2('赵六', 'zhaoliu@example.com', 999);

console.log('\n【测试5】@get 查询列表');
userApi.list(1, 10);

console.log('\n=== 测试完成 ===');
