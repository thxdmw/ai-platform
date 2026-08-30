# AI Platform

面向个人项目的统一 AI 平台。当前已实现可嵌入首页的网站助手、博客后台助手，以及支持多台服务器的 SSH 运维助手。

生产域名：`https://ai.thxdxw.cn`。

## 当前能力

- 网站助手公开 SSE 接口
- 首页右下角悬浮式 Web Component
- 网站公开知识与系统规则隔离
- 精确 CORS 来源配置
- 按客户端地址进行基础限流
- Ollama 与 DeepSeek 模型配置入口
- 独立的博客后台助手页面与本地会话历史
- 博客概览、搜索、详情、分类与标签的精简只读工具
- 对话内生成发布选项，用户点击确认后才执行发布
- ChatGPT 风格的 Markdown、表格、代码块和消息复制
- 多服务器 SSH 清单、状态与日志查询
- 白名单服务和容器的对话内重启确认
- 严格主机密钥校验、输出截断和命令超时

## 本地启动

```powershell
$env:JAVA_HOME='D:\java\environment\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

组件预览地址：`http://localhost:9900/preview/website-assistant.html`

博客助手地址：`http://localhost:9900/blog/assistant/`

服务器助手地址：`http://localhost:9900/server/assistant/`

平台首页以卡片展示并进入三个助手。

首页只需加载一个脚本：

```html
<script src="https://ai.thxdxw.cn/widgets/website-assistant/widget.js" defer></script>
```

环境变量与反向代理说明见 [首页接入文档](docs/home-page-integration.md)。

博客助手需要配置 `BLOG_ASSISTANT_ACCESS_TOKEN`、`BLOG_API_BASE_URL` 和
`BLOG_API_KEY`。访问口令由管理员进入页面时手动输入，只保存在浏览器当前会话中。

服务器助手通过 `SERVER_ASSISTANT_SERVERS_JSON` 配置多台地位相同的受控服务器。每台服务器可以
独立选择 `username + privateKeyPath` 私钥认证，或 `username + passwordEnv` 密码认证，并分别配置
允许查看/重启的 systemd 服务和 Docker 容器。`passwordEnv` 填写的是保存密码的环境变量名，真实
密码不写入 JSON。SSH 文件需挂载到容器的 `/run/ai-platform/ssh`；完整示例见 [.env.example](.env.example)。

服务器助手不开放任意 Shell：模型只可调用固定的状态、进程、Docker、服务状态与日志工具；重启
白名单目标时只生成一次性确认选项，管理员点击“执行”后服务端才连接 SSH。

## CI/CD

Drone 在 `master` 分支推送时执行 Maven 测试和组件语法检查，全部通过后连接服务器部署精确提交。部署脚本会先构建候选镜像，健康检查通过后才更新稳定版本；失败时自动恢复上一镜像。

首次部署前需要：

1. 将 `.env.example` 复制到服务器 `/app/ai-platform/.env` 并填写模型密钥和助手访问配置。
2. 将服务器助手私钥与 `known_hosts` 放到 `/app/ai-platform/ssh`，私钥只授予部署账号读取权限。
3. 在 Drone 中配置 `ssh_host`、`ssh_port`、`ssh_username`、`ssh_password`。
4. 配置 `ai.thxdxw.cn` DNS 和 Nginx，示例位于 `deploy/nginx/ai.thxdxw.cn.conf.example`。
