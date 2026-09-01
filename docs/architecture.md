# 总体架构

## 当前形态

第一阶段采用一个 Spring Boot 进程，通过包级模块隔离不同助手。Spring Modulith 测试负责阻止模块形成未声明的依赖。

```text
home-page
  └─ 加载网站助手组件脚本
       └─ 调用 ai-platform 公开 SSE 接口

ai-platform
  ├─ platform：模型与共享契约
  ├─ website：网站知识、接口、CORS、限流
  ├─ blog：博客查询、访问控制、对话内发布确认
  └─ server：服务器/命令配置、凭据加密、SSH 执行与危险命令确认
```

## 网站助手请求链

```text
首页组件
→ POST /api/public/v1/website/messages
→ 来源检查和限流
→ WebsiteAssistantService
→ AssistantChatGateway
→ 当前配置的模型
→ SSE 文本片段
```

助手编号由服务端固定为 `website`，浏览器不能通过请求参数切换到博客或服务器助手。

## 博客助手请求链

```text
/blog/assistant/ 独立页面
→ Bearer 访问口令校验
→ POST /api/blog/v1/messages
→ BlogAssistantService
→ AssistantChatGateway + BlogQueryTools（只读）+ BlogPublicationTool（只生成选项）
→ 博客 Agent 查询 API / 当前模型
→ SSE 文本片段
```

模型只能通过发布候选工具生成 15 分钟有效的一次性发布选项，不能直接写入博客。
选项随 SSE 回复显示在消息下方；管理员点击“发布”后，服务端才访问博客发布 API。选项会在
调用上游前原子消费，避免重复点击造成重复文章。

## 服务器助手请求链

```text
/server/assistant/ 独立页面
→ Bearer 访问口令校验
→ POST /api/server/v1/messages
→ ServerAssistantService
→ 当前对话绑定的服务器 + 数据库中的固定命令
→ 普通命令直接执行 / 危险命令生成确认选项
→ 严格校验页面保存的主机公钥后通过 SSH 连接目标服务器
```

服务器和命令分别持久化到 `server_assistant_server` 与 `server_assistant_command`。每台服务器独立使用
用户名加私钥或用户名加密码认证；凭据经 AES-256-GCM 加密后保存，主密钥只来自环境变量。页面只能
知道凭据已经配置，模型上下文只包含服务器元数据和命令 ID、名称、用途、风险等级。

模型不能传入命令文本，只能执行页面配置的固定命令 ID。普通命令立即执行；危险命令先生成 10 分钟
有效的一次性确认选项，只有管理员点击“执行”后才调用 SSH。配置变化会撤销该服务器所有待确认操作；
操作开始后不自动重试，避免网络不确定时重复执行。同一对话在服务端内存中绑定一台服务器，切换目标
必须新建对话。

## 数据表职责

当前只持久化服务器助手配置，不持久化聊天：

- `server_assistant_server`：连接信息、加密凭据、主机公钥和启停状态。
- `server_assistant_command`：某台服务器允许执行的固定命令及风险等级。
- `flyway_schema_history`：仅由 Flyway 维护数据库迁移历史。

未来持久化聊天时，网站、博客和服务器助手分别建立自己的会话与消息表；审计记录再使用独立审计表，
不会创建跨模块共用的万能 `conversation` 或 `message` 表。

## 后续演进

需要独立扩缩容或将密钥迁移到外部 KMS 时，再拆分配置与 SSH 执行服务。

只有出现独立扩缩容或故障隔离需求时，才从当前单体中拆出服务。
