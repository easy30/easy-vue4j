const cssText = `{{css}}`;
(function() {
    if (typeof document === 'undefined') return; // SSR 安全
    const attrName = 'data-style-{{id}}';
    const selector = `style[${attrName}]`;
    let style = document.querySelector(selector);
    if (style) {
        style.textContent = cssText;
    } else {
        style = document.createElement('style');
        style.setAttribute(attrName, '');
        style.textContent = cssText;
        document.head.appendChild(style);
    }
})();