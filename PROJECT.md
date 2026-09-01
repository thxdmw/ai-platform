# AI Platform 项目上下文

## 项目目标

本项目为个人项目提供统一的模型调用、会话、工具、审批和审计能力。当前已接入网站助手、博客助手和多服务器 SSH 助手。

网站助手组件的源码和接口由本项目维护，但组件通过脚本嵌入 `home-page`，在首页右下角显示，不复制页面实现到首页仓库。

## 技术栈

- JDK 21
- Spring Boot 3.5
- Spring AI 1.1
- Spring Modulith 1.4
- Spring JDBC + Flyway
- PostgreSQL（生产）/ H2（本地与测试）
- Maven
- 原生 Web Component，用于跨项目嵌入网站助手

## 模块定位

| 目录 | 职责 |
|---|---|
| `platform` | 模型调用等跨助手共享契约，内部实现放在 `platform.internal` |
| `website` | 公开网站助手、来源限制、限流和网站知识 |
| `blog` | 博客后台助手、精简查询工具、访问控制和对话内发布确认 |
| `server` | 服务器与固定命令配置、凭据加密、SSH 执行和危险命令确认 |
| `static/widgets/website-assistant` | 可嵌入首页的助手组件 |
| `static/blog/assistant` | 博客后台助手独立页面，沿用旧 ai-cms 视觉语言 |
| `static/server/assistant` | 服务器助手独立页面、多服务器清单和对话内操作确认 |
| `static/preview` | 组件本地视觉验证页面 |
| `docs` | 架构和接入说明 |

## 不可破坏的边界

1. 浏览器代码中不得保存模型密钥、HMAC 密钥或后台凭据。
2. 网站助手只能调用公开网站能力，不能装配博客和服务器工具。
3. 博客发布和服务器写操作必须经过服务端审批，不能只依赖提示词确认。
4. 模型不得生成或修改任意 Shell；只能按 ID 执行管理员在页面配置的固定命令。
5. SSH 凭据必须使用环境变量提供的主密钥加密后入库，任何读取接口不得返回明文凭据。
6. 数据库表结构和初始化数据必须使用 Flyway 版本化迁移，禁止依赖自动建表或手工修改生产库。
7. 不同助手、配置、命令、会话和审计记录按职责分表；禁止用一张通用表混装多个模块的数据。

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
`SERVER_ASSISTANT_ACCESS_TOKEN` 与 `SERVER_CREDENTIAL_MASTER_KEY`，服务器、SSH 凭据、主机公钥和
允许执行的命令均在页面管理。

## 易错点

- 首页与 AI 平台通常跨域，生产环境必须通过 `WEBSITE_ASSISTANT_ALLOWED_ORIGINS` 精确配置首页来源。
- 浏览器无法安全保管共享签名密钥；公开接口依靠精确来源限制、服务端限流和固定助手绑定保护。
- 当前默认模型是本地 Ollama。页面验证不要求模型在线，但真实问答需要启动对应模型服务。
- 首页只加载组件脚本。组件资源和交互逻辑应继续在本仓库维护，避免两个仓库出现不同版本。
- 博客助手访问口令只保存在当前浏览器会话中；聊天记录保存在浏览器本地，不作为服务端审计记录。
- 博客模型可调用精简只读工具并生成发布选项，但不能直接发布；用户必须在对应回复下点击“发布”，服务端才调用写入接口。
- 服务器和命令持久化在各自职责表中；新服务器自动添加六个常用只读命令，已有服务器可以幂等补齐。
- 服务器对话记录目前只保存在浏览器本地，支持管理模式下全选或多选删除；删除时必须同步释放服务端模型记忆、服务器绑定和待确认操作，删除全部后保留一个空白对话。
- SSH 强制校验页面保存的主机公钥；服务账号仍受操作系统权限限制，需要为确实允许的命令配置最小化 sudo 权限。
- 危险命令配置发生变化时会使该服务器尚未执行的确认选项失效，避免确认旧命令却执行新内容。
- 生产域名为 `ai.thxdxw.cn`，宿主机默认监听 `20005`；旧 `ai-cms` 使用 `20004`，迁移期不能占用同一端口。
- 非 Compose 部署从 `/app/ai-platform/.env` 读取配置，部署脚本不能 `source` 该文件。
- Windows 本机没有 Docker 时，必须在交付说明中明确 Docker 镜像未做本机构建；至少执行 YAML 解析与 `deploy.sh` 语法检查。

## 专项文档

- [总体架构](docs/architecture.md)
- [首页接入网站助手](docs/home-page-integration.md)
- [服务器助手配置](docs/server-assistant.md)
