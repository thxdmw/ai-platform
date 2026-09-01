(() => {
    'use strict';

    const TOKEN_KEY = 'ai-platform.server-access-token';
    const CONVERSATIONS_KEY = 'ai-platform.server-conversations.v1';
    const MAX_CONVERSATIONS = 20;
    const MAX_MESSAGES = 100;
    const elements = {
        sidebar: document.querySelector('#sidebar'), sidebarOverlay: document.querySelector('#sidebarOverlay'),
        serverList: document.querySelector('#serverList'), historyList: document.querySelector('#historyList'),
        chatContainer: document.querySelector('#chatContainer'), welcome: document.querySelector('#welcome'),
        messages: document.querySelector('#messages'), input: document.querySelector('#messageInput'),
        send: document.querySelector('#sendButton'), loginOverlay: document.querySelector('#loginOverlay'),
        loginForm: document.querySelector('#loginForm'), accessToken: document.querySelector('#accessToken'),
        loginError: document.querySelector('#loginError'), toast: document.querySelector('#toast'),
        serverSelectButton: document.querySelector('#serverSelectButton'), serverSelectLabel: document.querySelector('#serverSelectLabel'),
        serverSelectMenu: document.querySelector('#serverSelectMenu'), currentServerMeta: document.querySelector('#currentServerMeta'),
        conversationBulkActions: document.querySelector('#conversationBulkActions'),
        selectAllConversations: document.querySelector('#selectAllConversations'),
        selectedConversationCount: document.querySelector('#selectedConversationCount'),
        deleteSelectedConversations: document.querySelector('#deleteSelectedConversations'),
        settingsOverlay: document.querySelector('#settingsOverlay'), settingsServerList: document.querySelector('#settingsServerList'),
        serverForm: document.querySelector('#serverForm'), commandSection: document.querySelector('#commandSection'),
        commandList: document.querySelector('#commandList'), commandForm: document.querySelector('#commandForm')
    };

    let token = sessionStorage.getItem(TOKEN_KEY) || '';
    let servers = [];
    let commands = [];
    let editingServerId = null;
    let editingCommandId = null;
    let conversations = loadConversations();
    if (!conversations.length) conversations.push(createConversation());
    let currentConversationId = conversations[0].id;
    let streaming = false;
    let managingConversations = false;
    const selectedConversationIds = new Set();
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
        document.querySelector('#toggleSidebar').addEventListener('click', toggleSidebar);
        document.querySelector('#mobileMenu').addEventListener('click', openMobileSidebar);
        document.querySelector('#closeSidebar').addEventListener('click', closeMobileSidebar);
        document.querySelector('#manageConversationsButton').addEventListener('click', () => setConversationManagement(true));
        document.querySelector('#cancelConversationManagement').addEventListener('click', () => setConversationManagement(false));
        elements.selectAllConversations.addEventListener('change', toggleAllConversations);
        elements.deleteSelectedConversations.addEventListener('click', deleteSelectedConversations);
        document.querySelector('#logoutButton').addEventListener('click', logout);
        document.querySelector('#openSettingsButton').addEventListener('click', openSettings);
        document.querySelector('#closeSettingsButton').addEventListener('click', closeSettings);
        document.querySelector('#addServerButton').addEventListener('click', () => editServer(null));
        document.querySelector('#installDefaultCommandsButton').addEventListener('click', installDefaultCommands);
        document.querySelector('#addCommandButton').addEventListener('click', () => editCommand(null));
        document.querySelector('#cancelCommandButton').addEventListener('click', () => { elements.commandForm.hidden = true; });
        document.querySelector('#deleteServerButton').addEventListener('click', deleteServer);
        document.querySelector('#testServerButton').addEventListener('click', testServer);
        document.querySelector('#deleteCommandButton').addEventListener('click', deleteCommand);
        document.querySelector('#serverAuthType').addEventListener('change', syncAuthenticationFields);
        elements.serverForm.addEventListener('submit', saveServer);
        elements.commandForm.addEventListener('submit', saveCommand);
        elements.serverSelectButton.addEventListener('click', toggleServerMenu);
        document.addEventListener('click', event => {
            if (!event.target.closest('.server-picker')) closeServerMenu();
        });
        document.addEventListener('keydown', event => { if (event.key === 'Escape') closeServerMenu(); });
        elements.sidebarOverlay.addEventListener('click', closeMobileSidebar);
        document.querySelectorAll('[data-question]').forEach(button => button.addEventListener('click', () => {
            elements.input.value = button.dataset.question || '';
            resizeComposer(); elements.input.focus();
        }));
        elements.input.addEventListener('input', resizeComposer);
        elements.input.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
                event.preventDefault(); sendCurrentMessage();
            }
        });
        elements.loginForm.addEventListener('submit', verifyLogin);
    }

    async function initializeAuth() {
        if (!token) return showLogin();
        try {
            await api('/api/server/v1/auth/verify', { method: 'POST' });
            await loadServers();
        } catch (error) {
            token = ''; sessionStorage.removeItem(TOKEN_KEY); showLogin(error.message);
        }
    }

    async function verifyLogin(event) {
        event.preventDefault();
        const candidate = elements.accessToken.value.trim();
        if (!candidate) return;
        token = candidate; elements.loginError.textContent = '';
        const submit = elements.loginForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        try {
            await api('/api/server/v1/auth/verify', { method: 'POST' });
            sessionStorage.setItem(TOKEN_KEY, token);
            elements.loginOverlay.hidden = true; elements.accessToken.value = '';
            await loadServers(); elements.input.focus();
        } catch (error) {
            token = ''; sessionStorage.removeItem(TOKEN_KEY); elements.loginError.textContent = error.message;
        } finally { submit.disabled = false; }
    }

    async function loadServers() {
        servers = await api('/api/server/v1/servers');
        elements.serverList.replaceChildren();
        renderServerMenu();
        if (!servers.length) {
            const empty = document.createElement('p'); empty.className = 'empty-note';
            empty.textContent = '尚未配置服务器'; elements.serverList.appendChild(empty);
            ensureConversationServer(); renderAll(); return;
        }
        servers.forEach(server => {
            const item = document.createElement('button'); item.type = 'button';
            item.className = 'server-item' + (!server.enabled ? ' disabled' : '');
            const name = document.createElement('strong');
            const dot = document.createElement('span'); dot.className = 'server-dot';
            name.append(dot, document.createTextNode(server.name));
            const address = document.createElement('span');
            address.textContent = `${server.username}@${server.host}:${server.port} · ${authLabel(server.authenticationType)}`;
            item.addEventListener('click', () => { if (server.enabled) selectServer(server.id); });
            item.append(name, address); elements.serverList.appendChild(item);
        });
        ensureConversationServer();
        renderAll();
    }

    async function openSettings() {
        elements.settingsOverlay.hidden = false;
        renderSettingsServerList();
        const preferred = editingServerId || currentConversation().serverId || servers[0]?.id || null;
        await editServer(preferred);
        document.querySelector('.settings-main').scrollTop = 0;
        document.querySelector('.settings-layout').scrollTop = 0;
    }

    function closeSettings() { elements.settingsOverlay.hidden = true; }

    function renderSettingsServerList() {
        elements.settingsServerList.replaceChildren();
        if (!servers.length) {
            const empty = document.createElement('p'); empty.className = 'empty-note'; empty.textContent = '还没有服务器配置';
            elements.settingsServerList.appendChild(empty); return;
        }
        servers.forEach(server => {
            const button = document.createElement('button'); button.type = 'button';
            button.className = 'settings-server-option' + (server.id === editingServerId ? ' active' : '');
            const name = document.createElement('strong'); name.textContent = server.name + (server.enabled ? '' : '（停用）');
            const address = document.createElement('span'); address.textContent = `${server.host}:${server.port}`;
            button.append(name, address); button.addEventListener('click', () => editServer(server.id));
            elements.settingsServerList.appendChild(button);
        });
    }

    async function editServer(serverId) {
        editingServerId = serverId;
        editingCommandId = null;
        const server = servers.find(value => value.id === serverId);
        elements.serverForm.reset();
        field('serverId').value = server?.id || '';
        field('serverName').value = server?.name || '';
        field('serverHost').value = server?.host || '';
        field('serverPort').value = server?.port || 22;
        field('serverUsername').value = server?.username || '';
        field('serverAuthType').value = server?.authenticationType || 'PASSWORD';
        field('serverCredential').value = '';
        field('serverPassphrase').value = '';
        field('serverHostKey').value = server?.hostKey || '';
        field('serverEnabled').checked = server?.enabled ?? true;
        field('serverFormHint').textContent = server
            ? '凭据留空表示继续使用已加密保存的值' : '新增服务器时请填写完整凭据';
        field('serverFormError').textContent = '';
        field('deleteServerButton').hidden = !server;
        field('testServerButton').hidden = !server;
        elements.commandSection.hidden = !server;
        elements.commandForm.hidden = true;
        syncAuthenticationFields();
        renderSettingsServerList();
        if (server) await loadCommands(server.id); else { commands = []; renderCommands(); }
        document.querySelector('.settings-main').scrollTop = 0;
        document.querySelector('.settings-layout').scrollTop = 0;
    }

    function syncAuthenticationFields() {
        const privateKey = field('serverAuthType').value === 'PRIVATE_KEY';
        field('credentialLabel').textContent = privateKey ? 'SSH 私钥内容' : 'SSH 密码';
        field('serverCredential').rows = privateKey ? 8 : 3;
        field('serverCredential').placeholder = privateKey
            ? '粘贴 PEM/OpenSSH 私钥；编辑时留空表示不修改' : '编辑时留空表示不修改';
        field('passphraseField').hidden = !privateKey;
    }

    async function saveServer(event) {
        event.preventDefault(); field('serverFormError').textContent = '';
        const payload = {
            name: field('serverName').value.trim(), host: field('serverHost').value.trim(),
            port: Number(field('serverPort').value), username: field('serverUsername').value.trim(),
            authenticationType: field('serverAuthType').value,
            credential: field('serverCredential').value || null,
            privateKeyPassphrase: field('serverPassphrase').value || null,
            hostKey: field('serverHostKey').value.trim(), enabled: field('serverEnabled').checked
        };
        const submit = elements.serverForm.querySelector('button[type="submit"]'); submit.disabled = true;
        try {
            const saved = await api(editingServerId ? `/api/server/v1/servers/${editingServerId}` : '/api/server/v1/servers', {
                method: editingServerId ? 'PUT' : 'POST', body: JSON.stringify(payload)
            });
            await loadServers(); await editServer(saved.id); showToast('服务器配置已保存');
        } catch (error) { field('serverFormError').textContent = error.message; }
        finally { submit.disabled = false; }
    }

    async function deleteServer() {
        const server = servers.find(value => value.id === editingServerId);
        if (!server || !window.confirm(`确定删除“${server.name}”及其全部命令吗？`)) return;
        try {
            await api(`/api/server/v1/servers/${server.id}`, { method: 'DELETE' });
            editingServerId = null; await loadServers(); await editServer(servers[0]?.id || null); showToast('服务器已删除');
        } catch (error) { field('serverFormError').textContent = error.message; }
    }

    async function testServer() {
        if (!editingServerId) return;
        const button = field('testServerButton'); button.disabled = true;
        try {
            const result = await api(`/api/server/v1/servers/${editingServerId}/test`, { method: 'POST' });
            showToast(result.message, 5000);
        } catch (error) { field('serverFormError').textContent = error.message; }
        finally { button.disabled = false; }
    }

    async function loadCommands(serverId) {
        commands = await api(`/api/server/v1/servers/${serverId}/commands`); renderCommands();
    }

    async function installDefaultCommands() {
        if (!editingServerId) return;
        const button = field('installDefaultCommandsButton');
        const previousCount = commands.length;
        button.disabled = true;
        try {
            commands = await api(`/api/server/v1/servers/${editingServerId}/commands/defaults`, { method: 'POST' });
            renderCommands();
            const addedCount = commands.length - previousCount;
            showToast(addedCount ? `已补充 ${addedCount} 个常用只读命令` : '常用只读命令已经齐全');
        } catch (error) { field('commandFormError').textContent = error.message; }
        finally { button.disabled = false; }
    }

    function renderCommands() {
        elements.commandList.replaceChildren();
        if (!commands.length) {
            const empty = document.createElement('p'); empty.className = 'empty-note';
            empty.textContent = editingServerId ? '尚未配置命令，可一键补充常用只读命令。' : '请先保存服务器';
            elements.commandList.appendChild(empty); return;
        }
        commands.forEach(command => {
            const item = document.createElement('button'); item.type = 'button'; item.className = 'command-item';
            const main = document.createElement('span'); main.className = 'command-item-main';
            const name = document.createElement('strong'); name.textContent = command.name + (command.enabled ? '' : '（停用）');
            const description = document.createElement('span'); description.textContent = command.description;
            main.append(name, description);
            const risk = document.createElement('span'); risk.className = 'risk-badge' + (command.riskLevel === 'DANGEROUS' ? ' dangerous' : '');
            risk.textContent = command.riskLevel === 'DANGEROUS' ? '需要确认' : '直接执行';
            item.append(main, risk); item.addEventListener('click', () => editCommand(command.id));
            elements.commandList.appendChild(item);
        });
    }

    function editCommand(commandId) {
        editingCommandId = commandId;
        const command = commands.find(value => value.id === commandId);
        elements.commandForm.reset(); elements.commandForm.hidden = false;
        field('commandId').value = command?.id || '';
        field('commandName').value = command?.name || '';
        field('commandDescription').value = command?.description || '';
        field('commandText').value = command?.commandText || '';
        field('commandRisk').value = command?.riskLevel || 'NORMAL';
        field('commandSortOrder').value = command?.sortOrder || 0;
        field('commandEnabled').checked = command?.enabled ?? true;
        field('commandFormError').textContent = '';
        field('deleteCommandButton').hidden = !command;
        field('commandName').focus();
    }

    async function saveCommand(event) {
        event.preventDefault(); field('commandFormError').textContent = '';
        const payload = {
            name: field('commandName').value.trim(), description: field('commandDescription').value.trim(),
            commandText: field('commandText').value.trim(), riskLevel: field('commandRisk').value,
            sortOrder: Number(field('commandSortOrder').value || 0), enabled: field('commandEnabled').checked
        };
        try {
            await api(editingCommandId ? `/api/server/v1/commands/${editingCommandId}`
                : `/api/server/v1/servers/${editingServerId}/commands`, {
                method: editingCommandId ? 'PUT' : 'POST', body: JSON.stringify(payload)
            });
            await loadCommands(editingServerId); elements.commandForm.hidden = true; showToast('命令配置已保存');
        } catch (error) { field('commandFormError').textContent = error.message; }
    }

    async function deleteCommand() {
        const command = commands.find(value => value.id === editingCommandId);
        if (!command || !window.confirm(`确定删除命令“${command.name}”吗？`)) return;
        try {
            await api(`/api/server/v1/commands/${command.id}`, { method: 'DELETE' });
            await loadCommands(editingServerId); elements.commandForm.hidden = true; showToast('命令已删除');
        } catch (error) { field('commandFormError').textContent = error.message; }
    }

    function showLogin(message = '') {
        elements.loginOverlay.hidden = false; elements.loginError.textContent = message;
        setTimeout(() => elements.accessToken.focus(), 0);
    }

    function logout() { token = ''; sessionStorage.removeItem(TOKEN_KEY); showLogin(); }

    async function sendCurrentMessage() {
        const text = elements.input.value.trim();
        if (!text || streaming) return;
        if (!token) return showLogin();
        const conversation = currentConversation();
        const server = servers.find(value => value.id === conversation.serverId && value.enabled);
        if (!server) { showToast('请先为当前对话选择一台已启用的服务器'); return; }
        expireActions(conversation);
        conversation.messages.push({ role: 'user', content: text });
        if (conversation.messages.filter(message => message.role === 'user').length === 1) {
            conversation.title = text.length > 24 ? text.slice(0, 24) + '…' : text;
        }
        const assistantMessage = { role: 'assistant', content: '', streaming: true };
        conversation.messages.push(assistantMessage);
        conversation.messages = conversation.messages.slice(-MAX_MESSAGES);
        conversation.updatedAt = Date.now(); elements.input.value = ''; resizeComposer();
        streaming = true; updateStreamingState(); renderAll();
        try {
            const response = await fetch('/api/server/v1/messages', {
                method: 'POST', headers: authHeaders(),
                body: JSON.stringify({ conversationId: conversation.id, serverId: conversation.serverId, message: text })
            });
            if (!response.ok) throw await responseError(response);
            await consumeSse(response, chunk => {
                assistantMessage.content += chunk; renderMessages();
            }, action => {
                assistantMessage.action = action; renderMessages();
            });
            if (!assistantMessage.content) assistantMessage.content = '暂时没有得到回答，请稍后再试。';
        } catch (error) {
            assistantMessage.content = `请求失败：${error.message}`;
            if (error.status === 401) logout();
        } finally {
            assistantMessage.streaming = false; streaming = false; conversation.updatedAt = Date.now();
            persistConversations(); updateStreamingState(); renderAll();
        }
    }

    async function consumeSse(response, onChunk, onAction) {
        const reader = response.body.getReader(); const decoder = new TextDecoder();
        let buffer = ''; let completed = false;
        while (!completed) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
            const events = buffer.split(/\r?\n\r?\n/); buffer = events.pop() || '';
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
        if (eventName === 'action') { try { onAction(JSON.parse(data)); } catch (_) { showToast('操作选项解析失败'); } return false; }
        if (data.trim() === '[DONE]') return true;
        onChunk(data); return false;
    }

    function renderAll() { renderHistory(); renderServerSelection(); renderMessages(); updateStreamingState(); }

    function ensureConversationServer() {
        const conversation = currentConversation();
        const enabled = servers.filter(server => server.enabled);
        if (!conversation.serverId && enabled.length) conversation.serverId = enabled[0].id;
        if (conversation.serverId && !enabled.some(server => server.id === conversation.serverId) && !conversation.messages.length) {
            conversation.serverId = enabled[0]?.id || null;
        }
        persistConversations();
    }

    function selectServer(serverId) {
        if (streaming) return;
        const server = servers.find(value => value.id === serverId && value.enabled);
        if (!server) return;
        closeServerMenu();
        const conversation = currentConversation();
        if (conversation.serverId === server.id) { closeMobileSidebar(); return; }
        if (conversation.messages.length) {
            const next = createConversation(server.id); conversations.unshift(next); currentConversationId = next.id;
            conversations = conversations.slice(0, MAX_CONVERSATIONS); showToast(`已新建与“${server.name}”的对话`);
        } else conversation.serverId = server.id;
        persistConversations(); renderAll(); closeMobileSidebar();
    }

    function renderServerSelection() {
        const conversation = currentConversation();
        const server = servers.find(value => value.id === conversation.serverId);
        elements.serverSelectLabel.textContent = server?.enabled ? server.name : '请选择服务器';
        elements.serverSelectButton.classList.toggle('has-value', Boolean(server?.enabled));
        elements.currentServerMeta.textContent = server
            ? `${server.name} · ${server.username}@${server.host}:${server.port}` : '请选择本次对话使用的服务器';
        elements.serverSelectMenu.querySelectorAll('.server-select-option').forEach(option => {
            const selected = option.dataset.serverId === server?.id;
            option.classList.toggle('selected', selected);
            option.setAttribute('aria-selected', String(selected));
        });
        elements.serverList.querySelectorAll('.server-item').forEach((item, index) => {
            item.classList.toggle('selected', servers[index]?.id === server?.id);
        });
    }

    function renderServerMenu() {
        elements.serverSelectMenu.replaceChildren();
        const enabledServers = servers.filter(server => server.enabled);
        if (!enabledServers.length) {
            const empty = document.createElement('p'); empty.className = 'server-select-empty';
            empty.textContent = '还没有可用服务器'; elements.serverSelectMenu.appendChild(empty); return;
        }
        enabledServers.forEach(server => {
            const option = document.createElement('button'); option.type = 'button';
            option.className = 'server-select-option'; option.dataset.serverId = server.id;
            option.setAttribute('role', 'option'); option.setAttribute('aria-selected', 'false');
            const icon = document.createElement('span'); icon.className = 'server-option-icon'; icon.textContent = 'S';
            const text = document.createElement('span'); text.className = 'server-option-text';
            const name = document.createElement('strong'); name.textContent = server.name;
            const address = document.createElement('small'); address.textContent = `${server.username}@${server.host}:${server.port}`;
            const check = document.createElement('span'); check.className = 'server-option-check'; check.textContent = '✓';
            text.append(name, address); option.append(icon, text, check);
            option.addEventListener('click', () => selectServer(server.id));
            elements.serverSelectMenu.appendChild(option);
        });
    }

    function toggleServerMenu() {
        if (elements.serverSelectButton.disabled) return;
        const willOpen = elements.serverSelectMenu.hidden;
        elements.serverSelectMenu.hidden = !willOpen;
        elements.serverSelectButton.setAttribute('aria-expanded', String(willOpen));
    }

    function closeServerMenu() {
        elements.serverSelectMenu.hidden = true;
        elements.serverSelectButton.setAttribute('aria-expanded', 'false');
    }

    function renderHistory() {
        elements.historyList.replaceChildren();
        [...conversations].sort((a, b) => b.updatedAt - a.updatedAt).forEach(conversation => {
            const row = document.createElement('div');
            row.className = 'history-item' + (conversation.id === currentConversationId ? ' active' : '')
                + (managingConversations ? ' selecting' : '')
                + (selectedConversationIds.has(conversation.id) ? ' selected' : ''); row.tabIndex = 0;
            if (managingConversations) {
                const checkbox = document.createElement('input'); checkbox.type = 'checkbox'; checkbox.className = 'history-checkbox';
                checkbox.checked = selectedConversationIds.has(conversation.id);
                checkbox.setAttribute('aria-label', `选择会话：${conversation.title}`);
                checkbox.addEventListener('click', event => event.stopPropagation());
                checkbox.addEventListener('change', () => toggleConversationSelection(conversation.id));
                row.appendChild(checkbox);
            }
            const title = document.createElement('span'); title.className = 'history-title'; title.textContent = conversation.title;
            const activate = () => {
                if (managingConversations) return toggleConversationSelection(conversation.id);
                if (!streaming) { currentConversationId = conversation.id; renderAll(); closeMobileSidebar(); }
            };
            row.addEventListener('click', activate); row.addEventListener('keydown', event => { if (event.key === 'Enter') activate(); });
            row.appendChild(title); elements.historyList.appendChild(row);
        });
        syncConversationSelectionControls();
    }

    function renderMessages() {
        const conversation = currentConversation(); const hasMessages = conversation.messages.length > 0;
        elements.chatContainer.classList.toggle('centered', !hasMessages); elements.welcome.hidden = hasMessages;
        elements.messages.replaceChildren();
        conversation.messages.forEach(message => elements.messages.appendChild(renderMessage(message)));
        requestAnimationFrame(() => { elements.messages.scrollTop = elements.messages.scrollHeight; });
    }

    function renderMessage(message) {
        const row = document.createElement('article'); row.className = `message ${message.role}`;
        const stack = document.createElement('div'); stack.className = 'message-stack';
        const content = document.createElement('div'); content.className = 'message-content';
        if (message.role === 'assistant') {
            content.classList.add('markdown-body');
            if (message.streaming && !message.content) { content.className = 'message-content typing'; content.textContent = '正在检查'; }
            else { content.innerHTML = renderMarkdown(message.content || ''); decorateCodeBlocks(content); }
        } else content.textContent = message.content;
        stack.appendChild(content);
        if (message.action) stack.appendChild(renderAction(message));
        if (!message.streaming && message.content) stack.appendChild(renderCopy(message));
        row.appendChild(stack); return row;
    }

    function renderCopy(message) {
        const actions = document.createElement('div'); actions.className = 'message-actions';
        const copy = document.createElement('button'); copy.type = 'button'; copy.className = 'message-copy';
        copy.innerHTML = '<svg viewBox="0 0 24 24" fill="none"><rect x="8" y="8" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2" stroke="currentColor" stroke-width="1.8"/></svg><span>复制</span>';
        copy.addEventListener('click', () => copyText(message.content, copy)); actions.appendChild(copy); return actions;
    }

    function renderAction(message) {
        const action = message.action; const card = document.createElement('section');
        card.className = `operation-action ${String(action.status || '').toLowerCase()}`;
        const addingCommand = action.actionType === 'ADD_COMMAND';
        const heading = document.createElement('div'); heading.className = 'operation-heading';
        const icon = document.createElement('span'); icon.className = 'operation-icon'; icon.textContent = addingCommand ? '+' : '⚙';
        const text = document.createElement('div');
        const title = document.createElement('h3');
        title.textContent = `${addingCommand ? '添加命令' : '执行命令'}：${action.commandName || '未命名命令'}`;
        const meta = document.createElement('p'); meta.textContent = `${action.serverName} · ${action.serverId}`;
        text.append(title, meta); heading.append(icon, text); card.appendChild(heading);
        if (addingCommand && action.commandDescription) {
            const description = document.createElement('p'); description.className = 'operation-description';
            description.textContent = action.commandDescription; card.appendChild(description);
        }
        const reason = document.createElement('p'); reason.className = 'operation-reason'; reason.textContent = action.reason; card.appendChild(reason);
        const command = document.createElement('div'); command.className = 'command-preview'; command.textContent = action.commandPreview; card.appendChild(command);
        if (addingCommand) {
            const risk = document.createElement('div');
            risk.className = `proposal-risk ${action.riskLevel === 'DANGEROUS' ? 'dangerous' : ''}`;
            risk.textContent = action.riskLevel === 'DANGEROUS'
                ? '服务端判定：危险命令 · 添加后执行仍需再次确认'
                : '服务端判定：普通命令 · 添加后可直接执行';
            card.appendChild(risk);
        }
        const footer = document.createElement('div'); footer.className = 'operation-footer';
        const status = document.createElement('span'); status.textContent = actionStatus(action.status); footer.appendChild(status);
        if (addingCommand && action.status === 'PENDING_COMMAND_APPROVAL') {
            footer.append(actionButton('暂不添加', 'cancel-operation', () => cancelCommandProposal(message)),
                actionButton('添加命令', 'execute-operation', () => approveCommandProposal(message)));
        } else if (action.status === 'PENDING_APPROVAL') {
            footer.append(actionButton('取消', 'cancel-operation', () => cancelOperation(message)),
                actionButton('执行', 'execute-operation', () => approveOperation(message)));
        }
        card.appendChild(footer); return card;
    }

    function actionButton(label, className, handler) {
        const button = document.createElement('button'); button.type = 'button'; button.className = className;
        button.textContent = label; button.addEventListener('click', handler); return button;
    }

    async function approveOperation(message) {
        if (message.action?.status !== 'PENDING_APPROVAL') return;
        message.action.status = 'PROCESSING'; renderMessages();
        try {
            const result = await api(`/api/server/v1/operations/${encodeURIComponent(message.action.actionId)}/approve`, { method: 'POST' });
            message.action.status = result.success ? 'EXECUTED' : 'FAILED';
            message.action.execution = result.execution; showToast(result.message, 6000);
        } catch (error) { message.action.status = 'PENDING_APPROVAL'; handleApiError(error); }
        finally { persistConversations(); renderMessages(); }
    }

    async function cancelOperation(message) {
        if (message.action?.status !== 'PENDING_APPROVAL') return;
        try {
            await api(`/api/server/v1/operations/${encodeURIComponent(message.action.actionId)}`, { method: 'DELETE' });
            message.action.status = 'CANCELLED'; persistConversations(); renderMessages();
        } catch (error) { handleApiError(error); }
    }

    async function approveCommandProposal(message) {
        if (message.action?.status !== 'PENDING_COMMAND_APPROVAL') return;
        message.action.status = 'PROCESSING'; renderMessages();
        try {
            const result = await api(`/api/server/v1/command-proposals/${encodeURIComponent(message.action.actionId)}/approve`, { method: 'POST' });
            message.action.status = 'ADDED'; message.action.commandId = result.command?.id;
            if (editingServerId === message.action.serverId) await loadCommands(editingServerId);
            showToast(result.message, 7000);
        } catch (error) { message.action.status = 'PENDING_COMMAND_APPROVAL'; handleApiError(error); }
        finally { persistConversations(); renderMessages(); }
    }

    async function cancelCommandProposal(message) {
        if (message.action?.status !== 'PENDING_COMMAND_APPROVAL') return;
        try {
            await api(`/api/server/v1/command-proposals/${encodeURIComponent(message.action.actionId)}`, { method: 'DELETE' });
            message.action.status = 'CANCELLED'; persistConversations(); renderMessages();
        } catch (error) { handleApiError(error); }
    }

    function actionStatus(status) {
        return ({ PENDING_APPROVAL: '等待你的确认', PENDING_COMMAND_APPROVAL: '等待你确认添加', PROCESSING: '正在处理…',
            ADDED: '已添加到当前服务器', EXECUTED: '执行成功', FAILED: '执行失败',
            CANCELLED: '已取消', SUPERSEDED: '已失效' })[status] || status;
    }

    function renderMarkdown(source) {
        if (!window.marked || !window.DOMPurify) return escapeHtml(source).replace(/\n/g, '<br>');
        return window.DOMPurify.sanitize(window.marked.parse(source), { USE_PROFILES: { html: true } });
    }

    function decorateCodeBlocks(root) {
        root.querySelectorAll('a').forEach(link => { link.target = '_blank'; link.rel = 'noopener noreferrer'; });
        root.querySelectorAll('pre').forEach(block => {
            const button = document.createElement('button'); button.type = 'button'; button.className = 'code-copy'; button.textContent = '复制代码';
            button.addEventListener('click', () => copyText(block.querySelector('code')?.textContent || block.textContent, button)); block.appendChild(button);
        });
    }

    async function copyText(text, button) {
        try {
            await navigator.clipboard.writeText(text); const label = button.querySelector('span');
            if (label) label.textContent = '已复制'; else button.textContent = '已复制';
            setTimeout(() => { if (label) label.textContent = '复制'; else button.textContent = '复制代码'; }, 1400);
        } catch (_) { showToast('复制失败，请手动选择内容'); }
    }

    function newConversation() {
        if (streaming) return; const conversation = createConversation(currentConversation()?.serverId || servers.find(server => server.enabled)?.id || null); conversations.unshift(conversation);
        conversations = conversations.slice(0, MAX_CONVERSATIONS); currentConversationId = conversation.id;
        persistConversations(); renderAll(); closeMobileSidebar(); elements.input.focus();
    }

    function setConversationManagement(active) {
        if (streaming) { showToast('回答生成期间不能管理会话'); return; }
        managingConversations = active;
        selectedConversationIds.clear();
        document.querySelector('#manageConversationsButton').hidden = active;
        elements.conversationBulkActions.hidden = !active;
        renderHistory();
    }

    function toggleConversationSelection(id) {
        if (selectedConversationIds.has(id)) selectedConversationIds.delete(id);
        else selectedConversationIds.add(id);
        renderHistory();
    }

    function toggleAllConversations() {
        selectedConversationIds.clear();
        if (elements.selectAllConversations.checked) conversations.forEach(conversation => selectedConversationIds.add(conversation.id));
        renderHistory();
    }

    function syncConversationSelectionControls() {
        const selectedCount = selectedConversationIds.size;
        elements.selectedConversationCount.textContent = `已选 ${selectedCount} 项`;
        elements.deleteSelectedConversations.disabled = selectedCount === 0;
        elements.selectAllConversations.checked = conversations.length > 0 && selectedCount === conversations.length;
        elements.selectAllConversations.indeterminate = selectedCount > 0 && selectedCount < conversations.length;
    }

    async function deleteSelectedConversations() {
        if (streaming || !selectedConversationIds.size) return;
        const count = selectedConversationIds.size;
        if (!window.confirm(`确定删除选中的 ${count} 个本地对话吗？此操作无法撤销。`)) return;
        const removed = conversations.filter(conversation => selectedConversationIds.has(conversation.id));
        const cleanupResults = await Promise.all(removed.map(async conversation => {
            try {
                await api(`/api/server/v1/conversations/${encodeURIComponent(conversation.id)}`, { method: 'DELETE' });
                return { id: conversation.id, success: true };
            } catch (error) { return { id: conversation.id, success: false, error }; }
        }));
        const deletedIds = new Set(cleanupResults.filter(result => result.success).map(result => result.id));
        if (!deletedIds.size) {
            handleApiError(cleanupResults.find(result => result.error)?.error || new Error('服务端对话清理失败'));
            return;
        }
        const replacementServerId = currentConversation()?.serverId || servers.find(server => server.enabled)?.id || null;
        conversations = conversations.filter(conversation => !deletedIds.has(conversation.id));
        if (!conversations.length) conversations.push(createConversation(replacementServerId));
        if (!conversations.some(conversation => conversation.id === currentConversationId)) currentConversationId = conversations[0].id;
        persistConversations(); setConversationManagement(false); renderAll();
        const failedCount = count - deletedIds.size;
        showToast(failedCount
            ? `已同步删除 ${deletedIds.size} 个对话，${failedCount} 个清理失败，请重试`
            : `已同步删除 ${deletedIds.size} 个对话`);
    }

    function createConversation(serverId = null) { return { id: crypto.randomUUID(), serverId, title: '新对话', messages: [], updatedAt: Date.now() }; }
    function currentConversation() { return conversations.find(value => value.id === currentConversationId) || conversations[0]; }
    function expireActions(conversation) { conversation.messages.forEach(message => {
        if (['PENDING_APPROVAL', 'PENDING_COMMAND_APPROVAL'].includes(message.action?.status)) message.action.status = 'SUPERSEDED';
    }); }
    function loadConversations() { try { const value = JSON.parse(localStorage.getItem(CONVERSATIONS_KEY)); return Array.isArray(value) ? value.slice(0, MAX_CONVERSATIONS) : []; } catch (_) { return []; } }
    function persistConversations() { localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations.slice(0, MAX_CONVERSATIONS))); }

    async function api(url, options = {}) {
        const response = await fetch(url, { ...options, headers: { ...authHeaders(), ...(options.headers || {}) } });
        if (!response.ok) throw await responseError(response); if (response.status === 204) return null; return response.json();
    }
    function authHeaders() { return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }; }
    async function responseError(response) {
        let message = `请求失败（HTTP ${response.status}）`;
        try { const data = await response.json(); if (data.message) message = data.message; }
        catch (_) { if (response.status === 401) message = '访问口令无效或已更改'; if (response.status === 503) message = '服务器助手访问口令尚未配置'; }
        const error = new Error(message); error.status = response.status; return error;
    }
    function handleApiError(error) { if (error.status === 401) logout(); showToast(error.message, 6000); }
    function updateStreamingState() {
        const ready = Boolean(servers.find(server => server.id === currentConversation()?.serverId && server.enabled));
        elements.input.disabled = streaming || !ready; elements.send.disabled = streaming || !ready;
        elements.input.placeholder = ready ? '向服务器助手提问' : '请先选择或配置服务器';
        elements.serverSelectButton.disabled = streaming;
        if (streaming) closeServerMenu();
    }
    function resizeComposer() { elements.input.style.height = 'auto'; elements.input.style.height = `${Math.min(elements.input.scrollHeight, 200)}px`; }
    function syncViewportHeight() { const height = window.visualViewport?.height || window.innerHeight; document.documentElement.style.setProperty('--app-height', `${Math.round(height)}px`); }
    function openMobileSidebar() { elements.sidebar.classList.add('active'); elements.sidebarOverlay.classList.add('active'); }
    function closeMobileSidebar() { elements.sidebar.classList.remove('active'); elements.sidebarOverlay.classList.remove('active'); }
    function toggleSidebar() {
        const collapsed = elements.sidebar.classList.toggle('collapsed');
        const button = document.querySelector('#toggleSidebar');
        button.classList.toggle('is-collapsed', collapsed);
        button.setAttribute('aria-label', collapsed ? '展开侧边栏' : '折叠侧边栏');
    }
    function escapeHtml(value) { return String(value).replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]); }
    function authLabel(value) { return value === 'PRIVATE_KEY' ? '私钥' : '密码'; }
    function field(id) { return document.querySelector(`#${id}`); }
    function showToast(message, duration = 3500) { clearTimeout(toastTimer); elements.toast.textContent = message; elements.toast.hidden = false; toastTimer = setTimeout(() => { elements.toast.hidden = true; }, duration); }
})();
