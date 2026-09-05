(() => {
    'use strict';

    // 口令只存当前标签页，关闭后自动清理，避免后台凭据长期留在浏览器。
    const TOKEN_KEY = 'ai-platform.website-access-token';
    const state = { token: sessionStorage.getItem(TOKEN_KEY) || '', entries: [], settings: null, filter: 'ALL', query: '', editingId: null };
    const $ = selector => document.querySelector(selector);
    const elements = {
        loginOverlay: $('#loginOverlay'), loginForm: $('#loginForm'), accessToken: $('#accessToken'), loginError: $('#loginError'),
        knowledgeList: $('#knowledgeList'), emptyState: $('#emptyState'), searchInput: $('#searchInput'), entryModal: $('#entryModal'),
        entryForm: $('#entryForm'), addEntry: $('#addEntryButton'), deleteEntry: $('#deleteEntryButton'), toast: $('#toast'),
        settingsForm: $('#settingsForm'), sidebar: $('.sidebar'), sidebarOverlay: $('#sidebarOverlay')
    };
    let toastTimer;

    bindEvents();
    initialize();

    function bindEvents() {
        elements.loginForm.addEventListener('submit', login);
        $('#logoutButton').addEventListener('click', logout);
        elements.addEntry.addEventListener('click', () => openEntry());
        $('#closeEntryModal').addEventListener('click', closeEntry);
        $('#cancelEntryButton').addEventListener('click', closeEntry);
        elements.entryModal.addEventListener('click', event => { if (event.target === elements.entryModal) closeEntry(); });
        elements.entryForm.addEventListener('submit', saveEntry);
        elements.deleteEntry.addEventListener('click', deleteEntry);
        elements.searchInput.addEventListener('input', event => { state.query = event.target.value.trim().toLowerCase(); renderEntries(); });
        document.querySelectorAll('.filter').forEach(button => button.addEventListener('click', () => selectFilter(button)));
        document.querySelectorAll('.nav-item').forEach(button => button.addEventListener('click', () => selectView(button.dataset.view)));
        document.querySelectorAll('input[name="entryType"]').forEach(input => input.addEventListener('change', syncEntryType));
        $('#welcomeMessage').addEventListener('input', syncWelcomeLength);
        elements.settingsForm.addEventListener('submit', saveSettings);
        $('#mobileMenu').addEventListener('click', openSidebar);
        elements.sidebarOverlay.addEventListener('click', closeSidebar);
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && !elements.entryModal.hidden) closeEntry();
        });
    }

    async function initialize() {
        if (!state.token) return showLogin();
        try {
            await api('/api/website/v1/auth/verify', { method: 'POST' });
            await loadData();
        } catch (error) {
            if (error.status === 401 || error.status === 503) logout(error.message);
            else showToast(error.message);
        }
    }

    async function login(event) {
        event.preventDefault();
        const candidate = elements.accessToken.value.trim();
        if (!candidate) return;
        state.token = candidate;
        elements.loginError.textContent = '';
        const button = elements.loginForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            await api('/api/website/v1/auth/verify', { method: 'POST' });
            sessionStorage.setItem(TOKEN_KEY, state.token);
            elements.loginOverlay.hidden = true;
            elements.accessToken.value = '';
            await loadData();
        } catch (error) {
            state.token = '';
            sessionStorage.removeItem(TOKEN_KEY);
            elements.loginError.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    }

    function showLogin(message = '') {
        elements.loginOverlay.hidden = false;
        elements.loginError.textContent = message;
        setTimeout(() => elements.accessToken.focus(), 0);
    }

    function logout(message = '') {
        state.token = '';
        sessionStorage.removeItem(TOKEN_KEY);
        showLogin(message);
    }

    async function loadData() {
        const [entries, settings] = await Promise.all([
            api('/api/website/v1/knowledge'),
            api('/api/website/v1/settings')
        ]);
        state.entries = entries;
        state.settings = settings;
        renderEntries();
        renderSettings();
    }

    function renderEntries() {
        const entries = state.entries.filter(entry => {
            if (state.filter !== 'ALL' && entry.entryType !== state.filter) return false;
            if (!state.query) return true;
            return [entry.title, entry.question, entry.content, entry.keywords]
                .some(value => (value || '').toLowerCase().includes(state.query));
        });
        elements.knowledgeList.replaceChildren(...entries.map(entryCard));
        elements.emptyState.hidden = entries.length > 0;
        $('#knowledgeCount').textContent = state.entries.length;
        $('#allCount').textContent = state.entries.length;
        $('#enabledCount').textContent = state.entries.filter(entry => entry.enabled).length;
        $('#faqCount').textContent = state.entries.filter(entry => entry.entryType === 'FAQ').length;
    }

    function entryCard(entry) {
        const card = document.createElement('article');
        card.className = 'knowledge-card';
        const icon = document.createElement('span');
        icon.className = `entry-icon ${entry.entryType === 'FAQ' ? 'faq' : ''}`;
        icon.textContent = entry.entryType === 'FAQ' ? 'FAQ' : '资料';

        const main = document.createElement('div');
        main.className = 'entry-main';
        const titleRow = document.createElement('div');
        titleRow.className = 'entry-title-row';
        const title = document.createElement('h3');
        title.textContent = entry.title;
        const stateBadge = document.createElement('span');
        stateBadge.className = `entry-state ${entry.enabled ? '' : 'off'}`;
        stateBadge.textContent = entry.enabled ? '已启用' : '已停用';
        titleRow.append(title, stateBadge);
        const content = document.createElement('p');
        content.textContent = entry.entryType === 'FAQ' && entry.question ? `问：${entry.question}  答：${entry.content}` : entry.content;
        main.append(titleRow, content);

        const meta = document.createElement('div');
        meta.className = 'entry-meta';
        const priority = document.createElement('span');
        priority.textContent = `优先级 ${entry.priority}`;
        const edit = document.createElement('button');
        edit.className = 'edit-entry';
        edit.type = 'button';
        edit.title = '编辑';
        edit.setAttribute('aria-label', `编辑${entry.title}`);
        edit.textContent = '✎';
        edit.addEventListener('click', () => openEntry(entry));
        meta.append(priority, edit);
        card.append(icon, main, meta);
        return card;
    }

    function selectFilter(button) {
        state.filter = button.dataset.filter;
        document.querySelectorAll('.filter').forEach(item => item.classList.toggle('active', item === button));
        renderEntries();
    }

    function selectView(view) {
        const titles = {
            knowledge: ['知识库', '管理助手可用于回答的网站资料'],
            settings: ['助手设置', '调整对外展示、开关和站点专属规则'],
            guide: ['检索说明', '了解当前轻量 RAG 的工作方式和升级时机']
        };
        document.querySelectorAll('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.view === view));
        document.querySelectorAll('.view').forEach(item => item.classList.toggle('active', item.id === `${view}View`));
        $('#viewTitle').textContent = titles[view][0];
        $('#viewSubtitle').textContent = titles[view][1];
        elements.addEntry.hidden = view !== 'knowledge';
        closeSidebar();
    }

    function openEntry(entry = null) {
        state.editingId = entry?.id ?? null;
        elements.entryForm.reset();
        const type = entry?.entryType || 'INFO';
        document.querySelector(`input[name="entryType"][value="${type}"]`).checked = true;
        $('#entryName').value = entry?.title || '';
        $('#entryQuestion').value = entry?.question || '';
        $('#entryContent').value = entry?.content || '';
        $('#entryKeywords').value = entry?.keywords || '';
        $('#entryPriority').value = entry?.priority ?? 50;
        $('#entryEnabled').checked = entry?.enabled ?? true;
        $('#entryEyebrow').textContent = entry ? '编辑知识' : '新建知识';
        $('#entryTitle').textContent = entry ? entry.title : '添加网站资料';
        elements.deleteEntry.hidden = !entry;
        syncEntryType();
        elements.entryModal.hidden = false;
        setTimeout(() => $('#entryName').focus(), 0);
    }

    function closeEntry() {
        elements.entryModal.hidden = true;
        state.editingId = null;
    }

    function syncEntryType() {
        const faq = document.querySelector('input[name="entryType"]:checked').value === 'FAQ';
        $('#questionField').hidden = !faq;
        $('#entryQuestion').required = faq;
        $('#contentLabel').textContent = faq ? '标准答案' : '资料内容';
        $('#entryTitle').textContent = state.editingId
            ? $('#entryName').value || '编辑知识'
            : faq ? '添加常见问答' : '添加网站资料';
    }

    async function saveEntry(event) {
        event.preventDefault();
        const payload = entryPayload();
        const button = elements.entryForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const path = state.editingId ? `/api/website/v1/knowledge/${state.editingId}` : '/api/website/v1/knowledge';
            const saved = await api(path, { method: state.editingId ? 'PUT' : 'POST', body: JSON.stringify(payload) });
            const index = state.entries.findIndex(entry => entry.id === saved.id);
            if (index >= 0) state.entries[index] = saved;
            else state.entries.unshift(saved);
            renderEntries();
            closeEntry();
            showToast('知识已保存，下一次问答即可生效');
        } catch (error) {
            showToast(error.message);
        } finally {
            button.disabled = false;
        }
    }

    function entryPayload() {
        return {
            entryType: document.querySelector('input[name="entryType"]:checked').value,
            title: $('#entryName').value.trim(), question: $('#entryQuestion').value.trim(),
            content: $('#entryContent').value.trim(), keywords: $('#entryKeywords').value.trim(),
            enabled: $('#entryEnabled').checked, priority: Number($('#entryPriority').value)
        };
    }

    async function deleteEntry() {
        const entry = state.entries.find(item => item.id === state.editingId);
        if (!entry || !window.confirm(`确定删除「${entry.title}」？此操作无法撤销。`)) return;
        elements.deleteEntry.disabled = true;
        try {
            await api(`/api/website/v1/knowledge/${entry.id}`, { method: 'DELETE' });
            state.entries = state.entries.filter(item => item.id !== entry.id);
            renderEntries();
            closeEntry();
            showToast('知识条目已删除');
        } catch (error) {
            showToast(error.message);
        } finally {
            elements.deleteEntry.disabled = false;
        }
    }

    function renderSettings() {
        if (!state.settings) return;
        $('#assistantName').value = state.settings.assistantName;
        $('#welcomeMessage').value = state.settings.welcomeMessage;
        $('#promptAddition').value = state.settings.promptAddition;
        $('#assistantEnabled').checked = state.settings.enabled;
        $('#settingsTime').textContent = `上次更新：${formatTime(state.settings.updatedAt)}`;
        const status = $('#serviceStatus');
        status.className = `status-pill ${state.settings.enabled ? 'enabled' : 'disabled'}`;
        status.lastChild.textContent = state.settings.enabled ? '对外服务中' : '已暂停对外服务';
        syncWelcomeLength();
    }

    async function saveSettings(event) {
        event.preventDefault();
        const button = elements.settingsForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            state.settings = await api('/api/website/v1/settings', {
                method: 'PUT', body: JSON.stringify({
                    assistantName: $('#assistantName').value.trim(), welcomeMessage: $('#welcomeMessage').value.trim(),
                    promptAddition: $('#promptAddition').value.trim(), enabled: $('#assistantEnabled').checked
                })
            });
            renderSettings();
            showToast('助手设置已保存');
        } catch (error) {
            showToast(error.message);
        } finally {
            button.disabled = false;
        }
    }

    function syncWelcomeLength() { $('#welcomeLength').textContent = $('#welcomeMessage').value.length; }
    function openSidebar() { elements.sidebar.classList.add('active'); elements.sidebarOverlay.classList.add('active'); }
    function closeSidebar() { elements.sidebar.classList.remove('active'); elements.sidebarOverlay.classList.remove('active'); }

    async function api(path, options = {}) {
        const response = await fetch(path, {
            ...options,
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${state.token}`, ...(options.headers || {}) }
        });
        if (!response.ok) {
            let message = response.status === 401 ? '访问口令无效或已更改' : `请求失败（${response.status}）`;
            try { const data = await response.json(); message = data.message || data.error || message; } catch (_) { /* sendError 可能返回 HTML */ }
            const error = new Error(message); error.status = response.status; throw error;
        }
        if (response.status === 204) return null;
        return response.json();
    }

    function formatTime(value) {
        if (!value) return '未知';
        return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
    }

    function showToast(message) {
        clearTimeout(toastTimer);
        elements.toast.textContent = message;
        elements.toast.hidden = false;
        toastTimer = setTimeout(() => { elements.toast.hidden = true; }, 2800);
    }
})();
