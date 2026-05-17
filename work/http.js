import axios from '/js/lib/axios.min.js'
import qs from '/js/lib/qs.min.js'
import {HttpApi,execute} from 'httpApiModule'
//这个包要引入,否则async/await是会报: Uncaught (in promise) ReferenceError: regeneratorRuntime is not defined
import runtime from '/js/lib/runtime.js'

let _errorHandler=null;
let _pathPrefix=null;
export function setRequestErrorHandler(errorHandler){
    _errorHandler = errorHandler;
}

export function setPathPrefix(pathPrefix){
    _pathPrefix=pathPrefix;
}

/*let httpApi =new HttpApi();
export function api(url){ return  httpApi.api(url);}
export function post(url){ return  httpApi.post(url);}
export function get(url){ return  httpApi.get(url);}
export function body(){ return  httpApi.body();}*/
function exe(config) {
    if (config.method == 'post' && !config.isJson && config.data != null) {
        config.data = qs.stringify(config.data);
    }
    //if(config.params!=null) config.params=qs.stringify(config.params ,{arrayFormat: 'repeat'});
    let url: string = config.url;
    if (!url.toLowerCase().startsWith("http:")) url = _pathPrefix + config.url;
    return request({
        url: url,
        method: config.method,
        params: config.params,
        data: config.data,
        headers: {
            //"token": localStorage.getItem("token")
            //'Content-Type': 'application/json; charset=utf-8',
        }
    });
}

execute(exe);
//httpApi.execute( exe);



export function request(config) {
    //1.创建axios实例
    // @ts-ignore
    const instance = axios.create({
        //baseURL: 'http://152.136.185.210:7878/api/hy66',
        timeout: 60000
    })

    //2.axios拦截器的使用
    //请求拦截器
    /*需要拦截请求的原因
    *   1.config中包含了某些不符合服务器要求的信息
    *   2.发送网络请求的时候需要向用户展示一些加载中的图标
    *   3.网站需要登录才能请求资源，也就是需要token才能请求资源*/
    instance.interceptors.request.use(config => {
        console.log(config)
        return config//拦截器里一定要记得将拦截的结果处理后返回，否则无法进行数据获取
    }, err => {
        throw new Error(err);
    })
    //响应拦截器
    instance.interceptors.response.use(
        res => {

            let result= res.data;
            if(result.code!=null){
                if(result.code==0){
                    return  result.data;
                }else{
                    let err=new Error(result.msg);
                    err.code=result.code;
                    if (_errorHandler != null) _errorHandler(err);
                    return Promise.reject(err) ;
                }
            } else return result;
        },
        err => {
            //err.code=500;
            if (_errorHandler != null) _errorHandler(err);
            //抛出异常中断代码执行,如果要继续执行,需要try catch
            return Promise.reject(err);

        })

    //3.发送网络请求
    //axios实例本身返回的就是Promise对象，直接调用即可
    return instance(config)
}