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
  └─ server：多服务器 SSH 查询与白名单操作确认
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
→ 固定只读工具 / 生成白名单操作选项
→ 严格校验 known_hosts 后通过 SSH 连接目标服务器
```

服务器配置从 `SERVER_ASSISTANT_SERVERS_JSON` 一次性载入，支持配置多台地位相同的受控服务器。
每台服务器独立使用用户名加私钥，或用户名加密码认证。浏览器只获取服务器名称、地址和操作白名单；
密码、私钥与私钥口令只由 SSH 执行器从环境变量或只读挂载读取，不会进入模型上下文。

模型不能传入任意命令。服务端命令目录只提供系统概览、进程、Docker 状态、白名单服务状态与日志、
白名单容器日志。重启服务或容器时先生成 10 分钟有效的一次性确认选项，只有管理员点击“执行”后
才调用 SSH；操作开始后不自动重试，避免网络不确定时重复执行。

## 后续演进

需要网页动态增删服务器时再引入数据库与密钥服务；数据库结构必须通过 Flyway 管理，凭据只存密文或外部引用。

只有出现独立扩缩容或故障隔离需求时，才从当前单体中拆出服务。
