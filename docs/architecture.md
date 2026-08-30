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
  └─ server：服务器助手边界
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

## 后续演进

服务器助手先实现只读查询，写操作通过单独部署的白名单 Runner 执行。

只有出现独立扩缩容或故障隔离需求时，才从当前单体中拆出服务。
