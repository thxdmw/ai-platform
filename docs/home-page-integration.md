# 首页接入网站助手

## 目标

组件代码由 `ai-platform` 提供。未打开时入口显示在首页右下角；桌面端打开后展示右侧整高对话栏并让首页内容同步变窄，移动端则全屏展示。首页项目不再维护助手的 CSS 和交互逻辑。

组件回答按 Markdown 排版，渲染时从 jsdelivr 加载与博客/服务器助手同版本的 `marked@11.1.1` 和 `dompurify@3.2.6`。如果首页内容安全策略（CSP）不允许访问该 CDN 或 CDN 不可用，回答会退化为转义后的纯文本，不会渲染模型返回的原始 HTML。

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
WEBSITE_ASSISTANT_ACCESS_TOKEN=使用独立高强度口令
WEBSITE_ASSISTANT_REQUESTS_PER_MINUTE=6
WEBSITE_ASSISTANT_REQUESTS_PER_CLIENT_PER_DAY=30
WEBSITE_ASSISTANT_REQUESTS_PER_DAY=300
AI_CHAT_PROVIDER=deepseek
DEEPSEEK_API_KEY=由部署环境提供
```

多个精确来源使用逗号分隔。不要使用 `*`，也不要在首页 JavaScript 中配置共享密钥。限流计数保存在当前应用实例内存中，重启后重置；如果以后水平扩容到多实例，应迁移到 Redis 统一计数。

## 知识库后台

从 AI 平台首页点击「网站助手」，或直接打开 `/website/assistant/`。口令验证成功后可以管理两类知识：

- 网站资料：适合功能、项目、入口和站点介绍。
- FAQ：适合联系方式、收费、权限等必须使用稳定口径的问题。

后台与其他助手页面统一采用 ChatGPT 风格的浅色中性界面。首页组件在桌面端以带过渡动画的右侧栏打开，在移动端全屏打开；“新建对话”会先清理服务端模型记忆，成功后再清空本地记录并生成新的会话编号。

当前使用可解释的轻量召回：按标题、FAQ 问题、关键词和正文相关性取最多 8 条、最多 6000 字符送入模型。这已避免全量提示词的 token 浪费；长文档和百级条目再升级为 pgvector 混合检索。

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
