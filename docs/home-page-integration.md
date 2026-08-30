# 首页接入网站助手

## 目标

组件代码由 `ai-platform` 提供，但组件仍显示在首页右下角。首页项目不再维护助手的 CSS、交互逻辑或浏览器签名密钥。

## 首页改动

在 `home-page/index.html` 的 `</body>` 前加入：

```html
<script src="https://ai.thxdxw.cn/widgets/website-assistant/widget.js" defer></script>
```

组件默认调用脚本所在域名的接口。开发时可覆盖接口地址：

```html
<script
    src="http://localhost:9900/widgets/website-assistant/widget.js"
    data-api-base="http://localhost:9900"
    defer></script>
```

旧的 `css/ai-assistant.css` 和 `js/aiAssistant.js` 应在新组件上线并验证后删除，避免重复创建两个悬浮按钮。

## 生产环境变量

```text
WEBSITE_ASSISTANT_ALLOWED_ORIGINS=https://thxdxw.cn
WEBSITE_ASSISTANT_REQUESTS_PER_MINUTE=20
AI_CHAT_PROVIDER=deepseek
DEEPSEEK_API_KEY=由部署环境提供
```

多个精确来源使用逗号分隔。不要使用 `*`，也不要在首页 JavaScript 中配置共享密钥。

## 同域路径方案

如果希望浏览器看到的资源和接口都位于首页域名，可以由 Nginx 代理：

```nginx
location /ai-platform/ {
    proxy_pass http://127.0.0.1:9900/;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_read_timeout 120s;
}
```

首页改为：

```html
<script
    src="/ai-platform/widgets/website-assistant/widget.js"
    data-api-base="/ai-platform"
    defer></script>
```

这种方式下页面位置仍属于首页，组件代码和接口则统一由 `ai-platform` 发布。

当前生产方案直接使用独立域名 `https://ai.thxdxw.cn`。同域路径方案保留为以后减少跨域配置时的可选方案。
