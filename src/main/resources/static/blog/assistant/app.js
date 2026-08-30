(() => {
    'use strict';

    const TOKEN_KEY = 'ai-platform.blog-access-token';
    const CONVERSATIONS_KEY = 'ai-platform.blog-conversations.v1';
    const MAX_CONVERSATIONS = 20;
    const MAX_MESSAGES = 100;

    const elements = {
        sidebar: document.querySelector('#sidebar'),
        sidebarOverlay: document.querySelector('#sidebarOverlay'),
        historyList: document.querySelector('#historyList'),
        chatContainer: document.querySelector('#chatContainer'),
        welcome: document.querySelector('#welcome'),
        messages: document.querySelector('#messages'),
        input: document.querySelector('#messageInput'),
        send: document.querySelector('#sendButton'),
        loginOverlay: document.querySelector('#loginOverlay'),
        loginForm: document.querySelector('#loginForm'),
        accessToken: document.querySelector('#accessToken'),
        loginError: document.querySelector('#loginError'),
        publishOverlay: document.querySelector('#publishOverlay'),
        publicationForm: document.querySelector('#publicationForm'),
        approvalPanel: document.querySelector('#approvalPanel'),
        approvalConfirmation: document.querySelector('#approvalConfirmation'),
        approvePublication: document.querySelector('#approvePublication'),
        toast: document.querySelector('#toast')
    };

    let token = sessionStorage.getItem(TOKEN_KEY) || '';
    let conversations = loadConversations();
    let currentConversationId = conversations[0]?.id || createConversation().id;
    let streaming = false;
    let pendingPublication = null;
    let toastTimer = null;

    bindEvents();
    syncViewportHeight();
    renderAll();
    initializeAuth();

    function bindEvents() {
        window.addEventListener('resize', syncViewportHeight);
        window.visualViewport?.addEventListener('resize', syncViewportHeight);
        document.querySelector('#newChatButton').addEventListener('click', newConversation);
        document.querySelector('#sendButton').addEventListener('click', sendCurrentMessage);
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
        document.querySelector('#headerPublishButton').addEventListener('click', openPublishDialog);
        document.querySelector('#sidebarPublishButton').addEventListener('click', openPublishDialog);
        document.querySelector('#closePublish').addEventListener('click', closePublishDialog);
        elements.publishOverlay.addEventListener('click', event => {
            if (event.target === elements.publishOverlay) closePublishDialog();
        });
        document.querySelector('#useLatestAnswer').addEventListener('click', useLatestAnswer);
        elements.publicationForm.addEventListener('submit', preparePublication);
        elements.approvalConfirmation.addEventListener('input', () => {
            elements.approvePublication.disabled = elements.approvalConfirmation.value !== '发布';
        });
        elements.approvePublication.addEventListener('click', approvePublication);
        document.querySelector('#backToEdit').addEventListener('click', showPublicationForm);
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && !elements.publishOverlay.hidden) closePublishDialog();
        });
    }

    async function initializeAuth() {
        if (!token) {
            showLogin();
            return;
        }
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
        if (!token) {
            showLogin();
            return;
        }

        const conversation = currentConversation();
        conversation.messages.push({ role: 'user', content: text });
        if (conversation.messages.filter(message => message.role === 'user').length === 1) {
            conversation.title = text.length > 24 ? text.slice(0, 24) + '…' : text;
        }
        const assistantMessage = { role: 'assistant', content: '', streaming: true };
        conversation.messages.push(assistantMessage);
        conversation.updatedAt = Date.now();
        elements.input.value = '';
        resizeComposer();
        streaming = true;
        updateStreamingState();
        renderAll();

        try {
            const response = await fetch('/api/blog/v1/messages', {
                method: 'POST',
                headers: authHeaders(),
                body: JSON.stringify({ conversationId: conversation.id, message: text })
            });
            if (!response.ok) throw await responseError(response);
            await consumeSse(response, chunk => {
                assistantMessage.content += chunk;
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

    async function consumeSse(response, onChunk) {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let completed = false;
        while (!completed) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() || '';
            for (const event of events) completed = consumeSseEvent(event, onChunk) || completed;
            if (done) break;
        }
        if (buffer && !completed) consumeSseEvent(buffer, onChunk);
    }

    function consumeSseEvent(event, onChunk) {
        const data = event.split(/\r?\n/)
            .filter(line => line.startsWith('data:'))
            .map(line => line.slice(5).replace(/^ /, ''))
            .join('\n');
        if (!data) return false;
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
            row.addEventListener('keydown', event => {
                if (event.key === 'Enter') select();
            });
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
        conversation.messages.forEach(message => {
            const row = document.createElement('article');
            row.className = `message ${message.role}`;
            const content = document.createElement('div');
            content.className = 'message-content';
            if (message.role === 'user') {
                content.textContent = message.content;
            } else if (message.streaming && !message.content) {
                content.classList.add('typing');
                content.textContent = '思考中';
            } else {
                content.classList.add('markdown-body');
                content.innerHTML = renderMarkdown(message.content);
            }
            row.appendChild(content);
            elements.messages.appendChild(row);
        });
        requestAnimationFrame(() => { elements.messages.scrollTop = elements.messages.scrollHeight; });
    }

    function renderMarkdown(markdown) {
        const lines = String(markdown || '').replace(/\r\n/g, '\n').split('\n');
        const html = [];
        let code = false;
        let codeLines = [];
        for (const line of lines) {
            if (line.trim().startsWith('```')) {
                if (code) {
                    html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
                    codeLines = [];
                }
                code = !code;
                continue;
            }
            if (code) {
                codeLines.push(line);
                continue;
            }
            if (!line.trim()) continue;
            const heading = line.match(/^(#{1,3})\s+(.+)$/);
            if (heading) {
                const level = heading[1].length;
                html.push(`<h${level}>${inlineMarkdown(heading[2])}</h${level}>`);
            } else if (/^>\s?/.test(line)) {
                html.push(`<blockquote>${inlineMarkdown(line.replace(/^>\s?/, ''))}</blockquote>`);
            } else if (/^[-*]\s+/.test(line)) {
                html.push(`<p>• ${inlineMarkdown(line.replace(/^[-*]\s+/, ''))}</p>`);
            } else if (/^\d+\.\s+/.test(line)) {
                html.push(`<p>${inlineMarkdown(line)}</p>`);
            } else {
                html.push(`<p>${inlineMarkdown(line)}</p>`);
            }
        }
        if (codeLines.length) html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
        return html.join('');
    }

    function inlineMarkdown(text) {
        return escapeHtml(text)
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\[([^\]]+)]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    }

    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, character => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
        })[character]);
    }

    function newConversation() {
        if (streaming) return;
        const conversation = createConversation();
        currentConversationId = conversation.id;
        persistConversations();
        renderAll();
        closeMobileSidebar();
        elements.input.focus();
    }

    function createConversation() {
        const conversation = { id: crypto.randomUUID(), title: '新对话', messages: [], updatedAt: Date.now() };
        conversations.unshift(conversation);
        return conversation;
    }

    function currentConversation() {
        let conversation = conversations.find(item => item.id === currentConversationId);
        if (!conversation) {
            conversation = createConversation();
            currentConversationId = conversation.id;
        }
        return conversation;
    }

    function deleteConversation(id) {
        if (streaming || !window.confirm('只删除当前浏览器中的这段会话记录，确定继续吗？')) return;
        conversations = conversations.filter(item => item.id !== id);
        if (!conversations.length) createConversation();
        if (currentConversationId === id) currentConversationId = conversations[0].id;
        persistConversations();
        renderAll();
    }

    function loadConversations() {
        try {
            const data = JSON.parse(localStorage.getItem(CONVERSATIONS_KEY) || '[]');
            if (!Array.isArray(data)) return [];
            return data.filter(item => item && /^[A-Za-z0-9_-]{1,64}$/.test(item.id) && Array.isArray(item.messages));
        } catch (_) {
            return [];
        }
    }

    function persistConversations() {
        conversations = conversations
            .sort((a, b) => b.updatedAt - a.updatedAt)
            .slice(0, MAX_CONVERSATIONS)
            .map(item => ({ ...item, messages: item.messages.slice(-MAX_MESSAGES) }));
        localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations));
    }

    function openPublishDialog() {
        if (!token) {
            showLogin();
            return;
        }
        showPublicationForm();
        elements.publishOverlay.hidden = false;
        closeMobileSidebar();
        setTimeout(() => document.querySelector('#publicationTitle').focus(), 0);
    }

    function closePublishDialog() {
        elements.publishOverlay.hidden = true;
    }

    function useLatestAnswer() {
        const answer = [...currentConversation().messages].reverse().find(message => message.role === 'assistant' && message.content);
        if (!answer) {
            showToast('当前会话还没有可用的 AI 回复');
            return;
        }
        document.querySelector('#publicationContent').value = answer.content;
        const heading = answer.content.match(/^#\s+(.+)$/m);
        if (heading && !document.querySelector('#publicationTitle').value.trim()) {
            document.querySelector('#publicationTitle').value = heading[1].trim().slice(0, 200);
        }
        showToast('已填入最后一条 AI 回复，请检查后创建审批任务');
    }

    async function preparePublication(event) {
        event.preventDefault();
        const submit = elements.publicationForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        try {
            const categoryValue = document.querySelector('#publicationCategory').value;
            const payload = {
                title: document.querySelector('#publicationTitle').value.trim(),
                contentMd: document.querySelector('#publicationContent').value.trim(),
                categoryId: categoryValue ? Number(categoryValue) : null,
                tagIds: nullableValue('#publicationTags'),
                description: nullableValue('#publicationDescription'),
                keywords: nullableValue('#publicationKeywords'),
                coverImage: nullableValue('#publicationCover'),
                author: nullableValue('#publicationAuthor')
            };
            pendingPublication = await api('/api/blog/v1/publications', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            elements.publicationForm.hidden = true;
            elements.approvalPanel.hidden = false;
            document.querySelector('#approvalTitle').textContent = pendingPublication.title;
            document.querySelector('#approvalSummary').textContent = `正文 ${pendingPublication.contentLength} 字符，审批将在 ${new Date(pendingPublication.expiresAt).toLocaleString()} 过期。`;
            elements.approvalConfirmation.value = '';
            elements.approvePublication.disabled = true;
            elements.approvalConfirmation.focus();
        } catch (error) {
            handleApiError(error);
        } finally {
            submit.disabled = false;
        }
    }

    async function approvePublication() {
        if (!pendingPublication) return;
        elements.approvePublication.disabled = true;
        try {
            const result = await api(`/api/blog/v1/publications/${encodeURIComponent(pendingPublication.actionId)}/approve`, {
                method: 'POST',
                body: JSON.stringify({ confirmation: elements.approvalConfirmation.value })
            });
            closePublishDialog();
            showToast(result.message, 6000);
            if (result.success) elements.publicationForm.reset();
            pendingPublication = null;
        } catch (error) {
            handleApiError(error);
            elements.approvePublication.disabled = false;
        }
    }

    function showPublicationForm() {
        pendingPublication = null;
        elements.publicationForm.hidden = false;
        elements.approvalPanel.hidden = true;
        elements.approvalConfirmation.value = '';
    }

    async function api(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: { ...authHeaders(), ...(options.headers || {}) }
        });
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

    function nullableValue(selector) {
        const value = document.querySelector(selector).value.trim();
        return value || null;
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

    function showToast(message, duration = 3500) {
        clearTimeout(toastTimer);
        elements.toast.textContent = message;
        elements.toast.hidden = false;
        toastTimer = setTimeout(() => { elements.toast.hidden = true; }, duration);
    }
})();
