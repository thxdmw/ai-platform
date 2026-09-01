(() => {
    'use strict';

    // 组件可能被多个脚本入口重复注入（如首页同时挂了新旧版本），用全局标记保证只初始化一次。
    if (window.__THX_WEBSITE_ASSISTANT_LOADED__) return;
    window.__THX_WEBSITE_ASSISTANT_LOADED__ = true;

    // 脚本地址同时是默认 API 来源和样式表来源；跨域部署时由首页通过 data-api-base 显式指定。
    const loaderScript = document.currentScript;
    if (!loaderScript || !loaderScript.src) {
        console.error('网站助手加载失败：无法确定组件脚本地址');
        return;
    }

    const scriptUrl = new URL(loaderScript.src, window.location.href);
    const configuredApiBase = loaderScript.dataset.apiBase?.trim();
    const apiBase = configuredApiBase
        ? new URL(configuredApiBase, window.location.href).href.replace(/\/$/, '')
        : scriptUrl.origin;
    const stylesheetUrl = new URL('widget.css', scriptUrl).href;
    const assistantTitle = loaderScript.dataset.title?.trim() || '网站智能助手';

    // 网站助手前端组件：聊天记录和会话编号保存在 localStorage，刷新页面可继续同一会话；
    // 模型记忆也按 conversationId 挂在服务端，因此「新建会话」必须同时换编号并清空本地记录。
    class WebsiteAssistantElement extends HTMLElement {
        constructor() {
            super();
            this.attachShadow({mode: 'open'});
            this.messages = this.readMessages();
            this.conversationId = this.readConversationId();
            this.pending = false;
            this.reader = null;
            this.themeObserver = null;
        }

        connectedCallback() {
            this.renderShell();
            this.bindEvents();
            this.renderMessages();
            this.syncTheme();
            // 首页可能在运行中切换主题（data-theme/class），跟随宿主页面而不是固定一种外观。
            this.themeObserver = new MutationObserver(() => this.syncTheme());
            this.themeObserver.observe(document.documentElement, {
                attributes: true,
                attributeFilter: ['data-theme', 'class']
            });
        }

        disconnectedCallback() {
            this.themeObserver?.disconnect();
            // 组件被移除时中断进行中的 SSE 读取，避免 reader 悬挂占用连接。
            this.reader?.cancel().catch(() => {});
        }

        // 整个界面用原生模板字符串构建并放入 shadow DOM，与首页样式完全隔离；
        // 交互元素不依赖任何外部框架，方便嵌入任意站点。
        renderShell() {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = stylesheetUrl;

            const wrapper = document.createElement('div');
            wrapper.className = 'thx-ai-root';
            wrapper.innerHTML = `
                <button class="thx-ai-launcher" type="button" aria-label="打开网站智能助手" aria-expanded="false">
                    <span aria-hidden="true">🤖</span>
                </button>
                <section class="thx-ai-panel" role="dialog" aria-label="网站智能助手" hidden>
                    <header class="thx-ai-header">
                        <div>
                            <strong class="thx-ai-title"></strong>
                            <span>回答首页功能与导航问题</span>
                        </div>
                        <div class="thx-ai-header-actions">
                            <button class="thx-ai-new" type="button" title="新建会话" aria-label="新建会话">↻</button>
                            <button class="thx-ai-close" type="button" title="关闭" aria-label="关闭助手">×</button>
                        </div>
                    </header>
                    <div class="thx-ai-messages" role="log" aria-live="polite"></div>
                    <form class="thx-ai-form">
                        <label class="thx-ai-input-label" for="thx-ai-input">询问网站功能</label>
                        <div class="thx-ai-input-row">
                            <textarea id="thx-ai-input" rows="1" maxlength="500" placeholder="例如：博客入口在哪里？"></textarea>
                            <button class="thx-ai-send" type="submit" aria-label="发送消息">发送</button>
                        </div>
                        <p class="thx-ai-hint">AI 可能出错，请以首页实际入口为准</p>
                    </form>
                </section>`;

            this.shadowRoot.replaceChildren(link, wrapper);
            this.launcher = this.shadowRoot.querySelector('.thx-ai-launcher');
            this.panel = this.shadowRoot.querySelector('.thx-ai-panel');
            this.shadowRoot.querySelector('.thx-ai-title').textContent = assistantTitle;
            this.launcher.setAttribute('aria-label', `打开${assistantTitle}`);
            this.panel.setAttribute('aria-label', assistantTitle);
            this.messagesElement = this.shadowRoot.querySelector('.thx-ai-messages');
            this.form = this.shadowRoot.querySelector('.thx-ai-form');
            this.input = this.shadowRoot.querySelector('textarea');
            this.sendButton = this.shadowRoot.querySelector('.thx-ai-send');
        }

        bindEvents() {
            this.launcher.addEventListener('click', () => this.open());
            this.shadowRoot.querySelector('.thx-ai-close').addEventListener('click', () => this.close());
            this.shadowRoot.querySelector('.thx-ai-new').addEventListener('click', () => this.startNewConversation());
            this.form.addEventListener('submit', event => {
                event.preventDefault();
                this.sendCurrentMessage();
            });
            this.input.addEventListener('keydown', event => {
                if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    this.form.requestSubmit();
                }
            });
            document.addEventListener('keydown', event => {
                if (event.key === 'Escape' && !this.panel.hidden) this.close();
            });
        }

        open() {
            this.panel.hidden = false;
            this.launcher.setAttribute('aria-expanded', 'true');
            this.input.focus();
        }

        close() {
            this.panel.hidden = true;
            this.launcher.setAttribute('aria-expanded', 'false');
            this.launcher.focus();
        }

        // 新会话 = 换一个新的 conversationId。服务端对每个编号保留独立模型记忆，
        // 本地旧的 30 条消息也必须清掉，否则会与「新会话」的语义矛盾。
        startNewConversation() {
            this.reader?.cancel().catch(() => {});
            this.reader = null;
            this.pending = false;
            this.messages = [];
            this.conversationId = this.createConversationId();
            this.persistState();
            this.updatePendingState();
            this.renderMessages();
            this.input.focus();
        }

        async sendCurrentMessage() {
            const message = this.input.value.trim();
            if (!message || this.pending) return;

            this.input.value = '';
            this.messages.push({role: 'user', content: message});
            // 先渲染一个空的助手消息占位，SSE 文本片段到达后逐段追加，形成流式打字效果。
            const assistantMessage = {role: 'assistant', content: ''};
            this.messages.push(assistantMessage);
            this.pending = true;
            this.updatePendingState();
            this.renderMessages();

            try {
                await this.streamAnswer(message, chunk => {
                    assistantMessage.content += chunk;
                    this.updateLastAssistantMessage(assistantMessage.content);
                });
                // 服务端正常结束但没有输出任何文本（如空回答兜底）时，补一句友好提示。
                if (!assistantMessage.content) {
                    assistantMessage.content = '抱歉，我暂时没有得到回答，请稍后再试。';
                }
            } catch (error) {
                console.error('网站助手请求失败', error);
                assistantMessage.content = '网站助手暂时无法连接，请稍后再试。';
            } finally {
                this.pending = false;
                this.reader = null;
                this.updatePendingState();
                this.updateLastAssistantMessage(assistantMessage.content);
                this.persistState();
                this.input.focus();
            }
        }

        // 用 fetch + ReadableStream 手写 SSE 解析，而不是 EventSource：
        // EventSource 只支持 GET，而本站接口是 POST（避免消息进入服务器访问日志/代理缓存）。
        async streamAnswer(message, onChunk) {
            const response = await fetch(`${apiBase}/api/public/v1/website/messages`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({conversationId: this.conversationId, message})
            });
            if (!response.ok) {
                throw new Error(`接口返回 ${response.status}`);
            }
            if (!response.body) {
                throw new Error('浏览器不支持流式响应');
            }

            this.reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let completed = false;

            // SSE 事件以空行分隔，但网络分片不会恰好切在事件边界，
            // 因此保留未消费的 buffer，等下一个分片到达再补全。
            while (!completed) {
                const {done, value} = await this.reader.read();
                buffer += decoder.decode(value || new Uint8Array(), {stream: !done});
                const events = buffer.split(/\r?\n\r?\n/);
                buffer = events.pop() || '';
                for (const event of events) {
                    completed = this.consumeSseEvent(event, onChunk) || completed;
                }
                if (done) break;
            }

            // 流已结束但最后一个事件没有空行结尾，补处理一次。
            if (buffer && !completed) this.consumeSseEvent(buffer, onChunk);
        }

        // 只取 data: 行并按行还原（服务端只有 data 事件，没有 event/type 字段）。
        // [DONE] 是服务端约定的流结束标记，遇到即停止，不再把结束标记当作文本渲染。
        consumeSseEvent(event, onChunk) {
            const data = event.split(/\r?\n/)
                .filter(line => line.startsWith('data:'))
                .map(line => line.slice(5).replace(/^ /, ''))
                .join('\n');
            if (!data) return false;
            if (data.trim() === '[DONE]') return true;
            onChunk(data);
            return false;
        }

        renderMessages() {
            this.messagesElement.replaceChildren();
            if (this.messages.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'thx-ai-empty';
                empty.innerHTML = '<span aria-hidden="true">👋</span><strong>你好！</strong><p>我可以介绍首页功能，并帮你找到对应入口。</p>';
                this.messagesElement.appendChild(empty);
                return;
            }

            for (const message of this.messages) {
                this.messagesElement.appendChild(this.createMessageElement(message));
            }
            this.scrollToLatest();
        }

        createMessageElement(message) {
            const element = document.createElement('div');
            element.className = `thx-ai-message thx-ai-message-${message.role}`;
            element.textContent = message.content || (this.pending ? '思考中…' : '');
            return element;
        }

        updateLastAssistantMessage(content) {
            const elements = this.messagesElement.querySelectorAll('.thx-ai-message-assistant');
            const last = elements[elements.length - 1];
            if (last) last.textContent = content || (this.pending ? '思考中…' : '');
            this.scrollToLatest();
        }

        updatePendingState() {
            this.input.disabled = this.pending;
            this.sendButton.disabled = this.pending;
            this.sendButton.textContent = this.pending ? '等待' : '发送';
        }

        scrollToLatest() {
            requestAnimationFrame(() => {
                this.messagesElement.scrollTop = this.messagesElement.scrollHeight;
            });
        }

        // 主题跟随宿主页面：优先 data-theme 显式声明，缺省时按系统 prefers-color-scheme 判断。
        syncTheme() {
            const declaredTheme = document.documentElement.getAttribute('data-theme');
            const dark = declaredTheme === 'dark'
                || (!declaredTheme && window.matchMedia('(prefers-color-scheme: dark)').matches);
            this.dataset.theme = dark ? 'dark' : 'light';
        }

        // 会话编号缺省时生成一个新的并持久化；隐私模式等场景 localStorage 不可用也不影响本次会话。
        readConversationId() {
            try {
                return localStorage.getItem('thx_website_assistant_conversation_id') || this.createConversationId();
            } catch (_) {
                return this.createConversationId();
            }
        }

        createConversationId() {
            if (window.crypto?.randomUUID) return window.crypto.randomUUID();
            return `web-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        }

        // 只信任结构完整的消息记录，并限制最多 30 条，防止坏数据/超大记录拖垮页面。
        readMessages() {
            try {
                const saved = JSON.parse(localStorage.getItem('thx_website_assistant_messages') || '[]');
                if (!Array.isArray(saved)) return [];
                return saved
                    .filter(item => item && ['user', 'assistant'].includes(item.role) && typeof item.content === 'string')
                    .slice(-30);
            } catch (_) {
                return [];
            }
        }

        persistState() {
            try {
                localStorage.setItem('thx_website_assistant_conversation_id', this.conversationId);
                localStorage.setItem('thx_website_assistant_messages', JSON.stringify(this.messages.slice(-30)));
            } catch (_) {
                // 隐私模式可能禁止本地存储；这不应影响当前会话继续使用。
            }
        }
    }

    if (!customElements.get('thx-website-assistant')) {
        customElements.define('thx-website-assistant', WebsiteAssistantElement);
    }

    const mount = () => {
        if (!document.querySelector('thx-website-assistant')) {
            document.body.appendChild(document.createElement('thx-website-assistant'));
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', mount, {once: true});
    } else {
        mount();
    }
})();
