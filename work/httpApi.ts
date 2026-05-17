//这个包要引入,否则async/await是会报: Uncaught (in promise) ReferenceError: regeneratorRuntime is not defined
//import runtime from '/js/lib/runtime.js'
//console.log(runtime);

//let HttpExt= typeof window !== "undefined" && window["HttpExt"]  ? window["HttpExt"]:{ bodyParams:{}};
let bodyParams = {};
export function addBodyParam(k,v){
    bodyParams[k]=v;
}
export function parseBodyDecorator(content) {
    //解析 @body 注解

        var n = content.indexOf("@api");
        if (n == -1 || content.indexOf("@body") == -1) return content;
        var  result=content;
        content = content.substring(n);
        n = content.indexOf("class");
        if (n == -1) return;
        var n2 = content.indexOf("{");
        var clazz = content.substring(n + 5, n2).trim();
        console.log(clazz);
        //  @api  class Person {    saveIt(@body params, config,p2 ) { }  }
        let reg = /([\w\$]+)\s*\(\s*@body\s+([\w\$]+)/g;
        let res;
        while (res = reg.exec(content)) {
            bodyParams[clazz + "." + res[1] + ".body"] = res[2];
            console.log(res[1] + "," + res[2]);
        }

        return result.replaceAll("@body","");


}

export class HttpApi {

    _sendReq=null;
    setRequestMethod(sendReq) {
        this._sendReq = sendReq;
    }

    api() {
    }

    post(url) {
        // alert(url);
        const self=this;
        return function (target, name, descriptor) {
            const clazz = target.constructor.name;
            //alert("@post:" + target + "," + name + "," + descriptor);
            const func = descriptor.value;
            const key = clazz + "." + func.name + ".body";
            const bodParam = bodyParams[key];
            const newFunc = function () {
                //console.log(`Before calling: ${name}`);
                //const result = func.apply(this, arguments);
                const pnames = getArgumentNames(func);
                let bodyIndex = -1;
                if (bodParam) {
                    for (var i = 0; i < pnames.length; i++) {
                        if (pnames[i] = bodParam) {
                            bodyIndex = i;
                            break;
                        }
                    }
                }
                //@body
                let data = null;
                let params = null;
                let isJson = bodyIndex >= 0;
                if (isJson) {
                    data = arguments[bodyIndex];

                    for (let i = 0; i < arguments.length; i++) {
                        if (i !== bodyIndex) {
                            if (typeof arguments[i] == 'object') {
                                params = Object.assign(params, arguments[i]);
                            } else {
                                params[pnames[i]] = arguments[i];
                            }

                        }
                    }

                } else {
                    data = arguments.length == 0 ? null : convertParams(arguments, pnames);//qs.stringify(convertParams( arguments,pnames));
                }

                return self._sendReq({
                    url: url,
                    method: 'post',
                    isJson: isJson,
                    params: params,
                    data: data,
                    /*headers: {
                        'Content-Type': 'application/json; charset=utf-8',
                    }*/
                });

                // return "new result";
            }
            return {
                ...descriptor,
                value: newFunc
            }
        }
    }



    get(url) {
        // alert(url);
        const self=this;
        return function (target, name, descriptor) {

            //console.log(HttpExt);
            //alert(target.constructor.name)
            //const clazz = target.constructor.name;
            const func = descriptor.value;
            const newFunc = function () {
                //const result = func.apply(this, arguments);
                const argumentNames = getArgumentNames(func);
                let params = arguments.length == 0 ? null : convertParams(arguments, argumentNames);
                //debugger;
                return self._sendReq({
                    url: url,
                    method: 'get',
                    params: params
                    /* headers: {
                         'token': 'coolmatoken',
                     }*/

                });
            }
            return {
                ...descriptor,
                value: newFunc
            }
        }
    }


    body(target, name, descriptor) {
        //alert("@body:" + name);
    }

    /*export class Result{
        code:number;
        msg:string;
        data:any;
        time:number;

    }*/



}

function convertParams(array, names) {
    let ret = {};
    for (let i = 0; i < array.length; i++) {
        if (typeof array[i] == 'object') {
            ret = Object.assign(ret, array[i]);
        } else {
            ret[names[i]] = array[i];
        }
    }
    return ret;

}


function getArgumentNames(fn) {
    if (typeof fn !== 'object' && typeof fn !== 'function') return;
    const COMMENTS = /((\/\/.*$)|(\/\*[\s\S]*?\*\/))/mg;
    const DEFAULT_PARAMS = /=[^,)]+/mg;
    const FAT_ARROWS = /=>.*$/mg;
    let code = fn.prototype ? fn.prototype.constructor.toString() : fn.toString();
    code = code
        .replace(COMMENTS, '')
        .replace(FAT_ARROWS, '')
        .replace(DEFAULT_PARAMS, '');
    let result = code.slice(code.indexOf('(') + 1, code.indexOf(')')).match(/([^\s,]+)/g);
    return result === null ? [] : result;
}
