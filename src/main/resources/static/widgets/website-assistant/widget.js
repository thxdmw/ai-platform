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
    const configuredTitle = loaderScript.dataset.title?.trim();
    const hostStyleId = 'thx-website-assistant-host-style';

    // 回答渲染库与博客/服务器助手同版本，从 jsdelivr 加载；首页若用 CSP 限制外部脚本，
    // 组件会退化为转义纯文本（见 renderMarkdown），不会放行模型返回的原始 HTML。
    const MARKED_URL = 'https://cdn.jsdelivr.net/npm/marked@11.1.1/marked.min.js';
    const DOMPURIFY_URL = 'https://cdn.jsdelivr.net/npm/dompurify@3.2.6/dist/purify.min.js';

    function loadExternalScript(url) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = url;
            script.async = true;
            script.onload = resolve;
            script.onerror = () => reject(new Error(`脚本加载失败：${url}`));
            document.head.appendChild(script);
        });
    }

    // 全局只加载一次，组件实例共享；成功后才开启 markdown 渲染并重绘已显示的消息。
    const markdownLibraries = Promise.all([loadExternalScript(MARKED_URL), loadExternalScript(DOMPURIFY_URL)])
        .then(() => {
            if (window.marked) window.marked.setOptions({gfm: true, breaks: true});
        })
        .catch(error => {
            console.warn('网站助手 markdown 渲染库加载失败，回答将以纯文本显示', error);
        });

    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, character => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[character]);
    }

    // 模型返回可能含任意 HTML，必须先过 DOMPurify 再进 innerHTML；
    // marked 不可用（CDN 失败或尚未就绪）时退化为转义纯文本，宁可没有排版也不能放行原始 HTML。
    function renderMarkdown(source) {
        if (!window.marked || !window.DOMPurify) return escapeHtml(source).replace(/\n/g, '<br>');
        return window.DOMPurify.sanitize(window.marked.parse(source), {USE_PROFILES: {html: true}});
    }

    // 外部链接强制新窗口打开并加 noopener；代码块补一个复制按钮（复制内容不经过 innerHTML）。
    function decorateCodeBlocks(root) {
        root.querySelectorAll('a').forEach(link => {
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
        });
        root.querySelectorAll('pre').forEach(block => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'thx-ai-code-copy';
            button.textContent = '复制代码';
            button.addEventListener('click', () => copyCode(block.querySelector('code')?.textContent || block.textContent, button));
            block.appendChild(button);
        });
    }

    async function copyCode(text, button) {
        try {
            await navigator.clipboard.writeText(text);
            button.textContent = '已复制';
        } catch (_) {
            button.textContent = '复制失败';
        }
        setTimeout(() => { button.textContent = '复制代码'; }, 1400);
    }

    // 网站助手前端组件：聊天记录和会话编号保存在 localStorage，刷新页面可继续同一会话；
    // 模型记忆也按 conversationId 挂在服务端，因此「新建会话」必须同时换编号并清空本地记录。
    class WebsiteAssistantElement extends HTMLElement {
        constructor() {
            super();
            this.attachShadow({mode: 'open'});
            this.messages = this.readMessages();
            this.conversationId = this.readConversationId();
            this.pending = false;
            this.clearingConversation = false;
            this.reader = null;
            this.themeObserver = null;
            this.closeTimer = null;
            this.assistantTitle = configuredTitle || '网站智能助手';
            this.welcomeMessage = '你好！我可以介绍首页功能，并帮你找到对应入口。';
        }

        connectedCallback() {
            this.renderShell();
            this.bindEvents();
            this.renderMessages();
            this.syncTheme();
            this.installHostLayoutStyle();
            this.loadPublicConfiguration();
            // 渲染库就绪时重绘一次，把库加载完成前已显示的纯文本回答补成排版。
            markdownLibraries.then(() => {
                if (this.isConnected) this.renderMessages();
            });
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
            window.clearTimeout(this.closeTimer);
            document.documentElement.classList.remove('thx-ai-sidebar-open', 'thx-ai-sidebar-closing');
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
                    <span class="thx-ai-launcher-glow" aria-hidden="true"></span>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.5A4 4 0 0112 2a4 4 0 014 3.5M12 2V.8M5.5 8h13A2.5 2.5 0 0121 10.5v7a2.5 2.5 0 01-2.5 2.5h-13A2.5 2.5 0 013 17.5v-7A2.5 2.5 0 015.5 8zM7 13h.01M17 13h.01M8.5 17h7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                    <span class="thx-ai-launcher-label">问助手</span>
                </button>
                <section class="thx-ai-panel" role="dialog" aria-label="网站智能助手" hidden>
                    <header class="thx-ai-header">
                        <div class="thx-ai-identity">
                            <span class="thx-ai-avatar" aria-hidden="true">AI</span>
                            <span>
                            <strong class="thx-ai-title"></strong>
                            <small><i></i>可随时询问站点功能与导航</small>
                            </span>
                        </div>
                        <div class="thx-ai-header-actions">
                            <button class="thx-ai-new" type="button" title="新建会话" aria-label="新建会话">↻</button>
                            <button class="thx-ai-close" type="button" title="关闭" aria-label="关闭助手">×</button>
                        </div>
                    </header>
                    <div class="thx-ai-messages" role="log" aria-live="polite"></div>
                    <div class="thx-ai-quick-questions" aria-label="快捷问题">
                        <button type="button" data-question="这个网站有哪些功能？">了解站点</button>
                        <button type="button" data-question="博客入口在哪里？">寻找博客</button>
                        <button type="button" data-question="怎么搜索站点？">使用搜索</button>
                    </div>
                    <form class="thx-ai-form">
                        <label class="thx-ai-input-label" for="thx-ai-input">询问网站功能</label>
                        <div class="thx-ai-input-row">
                            <textarea id="thx-ai-input" rows="1" maxlength="300" placeholder="询问网站功能或入口…"></textarea>
                            <button class="thx-ai-send" type="submit" aria-label="发送消息"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 3L10 14M21 3l-7 18-4-7-7-4 18-7z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg></button>
                        </div>
                        <p class="thx-ai-hint">AI 可能出错，请以首页实际入口为准</p>
                    </form>
                </section>`;

            this.shadowRoot.replaceChildren(link, wrapper);
            this.launcher = this.shadowRoot.querySelector('.thx-ai-launcher');
            this.panel = this.shadowRoot.querySelector('.thx-ai-panel');
            this.shadowRoot.querySelector('.thx-ai-title').textContent = this.assistantTitle;
            this.launcher.setAttribute('aria-label', `打开${this.assistantTitle}`);
            this.panel.setAttribute('aria-label', this.assistantTitle);
            this.messagesElement = this.shadowRoot.querySelector('.thx-ai-messages');
            this.form = this.shadowRoot.querySelector('.thx-ai-form');
            this.input = this.shadowRoot.querySelector('textarea');
            this.sendButton = this.shadowRoot.querySelector('.thx-ai-send');
            this.quickQuestions = this.shadowRoot.querySelector('.thx-ai-quick-questions');
            this.newButton = this.shadowRoot.querySelector('.thx-ai-new');
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
                if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
                    event.preventDefault();
                    this.form.requestSubmit();
                }
            });
            this.input.addEventListener('input', () => this.resizeInput());
            this.quickQuestions.addEventListener('click', event => {
                const button = event.target.closest('[data-question]');
                if (!button || this.pending) return;
                this.input.value = button.dataset.question || '';
                this.resizeInput();
                this.input.focus();
            });
            document.addEventListener('keydown', event => {
                if (event.key === 'Escape' && !this.panel.hidden) this.close();
            });
        }

        open() {
            window.clearTimeout(this.closeTimer);
            document.documentElement.classList.remove('thx-ai-sidebar-closing');
            this.panel.hidden = false;
            this.launcher.setAttribute('aria-expanded', 'true');
            document.documentElement.classList.add('thx-ai-sidebar-open');
            // 先让浏览器绘制隐藏态，再切到 open，确保首次打开也会执行过渡。
            requestAnimationFrame(() => {
                if (this.launcher.getAttribute('aria-expanded') !== 'true') return;
                this.panel.classList.add('open');
                this.input.focus({preventScroll: true});
            });
        }

        close() {
            if (this.panel.hidden) return;
            window.clearTimeout(this.closeTimer);
            this.panel.classList.remove('open');
            this.launcher.setAttribute('aria-expanded', 'false');
            document.documentElement.classList.remove('thx-ai-sidebar-open');
            document.documentElement.classList.add('thx-ai-sidebar-closing');
            const duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 260;
            this.closeTimer = window.setTimeout(() => {
                this.panel.hidden = true;
                document.documentElement.classList.remove('thx-ai-sidebar-closing');
                this.launcher.focus({preventScroll: true});
            }, duration);
        }

        // 必须先等服务端确认释放旧模型记忆，再清空本地记录并换编号；失败时保留原会话，
        // 防止界面看起来已经重置，服务端却继续积累不可见的历史。
        async startNewConversation() {
            if (this.pending || this.clearingConversation) return;
            this.clearingConversation = true;
            this.updatePendingState();
            try {
                const response = await fetch(
                    `${apiBase}/api/public/v1/website/conversations/${encodeURIComponent(this.conversationId)}`,
                    {method: 'DELETE'}
                );
                if (!response.ok) throw new Error('暂时无法清理旧会话，请稍后重试。');

                this.messages = [];
                this.conversationId = this.createConversationId();
                this.persistState();
                this.renderMessages();
            } catch (error) {
                console.error('网站助手清理会话失败', error);
                this.messages.push({role: 'assistant', content: error.message});
                this.persistState();
                this.renderMessages();
            } finally {
                this.clearingConversation = false;
                this.updatePendingState();
                this.input.focus();
            }
        }

        async sendCurrentMessage() {
            const message = this.input.value.trim();
            if (!message || this.pending) return;

            this.input.value = '';
            this.resizeInput();
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
                assistantMessage.content = error.status === 429
                    ? '今天的访问比较多，请稍后再问，也可以直接使用首页搜索。'
                    : error.message || '网站助手暂时无法连接，请稍后再试。';
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
                const error = new Error(response.status === 429 ? '请求过于频繁' : '网站助手暂时不可用。');
                error.status = response.status;
                throw error;
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
                const badge = document.createElement('span');
                badge.setAttribute('aria-hidden', 'true');
                badge.textContent = '✦';
                const title = document.createElement('strong');
                title.textContent = '你好，需要帮忙吗？';
                const copy = document.createElement('p');
                copy.textContent = this.welcomeMessage;
                empty.append(badge, title, copy);
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
            if (!message.content && this.pending && message === this.messages[this.messages.length - 1]) {
                this.renderThinkingState(element);
            } else if (message.role === 'assistant') {
                this.renderMessageContent(element, message.content);
            } else {
                element.textContent = message.content;
            }
            return element;
        }

        // 助手回答统一走 markdown 渲染，直接在气泡元素上渲染，排版样式见 widget.css 的
        // .thx-ai-message-assistant 系列规则；渲染前必须消毒，模型返回的 HTML 不能直接进页面。
        renderMessageContent(element, content) {
            element.innerHTML = renderMarkdown(content);
            decorateCodeBlocks(element);
        }

        updateLastAssistantMessage(content) {
            const elements = this.messagesElement.querySelectorAll('.thx-ai-message-assistant');
            const last = elements[elements.length - 1];
            if (last && content) {
                last.classList.remove('thx-ai-thinking');
                this.renderMessageContent(last, content);
            } else if (last && this.pending) {
                this.renderThinkingState(last);
            }
            this.scrollToLatest();
        }

        renderThinkingState(element) {
            element.classList.add('thx-ai-thinking');
            const label = document.createElement('span');
            label.textContent = '客官请稍等';
            const dots = document.createElement('span');
            dots.className = 'thx-ai-thinking-dots';
            dots.setAttribute('aria-hidden', 'true');
            dots.append(document.createElement('i'), document.createElement('i'), document.createElement('i'));
            element.replaceChildren(label, dots);
        }

        updatePendingState() {
            const controlsLocked = this.pending || this.clearingConversation;
            this.input.disabled = controlsLocked;
            this.sendButton.disabled = controlsLocked;
            this.newButton.disabled = controlsLocked;
            this.sendButton.classList.toggle('pending', this.pending);
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

        resizeInput() {
            this.input.style.height = 'auto';
            this.input.style.height = `${Math.min(this.input.scrollHeight, 120)}px`;
        }

        // 组件加载的公开配置不包含后台规则和口令；失败时保留内置文案，不阻断对话。
        async loadPublicConfiguration() {
            try {
                const response = await fetch(`${apiBase}/api/public/v1/website/configuration`);
                if (!response.ok) return;
                const configuration = await response.json();
                this.assistantTitle = configuredTitle || configuration.assistantName || this.assistantTitle;
                this.welcomeMessage = configuration.welcomeMessage || this.welcomeMessage;
                this.shadowRoot.querySelector('.thx-ai-title').textContent = this.assistantTitle;
                this.launcher.setAttribute('aria-label', `打开${this.assistantTitle}`);
                this.panel.setAttribute('aria-label', this.assistantTitle);
                this.launcher.hidden = configuration.enabled === false;
                if (configuration.enabled === false && !this.panel.hidden) this.close();
                this.renderMessages();
            } catch (_) {
                // 配置接口不可用时仍允许用户尝试对话，真实开关仍由服务端最终判定。
            }
        }

        // 宿主页面只需加载一个脚本：桌面端打开时页面宽度让给右侧栏，
        // 移动端锁定宿主滚动并由组件全屏接管，避免每个首页单独维护联动 CSS。
        installHostLayoutStyle() {
            if (document.getElementById(hostStyleId)) return;
            const style = document.createElement('style');
            style.id = hostStyleId;
            style.textContent = `
                @media (min-width: 769px) {
                    html body { transition: width 260ms cubic-bezier(.22,1,.36,1) !important; }
                    html body .top-actions { transition: right 260ms cubic-bezier(.22,1,.36,1) !important; }
                    html body #stars, html body #particles { transition: width 260ms cubic-bezier(.22,1,.36,1) !important; }
                    html.thx-ai-sidebar-open body { width: calc(100% - 420px) !important; }
                    html.thx-ai-sidebar-open body .top-actions { right: 442px !important; }
                    html.thx-ai-sidebar-open body #stars,
                    html.thx-ai-sidebar-open body #particles { width: calc(100vw - 420px) !important; }
                }
                @media (max-width: 768px) {
                    html.thx-ai-sidebar-open, html.thx-ai-sidebar-open body,
                    html.thx-ai-sidebar-closing, html.thx-ai-sidebar-closing body { overflow: hidden !important; overscroll-behavior: none; }
                }
            `;
            document.head.appendChild(style);
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
