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
- 页面管理多服务器 SSH 连接与加密凭据
- 新服务器自动提供常用只读命令，也可配置带安全参数约束的命令模板及普通/危险风险等级
- 缺少命令时由助手提出完整命令，服务端自动判定风险并在对话内确认添加
- 对话开始前选择服务器，危险命令在回复中二次确认
- 自定义 OpenAI/Anthropic 模型提供方，测试连接、拉取模型，并在输入框切换模型与推理等级
- 严格主机密钥校验、输出截断和命令超时
- PostgreSQL/H2 持久化与 Flyway 数据库迁移

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

服务器与命令直接在服务器助手页面配置并持久化。每台服务器可独立选择用户名加密码或用户名加私钥；
密码、私钥和私钥口令使用 `SERVER_CREDENTIAL_MASTER_KEY` 进行 AES-256-GCM 加密后保存，接口不会
返回明文凭据。生产数据库使用 PostgreSQL，表结构只通过 Flyway 迁移。

服务器助手按轻量 ReAct 循环工作，可直接提出“工作目录 + 临时命令”，不再要求每个路径或操作先新增命名命令。
服务端能证明只读的临时命令自动执行；写操作、复合 Shell 和无法判定的命令会暂停，页面可选择单次执行、
在本对话放行完全相同的命令、取消任务，或补充限制后从同一任务继续。固定命令、参数模板和六个默认只读命令
继续作为长期快捷方式；页面支持搜索、启停和批量维护，停用只代表助手看不到该命令。固定危险命令仍需一次性
执行确认。对话记录当前只保存在浏览器，可从近期对话悬停删除或批量管理，删除时同步释放模型
记忆、服务器绑定、待确认操作和对话级放行规则。
服务器对话还可配置 OpenAI Chat Completions 或 Anthropic Messages 提供方，测试连接并拉取模型目录；模型显式
返回的思考内容会通过独立事件在页面折叠展示。OpenAI Responses 协议当前可测试和读取目录，但因 Spring AI 1.1
尚无工具调用适配而不会出现在可用模型选择中。提供方 API 密钥同样加密保存。自定义提供方是平台全局配置，
入口位于左下角“设置”，每个服务器对话都可在输入框中按“提供方 → 模型”选择，并用滑块调整推理等级。
配置方法与权限说明见 [服务器助手文档](docs/server-assistant.md)。

## CI/CD

Drone 在 `master` 分支推送时执行 Maven 测试和组件语法检查，全部通过后连接服务器部署精确提交。部署脚本会先构建候选镜像，健康检查通过后才更新稳定版本；失败时自动恢复上一镜像。

首次部署前需要：

1. 将 `.env.example` 复制到服务器 `/app/ai-platform/.env`，填写模型、PostgreSQL 和助手配置。
2. 使用 `openssl rand -base64 32` 生成并妥善备份 `SERVER_CREDENTIAL_MASTER_KEY`。
3. 在 Drone 中配置 `ssh_host`、`ssh_port`、`ssh_username`、`ssh_password`。
4. 配置 `ai.thxdxw.cn` DNS 和 Nginx，示例位于 `deploy/nginx/ai.thxdxw.cn.conf.example`。
