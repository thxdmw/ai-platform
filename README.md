# AI Platform

面向个人项目的统一 AI 平台。当前已建立模块化单体骨架，并实现可嵌入首页的网站助手组件和流式问答接口。

生产域名：`https://ai.thxdxw.cn`。

## 当前能力

- 网站助手公开 SSE 接口
- 首页右下角悬浮式 Web Component
- 网站公开知识与系统规则隔离
- 精确 CORS 来源配置
- 按客户端地址进行基础限流
- Ollama 与 DeepSeek 模型配置入口
- 博客助手、服务器助手的模块边界

## 本地启动

```powershell
$env:JAVA_HOME='D:\java\environment\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

组件预览地址：`http://localhost:9900/preview/website-assistant.html`

首页只需加载一个脚本：

```html
<script src="https://ai.thxdxw.cn/widgets/website-assistant/widget.js" defer></script>
```

环境变量与反向代理说明见 [首页接入文档](docs/home-page-integration.md)。

## CI/CD

Drone 在 `master` 分支推送时执行 Maven 测试和组件语法检查，全部通过后连接服务器部署精确提交。部署脚本会先构建候选镜像，健康检查通过后才更新稳定版本；失败时自动恢复上一镜像。

首次部署前需要：

1. 将 `.env.example` 复制到服务器 `/app/ai-platform/.env` 并填写模型密钥。
2. 在 Drone 中配置 `ssh_host`、`ssh_port`、`ssh_username`、`ssh_password`。
3. 配置 `ai.thxdxw.cn` DNS 和 Nginx，示例位于 `deploy/nginx/ai.thxdxw.cn.conf.example`。
