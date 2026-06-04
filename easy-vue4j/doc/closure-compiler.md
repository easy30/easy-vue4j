# 2026-6-3  用 closure-compiler 编译js
  jar包太大,15m 所以不用了. 用acorn.js替换.

  

```xml
  <dependency>
            <groupId>com.google.javascript</groupId>
            <artifactId>closure-compiler</artifactId>
            <version>v20260531</version>
        </dependency>
```

```java

//BabelLikeJavaParser parser = new BabelLikeJavaParser();
//Set<String> exports = parser.collectTopLevelBindings(code);

package com.github.easy30.vue4j;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 纯Java实现 = Babel 解析 AST + 提取顶层变量
 * 支持：import / const / let / function / class
 * 完全对标 Babel 行为
 */
public class BabelLikeJavaParser {

    public Set<String> collectTopLevelBindings(String jsCode) {
        Set<String> vars = new HashSet<>();

        // 1. 初始化Closure编译器（只解析，不编译）
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
        // 静默模块解析错误
        compiler.setErrorManager(new com.google.javascript.jscomp.PrintStreamErrorManager(
                new java.io.PrintStream(new java.io.OutputStream() { @Override public void write(int b) {} })));

        // 2. 解析JS代码生成AST（忽略模块解析错误）
        com.google.javascript.jscomp.Result result = compiler.compile(
                Arrays.asList(SourceFile.fromCode("externs.js", "")),
                Arrays.asList(SourceFile.fromCode("script.js", jsCode)),
                options
        );
        // 即使有错误，AST 仍然可用
        Node root = compiler.getRoot();
        System.out.println("root children=" + root.getChildCount());
        Node scriptNode = null;
        if (root.getChildCount() > 0) {
            Node last = root.getLastChild();
            System.out.println("last child token=" + last.getToken() + " children=" + last.getChildCount());
            scriptNode = last.getFirstChild();
            if (scriptNode != null) {
                System.out.println("script token=" + scriptNode.getToken() + " children=" + scriptNode.getChildCount());
            }
        }

        // 3. 直接遍历 SCRIPT 子节点（顶层声明）
        if (scriptNode != null) {
            for (Node n = scriptNode.getFirstChild(); n != null; n = n.getNext()) {
                switch (n.getToken()) {
                    // 对应 Babel : ImportDeclaration
                    case IMPORT:
                        for (Node s = n.getFirstChild(); s != null; s = s.getNext()) {
                            if (s.isName()) vars.add(s.getString());
                        }
                        break;

                    // 对应 Babel : VariableDeclaration (const/let/var)
                    case CONST:
                    case LET:
                    case VAR:
                        for (Node init = n.getFirstChild(); init != null; init = init.getNext()) {
                            if (init.isName()) vars.add(init.getString());
                        }
                        break;

                    // 对应 Babel : FunctionDeclaration
                    case FUNCTION:
                        if (n.getFirstChild() != null && n.getFirstChild().isName()) {
                            vars.add(n.getFirstChild().getString());
                        }
                        break;

                    // 对应 Babel : ClassDeclaration
                    case CLASS:
                        if (n.getFirstChild() != null && n.getFirstChild().isName()) {
                            vars.add(n.getFirstChild().getString());
                        }
                        break;
                }
            }
        };

        return vars;
    }

    // 测试入口
    public static void main(String[] args) {
        String code=

                "import { ref, computed, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'\n" +
                        "import { knowledgeGraphApi } from '../api/knowledge-graph-api.ts'\n" +
                        "import { ElMessage, ElMessageBox } from 'element-plus'\n" +
                        "\n" +
                        "// 预设关系类型\n" +
                        "const RELATION_TYPES = ['属于', '包含', '导致', '依赖', '关联', '实现', '继承', '调用']\n" +
                        "\n" +
                        "const cyContainer = ref(null)\n" +
                        "const fileInput = ref(null)\n" +
                        "const searchKeyword = ref('')\n" +
                        "const showAddNode = ref(false)\n" +
                        "const showAddRelation = ref(false)\n" +
                        "const selectedNode = ref(null)\n" +
                        "const allNodes = ref([])\n" +
                        "\n" +
                        "// 面板折叠/拖拽状态\n" +
                        "const panelCollapsed = ref(false)\n" +
                        "const panelWidth = ref(360)\n" +
                        "const isResizing = ref(false)\n" +
                        "\n" +
                        "// 右键菜单状态\n" +
                        "const contextMenu = reactive({ visible: false, x: 0, y: 0, type: '', target: null })\n" +
                        "\n" +
                        "const nodeForm = reactive({ name: '', description: '', nodeType: 'entity' })\n" +
                        "const relationForm = reactive({ targetId: '', type: '关联', description: '' })\n" +
                        "\n" +
                        "// 编辑节点状态\n" +
                        "const showEditNode = ref(false)\n" +
                        "const editNodeForm = reactive({ id: '', name: '', description: '', nodeType: 'entity' })\n" +
                        "\n" +
                        "// 拖拽连线状态\n" +
                        "const showDragRelation = ref(false)\n" +
                        "const dragRelation = reactive({ fromId: '', toId: '', fromName: '', toName: '', type: '关联' })\n" +
                        "\n" +
                        "// 连线模式（点击两个节点建关系）\n" +
                        "const connectMode = ref(false)\n" +
                        "const connectSource = ref(null)\n" +
                        "const connectSourceName = computed(() => connectSource.value ? (connectSource.value.data('name') || '') : '')\n" +
                        "\n" +
                        "let cy = null\n" +
                        "let resizeStartX = 0\n" +
                        "let resizeStartW = 0\n" +
                        "\n" +
                        "// 选中节点数据\n" +
                        "const selectedNodeData = computed(() => {\n" +
                        "    if (!selectedNode.value) return {}\n" +
                        "    return selectedNode.value.data() || {}\n" +
                        "})\n" +
                        "const connectedCount = computed(() => {\n" +
                        "    if (!selectedNode.value) return 0\n" +
                        "    return selectedNode.value.connectedEdges().length\n" +
                        "})\n" +
                        "// 排除源节点的目标选项\n" +
                        "const targetNodeOptions = computed(() => {\n" +
                        "    const srcId = selectedNode.value?.id()\n" +
                        "    return allNodes.value.filter(n => n.id !== srcId)\n" +
                        "})\n" +
                        "\n" +
                        "// 节点类型对应标签颜色\n" +
                        "const nodeTypeTag = (type) => {\n" +
                        "    if (type === 'event') return 'warning'\n" +
                        "    if (type === 'concept') return 'success'\n" +
                        "    return ''\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 面板折叠/拖拽 ==========\n" +
                        "const togglePanel = () => {\n" +
                        "    panelCollapsed.value = !panelCollapsed.value\n" +
                        "    nextTick(() => { if (cy) cy.resize() })\n" +
                        "}\n" +
                        "const onResizeStart = (e) => {\n" +
                        "    isResizing.value = true\n" +
                        "    resizeStartX = e.clientX\n" +
                        "    resizeStartW = panelWidth.value\n" +
                        "    document.addEventListener('mousemove', onResizeMove)\n" +
                        "    document.addEventListener('mouseup', onResizeEnd)\n" +
                        "    e.preventDefault()\n" +
                        "}\n" +
                        "const onResizeMove = (e) => {\n" +
                        "    if (!isResizing.value) return\n" +
                        "    const delta = e.clientX - resizeStartX\n" +
                        "    panelWidth.value = Math.max(260, Math.min(600, resizeStartW + delta))\n" +
                        "}\n" +
                        "const onResizeEnd = () => {\n" +
                        "    isResizing.value = false\n" +
                        "    document.removeEventListener('mousemove', onResizeMove)\n" +
                        "    document.removeEventListener('mouseup', onResizeEnd)\n" +
                        "    nextTick(() => { if (cy) cy.resize() })\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 右键菜单 ==========\n" +
                        "const hideContextMenu = () => { contextMenu.visible = false }\n" +
                        "const contextExpand = () => {\n" +
                        "    hideContextMenu()\n" +
                        "    if (contextMenu.target) loadSubgraph(contextMenu.target.id())\n" +
                        "}\n" +
                        "const contextDeleteNode = async () => {\n" +
                        "    hideContextMenu()\n" +
                        "    if (!contextMenu.target) return\n" +
                        "    try {\n" +
                        "        await ElMessageBox.confirm('确定删除该节点？关联关系也会同步删除', '确认', { type: 'warning' })\n" +
                        "        await knowledgeGraphApi.deleteNode(contextMenu.target.id())\n" +
                        "        ElMessage.success('删除成功')\n" +
                        "        contextMenu.target.remove()\n" +
                        "        selectedNode.value = null\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "// 编辑节点\n" +
                        "const contextEditNode = () => {\n" +
                        "    hideContextMenu()\n" +
                        "    if (!contextMenu.target) return\n" +
                        "    const d = contextMenu.target.data()\n" +
                        "    editNodeForm.id = contextMenu.target.id()\n" +
                        "    editNodeForm.name = d.name || ''\n" +
                        "    editNodeForm.description = d.description || ''\n" +
                        "    editNodeForm.nodeType = d.nodeType || 'entity'\n" +
                        "    showEditNode.value = true\n" +
                        "}\n" +
                        "const saveEditNode = async () => {\n" +
                        "    try {\n" +
                        "        await knowledgeGraphApi.saveNode(editNodeForm.id, editNodeForm.name, editNodeForm.description, editNodeForm.nodeType)\n" +
                        "        ElMessage.success('节点更新成功')\n" +
                        "        showEditNode.value = false\n" +
                        "        // 更新画布中该节点数据\n" +
                        "        const node = cy.getElementById(editNodeForm.id)\n" +
                        "        if (node.length > 0) {\n" +
                        "            node.data('name', editNodeForm.name)\n" +
                        "            node.data('description', editNodeForm.description)\n" +
                        "            node.data('nodeType', editNodeForm.nodeType)\n" +
                        "            node.data('isEvent', editNodeForm.nodeType === 'event')\n" +
                        "            node.data('isConcept', editNodeForm.nodeType === 'concept')\n" +
                        "        }\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "const contextDeleteEdge = async () => {\n" +
                        "    hideContextMenu()\n" +
                        "    if (!contextMenu.target) return\n" +
                        "    const data = contextMenu.target.data()\n" +
                        "    try {\n" +
                        "        await ElMessageBox.confirm('确定删除该关系？', '确认', { type: 'warning' })\n" +
                        "        await knowledgeGraphApi.deleteRelation(data.source, data.target)\n" +
                        "        ElMessage.success('关系已删除')\n" +
                        "        contextMenu.target.remove()\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "// ========== Cytoscape 初始化 ==========\n" +
                        "const initCy = () => {\n" +
                        "    cy = cytoscape({\n" +
                        "        container: cyContainer.value,\n" +
                        "        style: [\n" +
                        "            {\n" +
                        "                selector: 'node',\n" +
                        "                style: {\n" +
                        "                    'label': 'data(name)',\n" +
                        "                    'font-size': '13px',\n" +
                        "                    'text-valign': 'bottom',\n" +
                        "                    'text-margin-y': 5,\n" +
                        "                    'background-color': '#409EFF',\n" +
                        "                    'width': 46,\n" +
                        "                    'height': 46,\n" +
                        "                    'color': '#1a1a2e',\n" +
                        "                    'text-wrap': 'wrap',\n" +
                        "                    'text-max-width': '90px'\n" +
                        "                }\n" +
                        "            },\n" +
                        "            { selector: 'node[?isEvent]', style: { 'background-color': '#f59e0b' } },\n" +
                        "            { selector: 'node[?isConcept]', style: { 'background-color': '#10b981' } },\n" +
                        "            {\n" +
                        "                selector: 'node.hover',\n" +
                        "                style: { 'border-width': 3, 'border-color': '#409EFF', 'width': 52, 'height': 52 }\n" +
                        "            },\n" +
                        "            {\n" +
                        "                selector: 'edge',\n" +
                        "                style: {\n" +
                        "                    'width': 2,\n" +
                        "                    'line-color': '#bfcbd9',\n" +
                        "                    'target-arrow-color': '#bfcbd9',\n" +
                        "                    'target-arrow-shape': 'triangle',\n" +
                        "                    'curve-style': 'bezier',\n" +
                        "                    'label': 'data(label)',\n" +
                        "                    'font-size': '11px',\n" +
                        "                    'color': '#6b7280',\n" +
                        "                    'text-rotation': 'autorotate',\n" +
                        "                    'text-background-color': '#fff',\n" +
                        "                    'text-background-opacity': 0.85,\n" +
                        "                    'text-background-padding': '2px'\n" +
                        "                }\n" +
                        "            },\n" +
                        "            { selector: ':selected', style: { 'border-color': '#409EFF', 'border-width': 3 } },\n" +
                        "            { selector: '.connect-source', style: { 'border-width': 4, 'border-color': '#f97316', 'background-color': '#fb923c' } }\n" +
                        "        ],\n" +
                        "        layout: { name: 'cose', padding: 30 },\n" +
                        "        wheelSensitivity: 0.3,\n" +
                        "        // 关闭原生 Shift 框选，避免与连线冲突\n" +
                        "        boxSelectionEnabled: false\n" +
                        "    })\n" +
                        "\n" +
                        "    // 事件绑定\n" +
                        "    cy.on('select', 'node', e => { selectedNode.value = e.target })\n" +
                        "    cy.on('unselect', 'node', () => { selectedNode.value = null })\n" +
                        "    cy.on('mouseover', 'node', e => { e.target.addClass('hover') })\n" +
                        "    cy.on('mouseout', 'node', e => { e.target.removeClass('hover') })\n" +
                        "    // 双击展开\n" +
                        "    cy.on('dblclick', 'node', e => { loadSubgraph(e.target.id()) })\n" +
                        "    // 右键节点\n" +
                        "    cy.on('cxttap', 'node', e => {\n" +
                        "        e.originalEvent.preventDefault()\n" +
                        "        contextMenu.type = 'node'\n" +
                        "        contextMenu.target = e.target\n" +
                        "        contextMenu.x = Math.min(e.originalEvent.clientX, window.innerWidth - 140)\n" +
                        "        contextMenu.y = Math.min(e.originalEvent.clientY, window.innerHeight - 100)\n" +
                        "        contextMenu.visible = true\n" +
                        "    })\n" +
                        "    // 右键边\n" +
                        "    cy.on('cxttap', 'edge', e => {\n" +
                        "        e.originalEvent.preventDefault()\n" +
                        "        contextMenu.type = 'edge'\n" +
                        "        contextMenu.target = e.target\n" +
                        "        contextMenu.x = Math.min(e.originalEvent.clientX, window.innerWidth - 140)\n" +
                        "        contextMenu.y = Math.min(e.originalEvent.clientY, window.innerHeight - 80)\n" +
                        "        contextMenu.visible = true\n" +
                        "    })\n" +
                        "    // 点击空白关闭菜单\n" +
                        "    cy.on('tap', e => { if (e.target === cy) hideContextMenu() })\n" +
                        "\n" +
                        "    // ========== 节点点击：连线模式处理 ==========\n" +
                        "    cy.on('tap', 'node', e => {\n" +
                        "        if (!connectMode.value) return\n" +
                        "        const node = e.target\n" +
                        "        if (!connectSource.value) {\n" +
                        "            connectSource.value = node\n" +
                        "            node.addClass('connect-source')\n" +
                        "        } else if (connectSource.value.id() === node.id()) {\n" +
                        "            // 点同一个节点：取消\n" +
                        "            node.removeClass('connect-source')\n" +
                        "            connectSource.value = null\n" +
                        "        } else {\n" +
                        "            // 第二个节点：弹出对话框\n" +
                        "            dragRelation.fromId = connectSource.value.id()\n" +
                        "            dragRelation.toId = node.id()\n" +
                        "            dragRelation.fromName = connectSource.value.data('name') || connectSource.value.id()\n" +
                        "            dragRelation.toName = node.data('name') || node.id()\n" +
                        "            dragRelation.type = '关联'\n" +
                        "            showDragRelation.value = true\n" +
                        "            connectSource.value.removeClass('connect-source')\n" +
                        "            connectSource.value = null\n" +
                        "            connectMode.value = false\n" +
                        "        }\n" +
                        "    })\n" +
                        "\n" +
                        "    // ========== Shift+拖拽连线（辅助方式） ==========\n" +
                        "    let dragSource = null\n" +
                        "    cy.on('tapstart', 'node', e => {\n" +
                        "        if (!e.originalEvent || !e.originalEvent.shiftKey) return\n" +
                        "        dragSource = e.target\n" +
                        "        dragSource.lock() // 锁住节点不让位移\n" +
                        "        cy.userPanningEnabled(false)\n" +
                        "    })\n" +
                        "    cy.on('tapend', 'node', e => {\n" +
                        "        if (!dragSource) return\n" +
                        "        const target = e.target\n" +
                        "        dragSource.unlock()\n" +
                        "        cy.userPanningEnabled(true)\n" +
                        "        if (target && target !== dragSource) {\n" +
                        "            dragRelation.fromId = dragSource.id()\n" +
                        "            dragRelation.toId = target.id()\n" +
                        "            dragRelation.fromName = dragSource.data('name') || dragSource.id()\n" +
                        "            dragRelation.toName = target.data('name') || target.id()\n" +
                        "            dragRelation.type = '关联'\n" +
                        "            showDragRelation.value = true\n" +
                        "        }\n" +
                        "        dragSource = null\n" +
                        "    })\n" +
                        "    cy.on('tapend', e => {\n" +
                        "        if (dragSource && e.target === cy) {\n" +
                        "            dragSource.unlock()\n" +
                        "            cy.userPanningEnabled(true)\n" +
                        "            dragSource = null\n" +
                        "        }\n" +
                        "    })\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 图数据加载 ==========\n" +
                        "const loadGraph = async () => {\n" +
                        "    try {\n" +
                        "        const result = await knowledgeGraphApi.getAllGraph()\n" +
                        "        if (result && result.nodes) {\n" +
                        "            renderGraph(result)\n" +
                        "        } else {\n" +
                        "            renderGraph({ nodes: [], edges: [] })\n" +
                        "        }\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "// 增量加载子图（不替换，合并）\n" +
                        "const loadSubgraph = async (nodeId) => {\n" +
                        "    try {\n" +
                        "        const data = await knowledgeGraphApi.getSubgraph(nodeId, 2)\n" +
                        "        if (!data) return\n" +
                        "        mergeGraph(data)\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "const renderGraph = (data) => {\n" +
                        "    if (!cy) return\n" +
                        "    cy.elements().remove()\n" +
                        "    addGraphData(data)\n" +
                        "    runLayout(true)\n" +
                        "}\n" +
                        "\n" +
                        "// 触发布局；fit=true 缩放到合适区域\n" +
                        "const runLayout = (fit = false) => {\n" +
                        "    if (!cy) return\n" +
                        "    nextTick(() => {\n" +
                        "        cy.resize() // 先确保容器尺寸正确\n" +
                        "        cy.layout({\n" +
                        "            name: 'cose',\n" +
                        "            padding: 50,\n" +
                        "            idealEdgeLength: 120,\n" +
                        "            nodeRepulsion: 8000,\n" +
                        "            edgeElasticity: 100,\n" +
                        "            gravity: 0.25,\n" +
                        "            numIter: 1500,\n" +
                        "            randomize: true,\n" +
                        "            animate: false,\n" +
                        "            fit: fit\n" +
                        "        }).run()\n" +
                        "        if (fit) cy.fit(undefined, 50)\n" +
                        "    })\n" +
                        "}\n" +
                        "\n" +
                        "// 手动重新布局按钮\n" +
                        "const relayoutGraph = () => {\n" +
                        "    if (!cy || cy.nodes().length === 0) {\n" +
                        "        ElMessage.info('暂无节点可布局')\n" +
                        "        return\n" +
                        "    }\n" +
                        "    runLayout(true)\n" +
                        "}\n" +
                        "\n" +
                        "// 增量合并图数据\n" +
                        "const mergeGraph = (data) => {\n" +
                        "    if (!cy) return\n" +
                        "    const added = addGraphData(data)\n" +
                        "    if (added.length > 0) {\n" +
                        "        runLayout(false)\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "// 添加图数据（去重），返回新增元素\n" +
                        "const addGraphData = (data) => {\n" +
                        "    const elements = []\n" +
                        "    for (const n of (data.nodes || [])) {\n" +
                        "        if (cy.getElementById(n.id).length === 0) {\n" +
                        "            elements.push({\n" +
                        "                data: {\n" +
                        "                    id: n.id, name: n.name,\n" +
                        "                    description: n.description || '',\n" +
                        "                    nodeType: n.nodeType || 'entity',\n" +
                        "                    isEvent: n.nodeType === 'event',\n" +
                        "                    isConcept: n.nodeType === 'concept'\n" +
                        "                }\n" +
                        "            })\n" +
                        "        }\n" +
                        "    }\n" +
                        "    const existIds = new Set(cy.nodes().map(n => n.id()))\n" +
                        "    for (const n of elements) existIds.add(n.data.id)\n" +
                        "    for (const e of (data.edges || [])) {\n" +
                        "        const edgeId = e.from + '-' + e.to\n" +
                        "        if (cy.getElementById(edgeId).length === 0 && existIds.has(e.from) && existIds.has(e.to)) {\n" +
                        "            elements.push({\n" +
                        "                data: { id: edgeId, source: e.from, target: e.to, label: e.label || e.type || '' }\n" +
                        "            })\n" +
                        "        }\n" +
                        "    }\n" +
                        "    if (elements.length > 0) cy.add(elements)\n" +
                        "    return elements\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 搜索/展开/刷新 ==========\n" +
                        "const searchNode = async () => {\n" +
                        "    try {\n" +
                        "        // 1. 按名称模糊搜索匹配节点\n" +
                        "        const matched = await knowledgeGraphApi.searchNode(searchKeyword.value)\n" +
                        "        if (!matched || matched.length === 0) {\n" +
                        "            ElMessage.info('未找到节点')\n" +
                        "            return\n" +
                        "        }\n" +
                        "        // 2. 对每个匹配节点展开 2 层子图，合并节点和边\n" +
                        "        const nodeMap = new Map()\n" +
                        "        const edgeMap = new Map()\n" +
                        "        for (const node of matched) {\n" +
                        "            const sub = await knowledgeGraphApi.getSubgraph(node.id, 2)\n" +
                        "            if (sub) {\n" +
                        "                for (const n of (sub.nodes || [])) nodeMap.set(n.id, n)\n" +
                        "                for (const e of (sub.edges || [])) edgeMap.set(e.from + '-' + e.to, e)\n" +
                        "            }\n" +
                        "        }\n" +
                        "        // 3. 兜底：未展开任何节点时至少把匹配的节点显示出来\n" +
                        "        if (nodeMap.size === 0) {\n" +
                        "            for (const n of matched) nodeMap.set(n.id, n)\n" +
                        "        }\n" +
                        "        renderGraph({\n" +
                        "            nodes: Array.from(nodeMap.values()),\n" +
                        "            edges: Array.from(edgeMap.values())\n" +
                        "        })\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "const expandNode = () => {\n" +
                        "    if (!selectedNode.value) return\n" +
                        "    loadSubgraph(selectedNode.value.id())\n" +
                        "}\n" +
                        "\n" +
                        "const refreshGraph = () => loadGraph()\n" +
                        "\n" +
                        "// ========== 新增节点/关系 ==========\n" +
                        "const addNode = async () => {\n" +
                        "    try {\n" +
                        "        const result = await knowledgeGraphApi.saveNode('', nodeForm.name, nodeForm.description, nodeForm.nodeType)\n" +
                        "        ElMessage.success('节点创建成功')\n" +
                        "        showAddNode.value = false\n" +
                        "        nodeForm.name = ''\n" +
                        "        nodeForm.description = ''\n" +
                        "        if (result) loadSubgraph(result.id || result)\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "const fetchAllNodes = async () => {\n" +
                        "    try {\n" +
                        "        const result = await knowledgeGraphApi.searchNode('')\n" +
                        "        if (result) allNodes.value = result\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "const openAddRelation = async () => {\n" +
                        "    relationForm.targetId = ''\n" +
                        "    relationForm.type = '关联'\n" +
                        "    relationForm.description = ''\n" +
                        "    await fetchAllNodes()\n" +
                        "    showAddRelation.value = true\n" +
                        "}\n" +
                        "\n" +
                        "const addRelation = async () => {\n" +
                        "    if (!selectedNode.value || !relationForm.targetId) return\n" +
                        "    try {\n" +
                        "        await knowledgeGraphApi.createRelation(selectedNode.value.id(), relationForm.targetId, relationForm.type, relationForm.description)\n" +
                        "        ElMessage.success('关系创建成功')\n" +
                        "        showAddRelation.value = false\n" +
                        "        loadSubgraph(selectedNode.value.id())\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "// 拖拽连线确认\n" +
                        "const confirmDragRelation = async () => {\n" +
                        "    if (!dragRelation.fromId || !dragRelation.toId) return\n" +
                        "    try {\n" +
                        "        await knowledgeGraphApi.createRelation(dragRelation.fromId, dragRelation.toId, dragRelation.type, '')\n" +
                        "        ElMessage.success('关系创建成功')\n" +
                        "        showDragRelation.value = false\n" +
                        "        // 在画布上直接添加新边\n" +
                        "        const edgeId = dragRelation.fromId + '-' + dragRelation.toId\n" +
                        "        if (cy && cy.getElementById(edgeId).length === 0) {\n" +
                        "            cy.add({\n" +
                        "                data: { id: edgeId, source: dragRelation.fromId, target: dragRelation.toId, label: dragRelation.type }\n" +
                        "            })\n" +
                        "        }\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 删除 ==========\n" +
                        "const deleteSelected = async () => {\n" +
                        "    if (!selectedNode.value) return\n" +
                        "    try {\n" +
                        "        await ElMessageBox.confirm('确定删除该节点？关联关系也会同步删除', '确认', { type: 'warning' })\n" +
                        "        await knowledgeGraphApi.deleteNode(selectedNode.value.id())\n" +
                        "        ElMessage.success('删除成功')\n" +
                        "        selectedNode.value.remove()\n" +
                        "        selectedNode.value = null\n" +
                        "    } catch (_) { }\n" +
                        "}\n" +
                        "\n" +
                        "// ========== Excel 导出 ==========\n" +
                        "const exportExcel = () => {\n" +
                        "    if (!cy || cy.nodes().length === 0) {\n" +
                        "        ElMessage.info('当前画布无数据可导出')\n" +
                        "        return\n" +
                        "    }\n" +
                        "    const nodes = cy.nodes().map(n => ({\n" +
                        "        '名称': n.data('name') || '',\n" +
                        "        '描述': n.data('description') || '',\n" +
                        "        '类型': n.data('nodeType') || ''\n" +
                        "    }))\n" +
                        "    // 构建 id→name 映射\n" +
                        "    const idNameMap = {}\n" +
                        "    cy.nodes().forEach(n => { idNameMap[n.id()] = n.data('name') || n.id() })\n" +
                        "    const edges = cy.edges().map(e => ({\n" +
                        "        '源节点': idNameMap[e.data('source')] || e.data('source'),\n" +
                        "        '目标节点': idNameMap[e.data('target')] || e.data('target'),\n" +
                        "        '关系类型': e.data('label') || '',\n" +
                        "        '描述': ''\n" +
                        "    }))\n" +
                        "    const wb = XLSX.utils.book_new()\n" +
                        "    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(nodes), '节点')\n" +
                        "    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(edges), '关系')\n" +
                        "    XLSX.writeFile(wb, '知识图谱.xlsx')\n" +
                        "    ElMessage.success('导出成功')\n" +
                        "}\n" +
                        "\n" +
                        "// ========== Excel 导入 ==========\n" +
                        "const importExcel = async (e) => {\n" +
                        "    const file = e.target.files[0]\n" +
                        "    if (!file) return\n" +
                        "    e.target.value = '' // 允许再次选同一文件\n" +
                        "    const reader = new FileReader()\n" +
                        "    reader.onload = async (evt) => {\n" +
                        "        try {\n" +
                        "            const wb = XLSX.read(evt.target.result, { type: 'array' })\n" +
                        "            const nodeSheet = wb.Sheets['节点']\n" +
                        "            const edgeSheet = wb.Sheets['关系']\n" +
                        "            if (!nodeSheet) { ElMessage.error('Excel 中未找到\"节点\"工作表'); return }\n" +
                        "            const nodeRows = XLSX.utils.sheet_to_json(nodeSheet)\n" +
                        "            const edgeRows = edgeSheet ? XLSX.utils.sheet_to_json(edgeSheet) : []\n" +
                        "\n" +
                        "            // 整理为批量导入参数\n" +
                        "            const nodes = []\n" +
                        "            for (const row of nodeRows) {\n" +
                        "                const name = (row['名称'] || '').trim()\n" +
                        "                if (!name) continue\n" +
                        "                nodes.push({\n" +
                        "                    name,\n" +
                        "                    description: row['描述'] || '',\n" +
                        "                    nodeType: row['类型'] || 'entity'\n" +
                        "                })\n" +
                        "            }\n" +
                        "            const edges = []\n" +
                        "            for (const row of edgeRows) {\n" +
                        "                const fromName = (row['源节点'] || '').trim()\n" +
                        "                const toName = (row['目标节点'] || '').trim()\n" +
                        "                if (!fromName || !toName) continue\n" +
                        "                edges.push({\n" +
                        "                    fromName, toName,\n" +
                        "                    type: row['关系类型'] || '关联',\n" +
                        "                    description: row['描述'] || ''\n" +
                        "                })\n" +
                        "            }\n" +
                        "\n" +
                        "            // 调用批量导入接口\n" +
                        "            const result = await knowledgeGraphApi.batchImport({ nodes, edges })\n" +
                        "            const nc = result?.nodeCount ?? 0\n" +
                        "            const ec = result?.edgeCount ?? 0\n" +
                        "            const nf = result?.nodeFailCount ?? 0\n" +
                        "            const ef = result?.edgeFailCount ?? 0\n" +
                        "            let msg = `导入完成：新建 ${nc} 个节点，${ec} 条关系`\n" +
                        "            if (nf || ef) msg += `；失败 节点${nf} 关系${ef}（详情见后端日志）`\n" +
                        "            ElMessage.success(msg)\n" +
                        "            refreshGraph()\n" +
                        "        } catch (err) {\n" +
                        "            ElMessage.error('导入失败：' + (err.message || '文件解析错误'))\n" +
                        "        }\n" +
                        "    }\n" +
                        "    reader.readAsArrayBuffer(file)\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 连线模式 ==========\n" +
                        "const toggleConnectMode = () => {\n" +
                        "    connectMode.value = !connectMode.value\n" +
                        "    if (!connectMode.value && connectSource.value) {\n" +
                        "        connectSource.value.removeClass('connect-source')\n" +
                        "        connectSource.value = null\n" +
                        "    }\n" +
                        "    if (connectMode.value) {\n" +
                        "        ElMessage.info('连线模式：先点击源节点，再点击目标节点')\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "// ========== 生命周期 ==========\n" +
                        "const onDocClick = () => { hideContextMenu() }\n" +
                        "const onKeyDown = (e) => {\n" +
                        "    if (e.key === 'Escape' && connectMode.value) {\n" +
                        "        toggleConnectMode()\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "onMounted(() => {\n" +
                        "    nextTick(() => {\n" +
                        "        initCy()\n" +
                        "        // 容器尺寸稳定后再加载数据，避免初始重叠\n" +
                        "        setTimeout(() => {\n" +
                        "            loadGraph()\n" +
                        "            fetchAllNodes()\n" +
                        "        }, 50)\n" +
                        "    })\n" +
                        "    document.addEventListener('click', onDocClick)\n" +
                        "    document.addEventListener('keydown', onKeyDown)\n" +
                        "})\n" +
                        "\n" +
                        "onBeforeUnmount(() => {\n" +
                        "    if (cy) cy.destroy()\n" +
                        "    document.removeEventListener('click', onDocClick)\n" +
                        "    document.removeEventListener('keydown', onKeyDown)\n" +
                        "})";

        BabelLikeJavaParser parser = new BabelLikeJavaParser();
        Set<String> result = parser.collectTopLevelBindings(code);
        System.out.println("顶层变量 = " + result);
    }
}

```