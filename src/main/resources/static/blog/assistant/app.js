(() => {
    'use strict';

    const TOKEN_KEY = 'ai-platform.blog-access-token';
    const CONVERSATIONS_KEY = 'ai-platform.blog-conversations.v2';
    const MAX_CONVERSATIONS = 20;
    const MAX_MESSAGES = 100;
    const elements = {
        sidebar: document.querySelector('#sidebar'), sidebarOverlay: document.querySelector('#sidebarOverlay'),
        historyList: document.querySelector('#historyList'), chatContainer: document.querySelector('#chatContainer'),
        welcome: document.querySelector('#welcome'), messages: document.querySelector('#messages'),
        input: document.querySelector('#messageInput'), send: document.querySelector('#sendButton'),
        loginOverlay: document.querySelector('#loginOverlay'), loginForm: document.querySelector('#loginForm'),
        accessToken: document.querySelector('#accessToken'), loginError: document.querySelector('#loginError'),
        toast: document.querySelector('#toast')
    };

    let token = sessionStorage.getItem(TOKEN_KEY) || '';
    let conversations = loadConversations();
    if (!conversations.length) conversations.push(createConversation());
    let currentConversationId = conversations[0].id;
    let streaming = false;
    let toastTimer;

    if (window.marked) window.marked.setOptions({ gfm: true, breaks: true });
    bindEvents();
    syncViewportHeight();
    renderAll();
    initializeAuth();

    function bindEvents() {
        window.addEventListener('resize', syncViewportHeight);
        window.visualViewport?.addEventListener('resize', syncViewportHeight);
        document.querySelector('#newChatButton').addEventListener('click', newConversation);
        elements.send.addEventListener('click', sendCurrentMessage);
        document.querySelector('#toggleSidebar').addEventListener('click', () => elements.sidebar.classList.toggle('collapsed'));
        document.querySelector('#mobileMenu').addEventListener('click', openMobileSidebar);
        document.querySelector('#closeSidebar').addEventListener('click', closeMobileSidebar);
        elements.sidebarOverlay.addEventListener('click', closeMobileSidebar);
        document.querySelector('#logoutButton').addEventListener('click', logout);
        document.querySelectorAll('[data-question]').forEach(button => button.addEventListener('click', () => {
            elements.input.value = button.dataset.question || '';
            resizeComposer();
            elements.input.focus();
        }));
        elements.input.addEventListener('input', resizeComposer);
        elements.input.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
                event.preventDefault();
                sendCurrentMessage();
            }
        });
        elements.loginForm.addEventListener('submit', verifyLogin);
    }

    async function initializeAuth() {
        if (!token) return showLogin();
        try {
            await api('/api/blog/v1/auth/verify', { method: 'POST' });
        } catch (error) {
            token = '';
            sessionStorage.removeItem(TOKEN_KEY);
            showLogin(error.message);
        }
    }

    async function verifyLogin(event) {
        event.preventDefault();
        const candidate = elements.accessToken.value.trim();
        if (!candidate) return;
        token = candidate;
        elements.loginError.textContent = '';
        const submit = elements.loginForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        try {
            await api('/api/blog/v1/auth/verify', { method: 'POST' });
            sessionStorage.setItem(TOKEN_KEY, token);
            elements.loginOverlay.hidden = true;
            elements.accessToken.value = '';
            elements.input.focus();
        } catch (error) {
            token = '';
            sessionStorage.removeItem(TOKEN_KEY);
            elements.loginError.textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    }

    function showLogin(message = '') {
        elements.loginOverlay.hidden = false;
        elements.loginError.textContent = message;
        setTimeout(() => elements.accessToken.focus(), 0);
    }

    function logout() {
        token = '';
        sessionStorage.removeItem(TOKEN_KEY);
        showLogin();
    }

    async function sendCurrentMessage() {
        const text = elements.input.value.trim();
        if (!text || streaming) return;
        if (!token) return showLogin();
        const conversation = currentConversation();
        expirePendingActions(conversation);
        conversation.messages.push({ role: 'user', content: text });
        if (conversation.messages.filter(message => message.role === 'user').length === 1) {
            conversation.title = text.length > 24 ? text.slice(0, 24) + '…' : text;
        }
        const assistantMessage = { role: 'assistant', content: '', streaming: true };
        conversation.messages.push(assistantMessage);
        conversation.messages = conversation.messages.slice(-MAX_MESSAGES);
        conversation.updatedAt = Date.now();
        elements.input.value = '';
        resizeComposer();
        streaming = true;
        updateStreamingState();
        renderAll();
        try {
            const response = await fetch('/api/blog/v1/messages', {
                method: 'POST', headers: authHeaders(),
                body: JSON.stringify({ conversationId: conversation.id, message: text })
            });
            if (!response.ok) throw await responseError(response);
            await consumeSse(response, chunk => {
                assistantMessage.content += chunk;
                renderMessages();
            }, action => {
                assistantMessage.action = action;
                renderMessages();
            });
            if (!assistantMessage.content) assistantMessage.content = '抱歉，我暂时没有得到回答，请稍后再试。';
        } catch (error) {
            assistantMessage.content = `请求失败：${error.message}`;
            if (error.status === 401) logout();
        } finally {
            assistantMessage.streaming = false;
            streaming = false;
            conversation.updatedAt = Date.now();
            persistConversations();
            updateStreamingState();
            renderAll();
        }
    }

    async function consumeSse(response, onChunk, onAction) {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let completed = false;
        while (!completed) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() || '';
            for (const event of events) completed = consumeSseEvent(event, onChunk, onAction) || completed;
            if (done) break;
        }
        if (buffer && !completed) consumeSseEvent(buffer, onChunk, onAction);
    }

    function consumeSseEvent(event, onChunk, onAction) {
        const lines = event.split(/\r?\n/);
        const eventName = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message';
        const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).replace(/^ /, '')).join('\n');
        if (!data) return false;
        if (eventName === 'action') {
            try { onAction(JSON.parse(data)); } catch (_) { showToast('发布选项解析失败'); }
            return false;
        }
        if (data.trim() === '[DONE]') return true;
        onChunk(data);
        return false;
    }

    function renderAll() {
        renderHistory();
        renderMessages();
    }

    function renderHistory() {
        elements.historyList.replaceChildren();
        [...conversations].sort((a, b) => b.updatedAt - a.updatedAt).forEach(conversation => {
            const row = document.createElement('div');
            row.className = 'history-item' + (conversation.id === currentConversationId ? ' active' : '');
            row.tabIndex = 0;
            const title = document.createElement('span');
            title.className = 'history-title';
            title.textContent = conversation.title;
            const remove = document.createElement('button');
            remove.className = 'history-delete';
            remove.type = 'button';
            remove.title = '删除本地会话';
            remove.textContent = '×';
            remove.addEventListener('click', event => {
                event.stopPropagation();
                deleteConversation(conversation.id);
            });
            const select = () => {
                if (streaming) return;
                currentConversationId = conversation.id;
                renderAll();
                closeMobileSidebar();
            };
            row.addEventListener('click', select);
            row.addEventListener('keydown', event => { if (event.key === 'Enter') select(); });
            row.append(title, remove);
            elements.historyList.appendChild(row);
        });
    }

    function renderMessages() {
        const conversation = currentConversation();
        const hasMessages = conversation.messages.length > 0;
        elements.chatContainer.classList.toggle('centered', !hasMessages);
        elements.welcome.hidden = hasMessages;
        elements.messages.replaceChildren();
        conversation.messages.forEach(message => elements.messages.appendChild(renderMessage(message)));
        requestAnimationFrame(() => { elements.messages.scrollTop = elements.messages.scrollHeight; });
    }

    function renderMessage(message) {
        const row = document.createElement('article');
        row.className = `message ${message.role}`;
        const stack = document.createElement('div');
        stack.className = 'message-stack';
        const content = document.createElement('div');
        content.className = 'message-content';
        if (message.role === 'assistant') {
            content.classList.add('markdown-body');
            if (message.streaming && !message.content) {
                content.className = 'message-content typing';
                content.textContent = '正在思考';
            } else {
                content.innerHTML = renderMarkdown(message.content || '');
                decorateCodeBlocks(content);
            }
        } else {
            content.textContent = message.content;
        }
        stack.appendChild(content);
        if (message.action) stack.appendChild(renderPublicationAction(message));
        if (!message.streaming && message.content) stack.appendChild(renderMessageActions(message));
        row.appendChild(stack);
        return row;
    }

    function renderMessageActions(message) {
        const actions = document.createElement('div');
        actions.className = 'message-actions';
        const copy = document.createElement('button');
        copy.type = 'button';
        copy.className = 'message-copy';
        copy.title = '复制消息';
        copy.setAttribute('aria-label', '复制消息');
        copy.innerHTML = '<svg viewBox="0 0 24 24" fill="none"><rect x="8" y="8" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2" stroke="currentColor" stroke-width="1.8"/></svg><span>复制</span>';
        copy.addEventListener('click', () => copyText(message.content, copy));
        actions.appendChild(copy);
        return actions;
    }

    function renderPublicationAction(message) {
        const action = message.action;
        const card = document.createElement('section');
        card.className = `publication-action ${String(action.status || '').toLowerCase()}`;
        const heading = document.createElement('div');
        heading.className = 'publication-action-heading';
        const badge = document.createElement('span');
        badge.className = 'publication-action-icon';
        badge.textContent = 'B';
        const text = document.createElement('div');
        const title = document.createElement('h3');
        title.textContent = action.title;
        const meta = document.createElement('p');
        meta.textContent = publicationMeta(action);
        text.append(title, meta);
        heading.append(badge, text);
        card.appendChild(heading);
        if (action.description) {
            const description = document.createElement('p');
            description.className = 'publication-description';
            description.textContent = action.description;
            card.appendChild(description);
        }
        const footer = document.createElement('div');
        footer.className = 'publication-action-footer';
        const status = document.createElement('span');
        status.textContent = publicationStatus(action.status);
        footer.appendChild(status);
        if (action.status === 'PENDING_APPROVAL') {
            footer.append(
                actionButton('取消', 'secondary-action', () => cancelPublication(message)),
                actionButton('发布', 'primary-action', () => approvePublication(message))
            );
        }
        card.appendChild(footer);
        return card;
    }

    function actionButton(label, className, handler) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.textContent = label;
        button.addEventListener('click', handler);
        return button;
    }

    async function approvePublication(message) {
        if (message.action?.status !== 'PENDING_APPROVAL') return;
        message.action.status = 'PROCESSING';
        renderMessages();
        try {
            const result = await api(`/api/blog/v1/publications/${encodeURIComponent(message.action.actionId)}/approve`, { method: 'POST' });
            message.action.status = result.success ? 'PUBLISHED' : 'UNCERTAIN';
            showToast(result.message, 6000);
        } catch (error) {
            message.action.status = 'PENDING_APPROVAL';
            handleApiError(error);
        } finally {
            persistConversations();
            renderMessages();
        }
    }

    async function cancelPublication(message) {
        if (message.action?.status !== 'PENDING_APPROVAL') return;
        try {
            await api(`/api/blog/v1/publications/${encodeURIComponent(message.action.actionId)}`, { method: 'DELETE' });
            message.action.status = 'CANCELLED';
            persistConversations();
            renderMessages();
        } catch (error) {
            handleApiError(error);
        }
    }

    function publicationMeta(action) {
        const parts = [`${action.contentLength || 0} 字符`];
        if (action.categoryId) parts.push(`分类 ${action.categoryId}`);
        if (action.tagIds) parts.push(`标签 ${action.tagIds}`);
        return parts.join(' · ');
    }

    function publicationStatus(status) {
        return ({ PENDING_APPROVAL: '等待你的确认', PROCESSING: '正在发布…', PUBLISHED: '已发布',
            CANCELLED: '已取消', SUPERSEDED: '已失效', UNCERTAIN: '结果不确定，请到博客后台核对' })[status] || status;
    }

    function renderMarkdown(source) {
        if (!window.marked || !window.DOMPurify) return escapeHtml(source).replace(/\n/g, '<br>');
        return window.DOMPurify.sanitize(window.marked.parse(source), { USE_PROFILES: { html: true } });
    }

    function decorateCodeBlocks(root) {
        root.querySelectorAll('a').forEach(link => {
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
        });
        root.querySelectorAll('pre').forEach(block => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'code-copy';
            button.textContent = '复制代码';
            button.addEventListener('click', () => copyText(block.querySelector('code')?.textContent || block.textContent, button));
            block.appendChild(button);
        });
    }

    async function copyText(text, button) {
        try {
            await navigator.clipboard.writeText(text);
            const label = button.querySelector('span');
            if (label) label.textContent = '已复制'; else button.textContent = '已复制';
            setTimeout(() => {
                if (label) label.textContent = '复制'; else button.textContent = '复制代码';
            }, 1400);
        } catch (_) {
            showToast('复制失败，请手动选择内容');
        }
    }

    function newConversation() {
        if (streaming) return;
        const conversation = createConversation();
        conversations.unshift(conversation);
        conversations = conversations.slice(0, MAX_CONVERSATIONS);
        currentConversationId = conversation.id;
        persistConversations();
        renderAll();
        closeMobileSidebar();
        elements.input.focus();
    }

    function deleteConversation(id) {
        if (streaming || conversations.length === 1) return;
        conversations = conversations.filter(conversation => conversation.id !== id);
        if (currentConversationId === id) currentConversationId = conversations[0].id;
        persistConversations();
        renderAll();
    }

    function createConversation() {
        return { id: crypto.randomUUID(), title: '新对话', messages: [], updatedAt: Date.now() };
    }

    function currentConversation() {
        return conversations.find(conversation => conversation.id === currentConversationId) || conversations[0];
    }

    function expirePendingActions(conversation) {
        conversation.messages.forEach(message => {
            if (message.action?.status === 'PENDING_APPROVAL') message.action.status = 'SUPERSEDED';
        });
    }

    function loadConversations() {
        try {
            const value = JSON.parse(localStorage.getItem(CONVERSATIONS_KEY));
            return Array.isArray(value) ? value.slice(0, MAX_CONVERSATIONS) : [];
        } catch (_) { return []; }
    }

    function persistConversations() {
        localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations.slice(0, MAX_CONVERSATIONS)));
    }

    async function api(url, options = {}) {
        const response = await fetch(url, { ...options, headers: { ...authHeaders(), ...(options.headers || {}) } });
        if (!response.ok) throw await responseError(response);
        if (response.status === 204) return null;
        return response.json();
    }

    function authHeaders() {
        return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
    }

    async function responseError(response) {
        let message = `请求失败（HTTP ${response.status}）`;
        try {
            const data = await response.json();
            if (data.message) message = data.message;
        } catch (_) {
            if (response.status === 401) message = '访问口令无效或已更改';
            if (response.status === 503) message = '博客助手访问口令尚未在服务端配置';
        }
        const error = new Error(message);
        error.status = response.status;
        return error;
    }

    function handleApiError(error) {
        if (error.status === 401) logout();
        showToast(error.message, 6000);
    }

    function updateStreamingState() {
        elements.input.disabled = streaming;
        elements.send.disabled = streaming;
    }

    function resizeComposer() {
        elements.input.style.height = 'auto';
        elements.input.style.height = `${Math.min(elements.input.scrollHeight, 200)}px`;
    }

    function syncViewportHeight() {
        const height = window.visualViewport?.height || window.innerHeight;
        document.documentElement.style.setProperty('--app-height', `${Math.round(height)}px`);
    }

    function openMobileSidebar() {
        elements.sidebar.classList.add('active');
        elements.sidebarOverlay.classList.add('active');
    }

    function closeMobileSidebar() {
        elements.sidebar.classList.remove('active');
        elements.sidebarOverlay.classList.remove('active');
    }

    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
    }

    function showToast(message, duration = 3500) {
        clearTimeout(toastTimer);
        elements.toast.textContent = message;
        elements.toast.hidden = false;
        toastTimer = setTimeout(() => { elements.toast.hidden = true; }, duration);
    }
})();
