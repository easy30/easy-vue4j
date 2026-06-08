/**
 * Acorn AST 解析器 - 提取 Vue <script setup> 中的关键信息
 * 调用方式: parseVueSetup(input) 返回 JSON 字符串
 */
function parseVueSetup(input) {
    var ast = acorn.parse(input, { sourceType: 'module', ecmaVersion: 2022 });
    
    var result = {
        topLevelNames: [],
        componentNames: [],
        propsDef: '',
        emitsDef: '',
        propsRaw: '',
        emitsRaw: '',
        hasProps: false,
        hasEmits: false,
        hasExpose: false
    };

    function traverse(node, parent) {
        if (!node) return;

        // 提取顶层声明名称
        if (parent === ast && node.type === 'VariableDeclaration') {
            for (var i = 0; i < node.declarations.length; i++) {
                var decl = node.declarations[i];
                if (decl.id && decl.id.type === 'Identifier') {
                    result.topLevelNames.push(decl.id.name);

                    // 提取 components 对象中的组件名
                    if (decl.id.name === 'components' && decl.init && decl.init.type === 'ObjectExpression') {
                        for (var j = 0; j < decl.init.properties.length; j++) {
                            var prop = decl.init.properties[j];
                            if (prop.type === 'Property' && prop.key) {
                                var name = prop.key.name || prop.key.value;
                                if (name && typeof name === 'string' && !name.startsWith('...')) {
                                    result.componentNames.push(name);
                                }
                            }
                        }
                    }
                }
            }
        } else if (parent === ast && node.type === 'FunctionDeclaration' && node.id) {
            result.topLevelNames.push(node.id.name);
        } else if (parent === ast && node.type === 'ClassDeclaration' && node.id) {
            result.topLevelNames.push(node.id.name);
        } else if (parent === ast && node.type === 'ImportDeclaration') {
            for (var i = 0; i < node.specifiers.length; i++) {
                var spec = node.specifiers[i];
                if (spec.local) result.topLevelNames.push(spec.local.name);
            }
        }

        // 提取 defineProps/defineEmits/defineExpose 调用
        if (node.type === 'CallExpression' && node.callee && node.callee.type === 'Identifier') {
            var fnName = node.callee.name;

            if (fnName === 'defineProps') {
                if (node.arguments && node.arguments.length > 0) {
                    var arg = node.arguments[0];
                    result.propsRaw = input.substring(arg.start, arg.end);

                    if (arg.type === 'ObjectExpression') {
                        var propsArr = [];
                        for (var k = 0; k < arg.properties.length; k++) {
                            var prop = arg.properties[k];
                            if (prop.type === 'Property') {
                                var key = prop.key.name || prop.key.value;
                                var valueStr = input.substring(prop.value.start, prop.value.end);
                                valueStr = valueStr.replace(/\s+/g, ' ').trim();
                                propsArr.push(key + ': ' + valueStr);
                            }
                        }
                        result.propsDef = propsArr.join(', ');
                    } else if (arg.type === 'ArrayExpression') {
                        var arr = [];
                        for (var k = 0; k < arg.elements.length; k++) {
                            var el = arg.elements[k];
                            if (el && el.type === 'Literal') arr.push("'" + el.value + "'");
                        }
                        result.propsDef = arr.join(', ');
                    }
                    result.hasProps = true;
                }
            } else if (fnName === 'defineEmits') {
                if (node.arguments && node.arguments.length > 0) {
                    var arg = node.arguments[0];
                    result.emitsRaw = input.substring(arg.start, arg.end);

                    if (arg.type === 'ArrayExpression') {
                        var arr = [];
                        for (var k = 0; k < arg.elements.length; k++) {
                            var el = arg.elements[k];
                            if (el && el.type === 'Literal') arr.push("'" + el.value + "'");
                        }
                        result.emitsDef = '[' + arr.join(', ') + ']';
                    }
                    result.hasEmits = true;
                }
            } else if (fnName === 'defineExpose') {
                result.hasExpose = true;
            }
        }

        // 递归遍历子节点
        for (var key in node) {
            if (key === 'start' || key === 'end' || key === 'type') continue;
            var val = node[key];
            if (Array.isArray(val)) {
                for (var i = 0; i < val.length; i++) traverse(val[i], node);
            } else if (val && typeof val === 'object' && typeof val.type === 'string') {
                traverse(val, node);
            }
        }
    }

    traverse(ast, null);
    return JSON.stringify(result);
}
