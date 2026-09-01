# 总体架构

## 当前形态

第一阶段采用一个 Spring Boot 进程，通过包级模块隔离不同助手。Spring Modulith 测试负责阻止模块形成未声明的依赖。

```text
home-page
  └─ 加载网站助手组件脚本
       └─ 调用 ai-platform 公开 SSE 接口

ai-platform
  ├─ platform：模型与共享契约（内部实现放 platform.internal）
  ├─ website：网站知识、接口、CORS、限流
  ├─ blog：博客查询、访问控制、对话内发布确认
  └─ server：服务器/命令配置、凭据加密、SSH 执行与危险命令确认
```

三个助手模块的包内统一按职责分子包：`controller`（REST/页面控制器）、`service`（业务服务）、
`model`（DTO 与实体记录）、`config`（Properties、拦截器、异常处理）；`server` 额外有
`repository`（JDBC 访问）、`security`（凭据加解密）、`tool`（模型工具），`blog`/`server` 的
模型工具统一放在 `tool` 子包。子包不声明 `@ApplicationModule`，属于父模块一部分，模块边界仍由
各模块根的 `package-info.java` 定义。

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
→ 当前对话绑定的服务器 + 页面选择的模型 + 数据库中的参数化命令模板
→ 已有普通命令直接执行 / 已有危险命令生成执行确认选项
→ 缺少命令时生成添加提议，服务端判定风险并等待添加确认
→ 严格校验页面保存的主机公钥后通过 SSH 连接目标服务器
```

服务器和命令分别持久化到 `server_assistant_server` 与 `server_assistant_command`。每台服务器独立使用
用户名加私钥或用户名加密码认证；凭据经 AES-256-GCM 加密后保存，主密钥只来自环境变量。页面只能
知道凭据已经配置，模型上下文只包含服务器元数据和命令 ID、名称、用途、风险等级。

模型只能按 ID 执行已经保存的命令。模板参数由服务端按类型和白名单校验并做 Shell 转义，路径等参数变化
不会扩大可执行命令集合。缺少能力时可以提出命令模板，但服务端会独立判定风险，并生成
10 分钟有效的一次性添加选项；管理员确认后只写入当前服务器的命令配置。普通命令随后可以直接执行；
危险命令仍要生成独立的执行确认，添加授权不能代替执行授权。配置变化会撤销该服务器所有待确认执行；
操作开始后不自动重试，避免网络不确定时重复执行。同一对话在服务端内存中绑定一台服务器，切换目标
必须新建对话。

当前代理循环由 Spring AI `ChatClient` 的原生工具调用驱动，属于带工具的 chat agent，并没有单独实现一套
可持久化的 ReAct 状态机。命令添加/危险执行使用服务端 action 与一次性续跑凭证做确定性暂停和恢复，避免把
审批状态交给模型推理；模型可继续进行多轮工具调用，但进程重启后不会恢复一段未完成的模型推理循环。

## 数据表职责

当前只持久化服务器助手配置，不持久化聊天：

- `server_assistant_server`：连接信息、加密凭据、主机公钥和启停状态。
- `server_assistant_command`：某台服务器允许执行的命令模板、参数规则及风险等级。
- `server_assistant_model_provider`：OpenAI 兼容提供方地址、接口路径与加密 API 密钥。
- `server_assistant_model`：提供方下可在服务器对话中选择的模型编号与推理等级。
- `flyway_schema_history`：仅由 Flyway 维护数据库迁移历史。

未来持久化聊天时，网站、博客和服务器助手分别建立自己的会话与消息表；审计记录再使用独立审计表，
不会创建跨模块共用的万能 `conversation` 或 `message` 表。

## 后续演进

需要独立扩缩容或将密钥迁移到外部 KMS 时，再拆分配置与 SSH 执行服务。

只有出现独立扩缩容或故障隔离需求时，才从当前单体中拆出服务。
