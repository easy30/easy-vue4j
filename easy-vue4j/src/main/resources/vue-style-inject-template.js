const css47_43_126a = `{{css}}`;
(function() {
    if (typeof document === 'undefined') return; // SSR 安全
    const attrName = 'data-style-{{id}}';
    const selector = `style[${attrName}]`;
    let style = document.querySelector(selector);
    if (style) {
        style.textContent = css47_43_126a;
    } else {
        style = document.createElement('style');
        style.setAttribute(attrName, '');
        style.textContent = css47_43_126a;
        document.head.appendChild(style);
    }
})();