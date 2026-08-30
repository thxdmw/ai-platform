# AI Platform 项目上下文

## 项目目标

本项目为个人项目提供统一的模型调用、会话、工具、审批和审计能力。当前已接入网站助手、博客助手和多服务器 SSH 助手。

网站助手组件的源码和接口由本项目维护，但组件通过脚本嵌入 `home-page`，在首页右下角显示，不复制页面实现到首页仓库。

## 技术栈

- JDK 21
- Spring Boot 3.5
- Spring AI 1.1
- Spring Modulith 1.4
- Maven
- 原生 Web Component，用于跨项目嵌入网站助手

## 模块定位

| 目录 | 职责 |
|---|---|
| `platform` | 模型调用等跨助手共享契约，内部实现放在 `platform.internal` |
| `website` | 公开网站助手、来源限制、限流和网站知识 |
| `blog` | 博客后台助手、精简查询工具、访问控制和对话内发布确认 |
| `server` | 多服务器清单、SSH 只读工具、白名单操作与服务端确认 |
| `static/widgets/website-assistant` | 可嵌入首页的助手组件 |
| `static/blog/assistant` | 博客后台助手独立页面，沿用旧 ai-cms 视觉语言 |
| `static/server/assistant` | 服务器助手独立页面、多服务器清单和对话内操作确认 |
| `static/preview` | 组件本地视觉验证页面 |
| `docs` | 架构和接入说明 |

## 不可破坏的边界

1. 浏览器代码中不得保存模型密钥、HMAC 密钥或后台凭据。
2. 网站助手只能调用公开网站能力，不能装配博客和服务器工具。
3. 博客发布和服务器写操作必须经过服务端审批，不能只依赖提示词确认。
4. 服务器助手不得暴露任意 Shell、任意 SQL 或任意文件写入能力。
5. 凭据只能从环境变量或外部密钥服务读取。
6. 后续引入关系型数据库时，表结构和初始化数据必须使用 Flyway 版本化迁移，禁止依赖自动建表或手工修改生产库。

## 验证入口

使用 JDK 21 执行：

```powershell
$env:JAVA_HOME='D:\java\environment\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
mvn package
node --check src/main/resources/static/widgets/website-assistant/widget.js
node --check src/main/resources/static/blog/assistant/app.js
node --check src/main/resources/static/server/assistant/app.js
git diff --check
```

本地页面验证：

```powershell
$env:JAVA_HOME='D:\java\environment\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

打开 `http://localhost:9900/preview/website-assistant.html`。

博客助手页面为 `http://localhost:9900/blog/assistant/`，本地使用前必须设置
`BLOG_ASSISTANT_ACCESS_TOKEN`；查询和发布还需配置博客系统 Agent API。

服务器助手页面为 `http://localhost:9900/server/assistant/`。本地使用前需要配置
`SERVER_ASSISTANT_ACCESS_TOKEN` 与 `SERVER_ASSISTANT_SERVERS_JSON`，真实 SSH 查询还需要准备
私钥或密码环境变量以及 `known_hosts`。

## 易错点

- 首页与 AI 平台通常跨域，生产环境必须通过 `WEBSITE_ASSISTANT_ALLOWED_ORIGINS` 精确配置首页来源。
- 浏览器无法安全保管共享签名密钥；公开接口依靠精确来源限制、服务端限流和固定助手绑定保护。
- 当前默认模型是本地 Ollama。页面验证不要求模型在线，但真实问答需要启动对应模型服务。
- 首页只加载组件脚本。组件资源和交互逻辑应继续在本仓库维护，避免两个仓库出现不同版本。
- 博客助手访问口令只保存在当前浏览器会话中；聊天记录保存在浏览器本地，不作为服务端审计记录。
- 博客模型可调用精简只读工具并生成发布选项，但不能直接发布；用户必须在对应回复下点击“发布”，服务端才调用写入接口。
- 服务器清单在启动时从环境变量载入；服务器凭据不会返回前端或提供给模型。新增服务器需要更新配置并重启应用。
- SSH 强制校验 `known_hosts`；服务账号只应授予读取状态、读取白名单日志和重启白名单目标所需的最小权限。
- 生产域名为 `ai.thxdxw.cn`，宿主机默认监听 `20005`；旧 `ai-cms` 使用 `20004`，迁移期不能占用同一端口。
- 非 Compose 部署从 `/app/ai-platform/.env` 读取配置，部署脚本不能 `source` 该文件。
- Windows 本机没有 Docker 时，必须在交付说明中明确 Docker 镜像未做本机构建；至少执行 YAML 解析与 `deploy.sh` 语法检查。

## 专项文档

- [总体架构](docs/architecture.md)
- [首页接入网站助手](docs/home-page-integration.md)
