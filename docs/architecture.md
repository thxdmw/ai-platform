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
  ├─ blog：博客助手边界
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

## 后续演进

博客助手通过受控 REST 适配器访问博客系统，发布操作进入审批状态；服务器助手先实现只读查询，写操作通过单独部署的白名单 Runner 执行。

只有出现独立扩缩容或故障隔离需求时，才从当前单体中拆出服务。
