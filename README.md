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
- 新服务器自动提供常用只读命令，也可独立配置固定命令及普通/危险风险等级
- 缺少命令时由助手提出完整命令，服务端自动判定风险并在对话内确认添加
- 对话开始前选择服务器，危险命令在回复中二次确认
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

模型可以在缺少能力时提出一条完整的固定命令，但未经管理员在对话中确认不会保存或执行；确认后只添加
到当前选择的服务器。服务端按只读白名单自动判定风险，无法证明只读的命令按危险处理。已经保存的普通
命令可直接执行，危险命令仍会生成独立的一次性执行确认。手动配置和六个默认只读命令继续保留。对话记录
当前仍只保存在浏览器，并支持全选或多选删除；删除时会同步释放服务端模型记忆、服务器绑定和待确认操作。
配置方法与权限说明见 [服务器助手文档](docs/server-assistant.md)。

## CI/CD

Drone 在 `master` 分支推送时执行 Maven 测试和组件语法检查，全部通过后连接服务器部署精确提交。部署脚本会先构建候选镜像，健康检查通过后才更新稳定版本；失败时自动恢复上一镜像。

首次部署前需要：

1. 将 `.env.example` 复制到服务器 `/app/ai-platform/.env`，填写模型、PostgreSQL 和助手配置。
2. 使用 `openssl rand -base64 32` 生成并妥善备份 `SERVER_CREDENTIAL_MASTER_KEY`。
3. 在 Drone 中配置 `ssh_host`、`ssh_port`、`ssh_username`、`ssh_password`。
4. 配置 `ai.thxdxw.cn` DNS 和 Nginx，示例位于 `deploy/nginx/ai.thxdxw.cn.conf.example`。
