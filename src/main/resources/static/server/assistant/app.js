(() => {
    'use strict';

    // 服务器助手整页应用。对话与服务器强绑定（服务端同一对话只绑一台服务器），
    // 所以本地会话记录里每个 conversation 都带 serverId；切换服务器 = 新建对话。
    // 消息协议分两段：
    //   1) POST /api/server/v1/messages               —— 普通提问，SSE 返回文本 + 可能的 action 事件
    //   2) POST /api/server/v1/messages/continue      —— 用户确认「执行命令/添加命令」后，凭服务端签发的一次性
    //      continuationId 恢复同一模型会话，让模型基于真实执行结果继续原任务
    // action 事件载荷是 pendingOperation / pendingCommandProposal 之一，前端据此渲染确认卡片。
    const TOKEN_KEY = 'ai-platform.server-access-token';
    const CONVERSATIONS_KEY = 'ai-platform.server-conversations.v1';
    const MAX_CONVERSATIONS = 20;
    const MAX_MESSAGES = 100;
    const elements = {
        sidebar: document.querySelector('#sidebar'), sidebarOverlay: document.querySelector('#sidebarOverlay'),
        serverList: document.querySelector('#serverList'), historyList: document.querySelector('#historyList'),
        chatContainer: document.querySelector('#chatContainer'), welcome: document.querySelector('#welcome'),
        messages: document.querySelector('#messages'), input: document.querySelector('#messageInput'),
        messageOutline: document.querySelector('#messageOutline'),
        scrollToBottom: document.querySelector('#scrollToBottomButton'),
        send: document.querySelector('#sendButton'), loginOverlay: document.querySelector('#loginOverlay'),
        loginForm: document.querySelector('#loginForm'), accessToken: document.querySelector('#accessToken'),
        loginError: document.querySelector('#loginError'), toast: document.querySelector('#toast'),
        serverSelectButton: document.querySelector('#serverSelectButton'), serverSelectLabel: document.querySelector('#serverSelectLabel'),
        serverSelectMenu: document.querySelector('#serverSelectMenu'), currentServerMeta: document.querySelector('#currentServerMeta'),
        providerSelect: document.querySelector('#providerSelect'), modelSelect: document.querySelector('#modelSelect'),
        reasoningRange: document.querySelector('#reasoningRange'), reasoningValueLabel: document.querySelector('#reasoningValueLabel'),
        modelMenuButton: document.querySelector('#modelMenuButton'), modelMenu: document.querySelector('#modelMenu'),
        composerModelLabel: document.querySelector('#composerModelLabel'),
        composerReasoningLabel: document.querySelector('#composerReasoningLabel'),
        conversationBulkActions: document.querySelector('#conversationBulkActions'),
        selectAllConversations: document.querySelector('#selectAllConversations'),
        selectedConversationCount: document.querySelector('#selectedConversationCount'),
        deleteSelectedConversations: document.querySelector('#deleteSelectedConversations'),
        settingsOverlay: document.querySelector('#settingsOverlay'), settingsServerList: document.querySelector('#settingsServerList'),
        globalSettingsOverlay: document.querySelector('#globalSettingsOverlay'),
        serverForm: document.querySelector('#serverForm'), commandSection: document.querySelector('#commandSection'),
        commandList: document.querySelector('#commandList'), commandForm: document.querySelector('#commandForm'),
        commandSearch: document.querySelector('#commandSearch'), selectAllCommands: document.querySelector('#selectAllCommands'),
        selectedCommandCount: document.querySelector('#selectedCommandCount'),
        enableSelectedCommands: document.querySelector('#enableSelectedCommands'),
        disableSelectedCommands: document.querySelector('#disableSelectedCommands'),
        deleteSelectedCommands: document.querySelector('#deleteSelectedCommands'),
        modelProviderList: document.querySelector('#modelProviderList'),
        modelProviderForm: document.querySelector('#modelProviderForm')
    };

    let token = sessionStorage.getItem(TOKEN_KEY) || '';
    let servers = [];
    let commands = [];
    let modelProviders = [];
    let modelOptions = [];
    let modelProvidersLoaded = false;
    let editingModelProviderId = null;
    let editingServerId = null;
    let editingCommandId = null;
    let conversations = loadConversations();
    if (!conversations.length) conversations.push(createConversation());
    let currentConversationId = conversations[0].id;
    let streaming = false;
    let managingConversations = false;
    const selectedConversationIds = new Set();
    const selectedCommandIds = new Set();
    const REASONING_LEVELS = ['auto', 'none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'];
    const REASONING_LABELS = { auto: '自动', none: '关闭', minimal: '最小', low: '低', medium: '中', high: '高', xhigh: '超高', max: 'Max' };
    const BOTTOM_FOLLOW_THRESHOLD = 120;
    let followConversationTail = true;
    let programmaticScroll = false;
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
        document.querySelector('#globalSettingsButton').addEventListener('click', openGlobalSettings);
        document.querySelector('#closeGlobalSettingsButton').addEventListener('click', closeGlobalSettings);
        document.querySelector('#addServerButton').addEventListener('click', () => editServer(null));
        document.querySelector('#installDefaultCommandsButton').addEventListener('click', installDefaultCommands);
        document.querySelector('#addCommandButton').addEventListener('click', () => editCommand(null));
        document.querySelector('#addModelProviderButton').addEventListener('click', () => editModelProvider(null));
        document.querySelector('#cancelModelProviderButton').addEventListener('click', () => { elements.modelProviderForm.hidden = true; });
        document.querySelector('#deleteModelProviderButton').addEventListener('click', deleteModelProvider);
        document.querySelector('#testModelProviderButton').addEventListener('click', () => probeModelProvider(false));
        document.querySelector('#discoverProviderModelsButton').addEventListener('click', () => probeModelProvider(true));
        document.querySelector('#addProviderModelButton').addEventListener('click', () => addProviderModelRow());
        document.querySelector('#cancelCommandButton').addEventListener('click', () => { elements.commandForm.hidden = true; });
        document.querySelector('#deleteServerButton').addEventListener('click', deleteServer);
        document.querySelector('#testServerButton').addEventListener('click', testServer);
        document.querySelector('#deleteCommandButton').addEventListener('click', deleteCommand);
        elements.commandSearch.addEventListener('input', renderCommands);
        elements.selectAllCommands.addEventListener('change', toggleAllCommands);
        elements.enableSelectedCommands.addEventListener('click', () => bulkSetCommandsEnabled(true));
        elements.disableSelectedCommands.addEventListener('click', () => bulkSetCommandsEnabled(false));
        elements.deleteSelectedCommands.addEventListener('click', bulkDeleteCommands);
        document.querySelector('#serverAuthType').addEventListener('change', syncAuthenticationFields);
        elements.serverForm.addEventListener('submit', saveServer);
        elements.commandForm.addEventListener('submit', saveCommand);
        elements.modelProviderForm.addEventListener('submit', saveModelProvider);
        elements.serverSelectButton.addEventListener('click', toggleServerMenu);
        elements.modelMenuButton.addEventListener('click', toggleModelMenu);
        elements.providerSelect.addEventListener('change', () => {
            const providerId = elements.providerSelect.value;
            const firstModel = modelOptions.find(model => model.providerId === providerId && model.apiProtocol !== 'openai-responses');
            currentConversation().modelId = firstModel?.id || null;
            currentConversation().updatedAt = Date.now();
            persistConversations(); renderModelSelection();
        });
        elements.modelSelect.addEventListener('change', () => {
            currentConversation().modelId = elements.modelSelect.value || null;
            currentConversation().updatedAt = Date.now();
            persistConversations(); renderModelSelection();
        });
        elements.reasoningRange.addEventListener('input', () => {
            currentConversation().reasoningEffort = REASONING_LEVELS[Number(elements.reasoningRange.value)] || 'auto';
            currentConversation().updatedAt = Date.now();
            persistConversations(); renderModelSelection();
        });
        elements.messages.addEventListener('scroll', handleConversationScroll, { passive: true });
        elements.scrollToBottom.addEventListener('click', () => scrollConversationToBottom(true));
        document.addEventListener('click', event => {
            if (!event.target.closest('.server-picker')) closeServerMenu();
            if (!event.target.closest('.composer-model-picker')) closeModelMenu();
        });
        document.addEventListener('keydown', event => {
            if (event.key !== 'Escape') return;
            closeServerMenu(); closeModelMenu();
            if (!elements.globalSettingsOverlay.hidden) closeGlobalSettings();
            else if (!elements.settingsOverlay.hidden) closeSettings();
        });
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
            await Promise.all([loadServers(), loadModelProviders()]);
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
            await Promise.all([loadServers(), loadModelProviders()]); elements.input.focus();
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

    async function loadModelProviders() {
        modelProviders = await api('/api/server/v1/model-providers');
        modelOptions = modelProviders.flatMap(provider => provider.enabled
            ? provider.models.filter(model => model.enabled) : []);
        modelProvidersLoaded = true;
        renderModelProviders();
        renderModelSelection();
    }

    function renderModelSelection() {
        const selected = currentConversation()?.modelId || '';
        const reasoning = currentConversation()?.reasoningEffort || 'auto';
        const selectedModel = modelOptions.find(model => model.id === selected && model.apiProtocol !== 'openai-responses');
        if (selected && !selectedModel && modelProvidersLoaded) {
            currentConversation().modelId = null;
            persistConversations();
        }
        const providerId = selectedModel?.providerId || '';
        elements.providerSelect.replaceChildren(new Option('系统默认', ''));
        modelProviders.filter(provider => provider.enabled && provider.models.some(model => model.enabled))
            .forEach(provider => elements.providerSelect.appendChild(new Option(provider.name, provider.id)));
        elements.providerSelect.value = providerId;
        elements.modelSelect.replaceChildren(new Option(providerId ? '请选择模型' : '系统默认模型', ''));
        modelOptions.filter(model => model.providerId === providerId).forEach(model => {
            const unsupported = model.apiProtocol === 'openai-responses';
            const option = new Option(`${model.name}${unsupported ? '（仅支持测试与模型目录）' : ''}`, model.id);
            option.disabled = unsupported; elements.modelSelect.appendChild(option);
        });
        elements.modelSelect.value = selectedModel?.id || '';
        elements.modelSelect.disabled = streaming || !providerId;
        const reasoningIndex = Math.max(0, REASONING_LEVELS.indexOf(reasoning));
        elements.reasoningRange.value = String(reasoningIndex);
        elements.reasoningRange.style.setProperty('--reasoning-progress', `${reasoningIndex / (REASONING_LEVELS.length - 1) * 100}%`);
        elements.reasoningValueLabel.textContent = REASONING_LABELS[REASONING_LEVELS[reasoningIndex]];
        const model = modelOptions.find(value => value.id === elements.modelSelect.value);
        elements.composerModelLabel.textContent = model ? `${model.providerName} / ${model.name}` : '系统默认模型';
        elements.composerReasoningLabel.textContent = REASONING_LABELS[REASONING_LEVELS[reasoningIndex]];
    }

    function renderModelProviders() {
        elements.modelProviderList.replaceChildren();
        if (!modelProviders.length) {
            const empty = document.createElement('p'); empty.className = 'empty-note';
            empty.textContent = '尚未配置自定义模型，当前使用系统默认模型。';
            elements.modelProviderList.appendChild(empty); return;
        }
        modelProviders.forEach(provider => {
            const item = document.createElement('button'); item.type = 'button'; item.className = 'provider-item';
            const main = document.createElement('span'); main.className = 'provider-item-main';
            const name = document.createElement('strong');
            const status = document.createElement('i'); status.className = provider.enabled ? 'online' : '';
            name.append(document.createTextNode(provider.name), status);
            const description = document.createElement('small');
            description.textContent = `${provider.providerKey} · ${provider.baseUrl} · ${provider.models.length} 个模型`;
            main.append(name, description);
            const badge = document.createElement('span'); badge.className = 'provider-edit-label'; badge.textContent = '编辑';
            item.append(main, badge); item.addEventListener('click', () => editModelProvider(provider.id));
            elements.modelProviderList.appendChild(item);
        });
    }

    function editModelProvider(providerId) {
        editingModelProviderId = providerId;
        const provider = modelProviders.find(value => value.id === providerId);
        elements.modelProviderForm.reset(); elements.modelProviderForm.hidden = false;
        field('modelProviderId').value = provider?.id || '';
        field('modelProviderKey').value = provider?.providerKey || '';
        field('modelProviderName').value = provider?.name || '';
        field('modelProviderBaseUrl').value = provider?.baseUrl || '';
        field('modelProviderProtocol').value = provider?.apiProtocol || 'openai-completions';
        field('modelProviderApiKey').value = '';
        field('modelProviderEnabled').checked = provider?.enabled ?? true;
        setProviderModelRows(provider?.models || []);
        field('modelProviderFormError').textContent = '';
        field('deleteModelProviderButton').hidden = !provider;
        field('modelProviderName').focus();
    }

    function setProviderModelRows(models) {
        field('modelProviderModels').replaceChildren();
        (models.length ? models : [{ name: '', modelCode: '' }]).forEach(model => addProviderModelRow(model));
    }

    function addProviderModelRow(model = {}) {
        const row = document.createElement('div'); row.className = 'provider-model-row';
        const code = document.createElement('input'); code.className = 'provider-model-code'; code.placeholder = '模型 ID'; code.maxLength = 200; code.value = model.modelCode || model.id || '';
        const name = document.createElement('input'); name.className = 'provider-model-name'; name.placeholder = '显示名称'; name.maxLength = 80; name.value = model.name || model.displayName || model.id || '';
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'provider-model-remove'; remove.textContent = '删除';
        remove.addEventListener('click', () => { row.remove(); if (!field('modelProviderModels').children.length) addProviderModelRow(); });
        row.append(code, name, remove); field('modelProviderModels').appendChild(row);
    }

    function readModelOptions() {
        return [...field('modelProviderModels').querySelectorAll('.provider-model-row')].map((row, index) => {
            const modelCode = row.querySelector('.provider-model-code').value.trim();
            const name = row.querySelector('.provider-model-name').value.trim();
            if (!modelCode || !name) throw new Error(`请完整填写第 ${index + 1} 个模型`);
            return { name, modelCode, reasoningEffort: null, enabled: true, sortOrder: index };
        });
    }

    function providerProbePayload() {
        return { providerId: editingModelProviderId, baseUrl: field('modelProviderBaseUrl').value.trim(),
            apiProtocol: field('modelProviderProtocol').value, apiKey: field('modelProviderApiKey').value || null };
    }

    async function probeModelProvider(importModels) {
        field('modelProviderFormError').textContent = '';
        const button = field(importModels ? 'discoverProviderModelsButton' : 'testModelProviderButton');
        button.disabled = true;
        try {
            const result = await api('/api/server/v1/model-providers/probe', {
                method: 'POST', body: JSON.stringify(providerProbePayload())
            });
            if (importModels) {
                if (!result.models.length) throw new Error('连接成功，但提供方没有返回可导入的模型');
                setProviderModelRows(result.models.map(model => ({ modelCode: model.id, name: model.name })));
            }
            showToast(result.message, 5000);
        } catch (error) { field('modelProviderFormError').textContent = error.message; }
        finally { button.disabled = false; }
    }

    async function saveModelProvider(event) {
        event.preventDefault(); field('modelProviderFormError').textContent = '';
        try {
            const payload = {
                providerKey: field('modelProviderKey').value.trim(),
                name: field('modelProviderName').value.trim(),
                baseUrl: field('modelProviderBaseUrl').value.trim(),
                apiProtocol: field('modelProviderProtocol').value,
                apiKey: field('modelProviderApiKey').value || null,
                enabled: field('modelProviderEnabled').checked,
                models: readModelOptions()
            };
            await api(editingModelProviderId ? `/api/server/v1/model-providers/${editingModelProviderId}`
                : '/api/server/v1/model-providers', {
                method: editingModelProviderId ? 'PUT' : 'POST', body: JSON.stringify(payload)
            });
            await loadModelProviders(); elements.modelProviderForm.hidden = true; showToast('模型提供方已保存');
        } catch (error) { field('modelProviderFormError').textContent = error.message; }
    }

    async function deleteModelProvider() {
        const provider = modelProviders.find(value => value.id === editingModelProviderId);
        if (!provider || !window.confirm(`确定删除模型提供方“${provider.name}”及其模型吗？`)) return;
        try {
            await api(`/api/server/v1/model-providers/${provider.id}`, { method: 'DELETE' });
            editingModelProviderId = null; await loadModelProviders(); elements.modelProviderForm.hidden = true;
            showToast('模型提供方已删除');
        } catch (error) { field('modelProviderFormError').textContent = error.message; }
    }

    async function openGlobalSettings() {
        elements.globalSettingsOverlay.hidden = false;
        await loadModelProviders();
        document.querySelector('.global-settings-main').scrollTop = 0;
    }

    function closeGlobalSettings() {
        elements.globalSettingsOverlay.hidden = true;
        elements.modelProviderForm.hidden = true;
    }

    // 打开服务器配置时默认选中「当前对话的服务器」；没有服务器时进入新增表单。
    async function openSettings() {
        elements.settingsOverlay.hidden = false;
        renderSettingsServerList();
        const candidate = editingServerId || currentConversation().serverId;
        const preferred = servers.some(server => server.id === candidate) ? candidate : (servers[0]?.id || null);
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

    // 编辑服务器表单：凭据输入框永远留空显示——服务端保存的是 AES-GCM 密文，
        // 接口只回「是否已配置」，不回明文；留空提交 = 沿用已有密文（见服务端 updateServer）。
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
        field('passwordCredentialGuide').hidden = privateKey;
        field('privateKeyCredentialGuide').hidden = !privateKey;
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

    async function loadCommands(serverId, resetView = true) {
        commands = await api(`/api/server/v1/servers/${serverId}/commands`);
        if (resetView) {
            selectedCommandIds.clear();
            elements.commandSearch.value = '';
        }
        renderCommands();
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
        const keyword = elements.commandSearch.value.trim().toLowerCase();
        const visibleCommands = commands.filter(command => !keyword || [command.name, command.description, command.commandText]
            .some(value => String(value || '').toLowerCase().includes(keyword)));
        if (!visibleCommands.length) {
            const empty = document.createElement('p'); empty.className = 'empty-note';
            empty.textContent = keyword ? '没有匹配的固定命令。'
                : (editingServerId ? '尚未配置命令，可一键补充常用只读命令。' : '请先保存服务器');
            elements.commandList.appendChild(empty); syncCommandSelectionControls(visibleCommands); return;
        }
        visibleCommands.forEach(command => {
            const item = document.createElement('div'); item.className = 'command-item' + (command.enabled ? '' : ' disabled');
            const checkbox = document.createElement('input'); checkbox.type = 'checkbox'; checkbox.className = 'command-select';
            checkbox.checked = selectedCommandIds.has(command.id); checkbox.setAttribute('aria-label', `选择命令：${command.name}`);
            checkbox.addEventListener('change', () => {
                if (checkbox.checked) selectedCommandIds.add(command.id); else selectedCommandIds.delete(command.id);
                renderCommands();
            });
            const main = document.createElement('button'); main.type = 'button'; main.className = 'command-item-main';
            const name = document.createElement('strong'); name.textContent = command.name;
            const description = document.createElement('span'); description.textContent = command.description;
            main.append(name, description); main.addEventListener('click', () => editCommand(command.id));
            const risk = document.createElement('span'); risk.className = 'risk-badge' + (command.riskLevel === 'DANGEROUS' ? ' dangerous' : '');
            risk.textContent = command.riskLevel === 'DANGEROUS' ? '需要确认' : '直接执行';
            const visibility = document.createElement('span'); visibility.className = 'command-visibility' + (command.enabled ? ' enabled' : '');
            visibility.textContent = command.enabled ? '助手可见' : '助手不可见';
            const toggleLabel = document.createElement('label'); toggleLabel.className = 'command-switch';
            const toggle = document.createElement('input'); toggle.type = 'checkbox'; toggle.checked = command.enabled;
            toggle.setAttribute('aria-label', `${command.enabled ? '停用' : '启用'}命令：${command.name}`);
            const slider = document.createElement('span'); toggleLabel.append(toggle, slider);
            toggle.addEventListener('change', () => setCommandEnabled(command, toggle.checked, toggle));
            const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'command-delete';
            remove.textContent = '×'; remove.setAttribute('aria-label', `删除命令：${command.name}`);
            remove.addEventListener('click', () => deleteCommandRecord(command));
            item.append(checkbox, main, risk, visibility, toggleLabel, remove);
            elements.commandList.appendChild(item);
        });
        syncCommandSelectionControls(visibleCommands);
    }

    function commandPayload(command, enabled = command.enabled) {
        return { name: command.name, description: command.description, commandText: command.commandText,
            riskLevel: command.riskLevel, parameterSchema: command.parameterSchema || '[]',
            sortOrder: Number(command.sortOrder || 0), enabled };
    }

    async function setCommandEnabled(command, enabled, control) {
        control.disabled = true;
        try {
            await api(`/api/server/v1/commands/${command.id}`, { method: 'PUT', body: JSON.stringify(commandPayload(command, enabled)) });
            await loadCommands(editingServerId, false);
            showToast(enabled ? '已启用，助手现在可以看到该命令' : '已停用，助手将不再看到该命令');
        } catch (error) { control.checked = !enabled; handleApiError(error); }
        finally { control.disabled = false; }
    }

    function toggleAllCommands() {
        const keyword = elements.commandSearch.value.trim().toLowerCase();
        const visible = commands.filter(command => !keyword || [command.name, command.description, command.commandText]
            .some(value => String(value || '').toLowerCase().includes(keyword)));
        visible.forEach(command => {
            if (elements.selectAllCommands.checked) selectedCommandIds.add(command.id); else selectedCommandIds.delete(command.id);
        });
        renderCommands();
    }

    function syncCommandSelectionControls(visibleCommands = commands) {
        const validIds = new Set(commands.map(command => command.id));
        [...selectedCommandIds].forEach(id => { if (!validIds.has(id)) selectedCommandIds.delete(id); });
        const selectedCount = selectedCommandIds.size;
        elements.selectedCommandCount.textContent = `已选 ${selectedCount} 项`;
        [elements.enableSelectedCommands, elements.disableSelectedCommands, elements.deleteSelectedCommands]
            .forEach(button => { button.disabled = selectedCount === 0; });
        const visibleSelected = visibleCommands.filter(command => selectedCommandIds.has(command.id)).length;
        elements.selectAllCommands.checked = visibleCommands.length > 0 && visibleSelected === visibleCommands.length;
        elements.selectAllCommands.indeterminate = visibleSelected > 0 && visibleSelected < visibleCommands.length;
    }

    async function bulkSetCommandsEnabled(enabled) {
        const selected = commands.filter(command => selectedCommandIds.has(command.id) && command.enabled !== enabled);
        if (!selected.length) { showToast(enabled ? '所选命令都已启用' : '所选命令都已停用'); return; }
        const buttons = [elements.enableSelectedCommands, elements.disableSelectedCommands, elements.deleteSelectedCommands];
        buttons.forEach(button => { button.disabled = true; });
        try {
            await Promise.all(selected.map(command => api(`/api/server/v1/commands/${command.id}`, {
                method: 'PUT', body: JSON.stringify(commandPayload(command, enabled))
            })));
            selectedCommandIds.clear();
            await loadCommands(editingServerId, false); showToast(`已${enabled ? '启用' : '停用'} ${selected.length} 个命令`);
        } catch (error) { handleApiError(error); renderCommands(); }
    }

    async function bulkDeleteCommands() {
        const selected = commands.filter(command => selectedCommandIds.has(command.id));
        if (!selected.length || !window.confirm(`确定删除选中的 ${selected.length} 个固定命令吗？`)) return;
        try {
            await Promise.all(selected.map(command => api(`/api/server/v1/commands/${command.id}`, { method: 'DELETE' })));
            selectedCommandIds.clear();
            await loadCommands(editingServerId, false); elements.commandForm.hidden = true;
            showToast(`已删除 ${selected.length} 个命令`);
        } catch (error) { handleApiError(error); await loadCommands(editingServerId, false); }
    }

    function editCommand(commandId) {
        editingCommandId = commandId;
        const command = commands.find(value => value.id === commandId);
        elements.commandForm.reset(); elements.commandForm.hidden = false;
        field('commandId').value = command?.id || '';
        field('commandName').value = command?.name || '';
        field('commandDescription').value = command?.description || '';
        field('commandText').value = command?.commandText || '';
        field('commandParameterSchema').value = command?.parameterSchema === '[]' ? '' : (command?.parameterSchema || '');
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
            parameterSchema: field('commandParameterSchema').value.trim() || '[]',
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
        if (command) await deleteCommandRecord(command);
    }

    async function deleteCommandRecord(command) {
        if (!window.confirm(`确定删除命令“${command.name}”吗？`)) return;
        try {
            await api(`/api/server/v1/commands/${command.id}`, { method: 'DELETE' });
            selectedCommandIds.delete(command.id);
            await loadCommands(editingServerId, false); elements.commandForm.hidden = true; showToast('命令已删除');
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
        if (await resolvePendingActionByText(conversation, text)) return;
        // 把待确认的操作/提议置为「已失效」：服务端在生成新动作时会作废同一对话的旧动作，
        // 前端同步状态，避免用户点击一个服务端已不认的按钮。
        expireActions(conversation);
        conversation.messages.push({ id: crypto.randomUUID(), role: 'user', content: text });
        if (conversation.messages.filter(message => message.role === 'user').length === 1) {
            conversation.title = text.length > 24 ? text.slice(0, 24) + '…' : text;
        }
        const assistantMessage = { id: crypto.randomUUID(), role: 'assistant', content: '', streaming: true };
        conversation.messages.push(assistantMessage);
        conversation.messages = conversation.messages.slice(-MAX_MESSAGES);
        conversation.updatedAt = Date.now(); elements.input.value = ''; resizeComposer();
        followConversationTail = true;
        streaming = true; updateStreamingState(); renderAll();
        try {
            const response = await fetch('/api/server/v1/messages', {
                method: 'POST', headers: authHeaders(),
                body: JSON.stringify({ conversationId: conversation.id, serverId: conversation.serverId,
                    message: text, modelId: conversation.modelId || null,
                    reasoningEffort: conversation.reasoningEffort || 'auto' })
            });
            if (!response.ok) throw await responseError(response);
            const renderer = createStreamRenderer(assistantMessage);
            await consumeSse(response, renderer.content, action => {
                assistantMessage.action = action; renderMessages();
            }, renderer.reasoning);
            renderer.flush();
            if (!assistantMessage.content) assistantMessage.content = '暂时没有得到回答，请稍后再试。';
        } catch (error) {
            assistantMessage.content = `请求失败：${friendlyRequestError(error)}`;
            if (error.status === 401) logout();
        } finally {
            assistantMessage.streaming = false; streaming = false; conversation.updatedAt = Date.now();
            persistConversations(); updateStreamingState(); renderAll();
        }
    }

    // 手写 SSE 消费（与博客页面相同的协议）：POST + Bearer 不能走 EventSource；
        // 按空行切事件、缓冲未完成分片，action 事件在流末尾携带待确认操作/提议。
        async function consumeSse(response, onChunk, onAction, onReasoning = () => {}) {
        const reader = response.body.getReader(); const decoder = new TextDecoder();
        let buffer = ''; let completed = false;
        while (!completed) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
            const events = buffer.split(/\r?\n\r?\n/); buffer = events.pop() || '';
            for (const event of events) completed = consumeSseEvent(event, onChunk, onAction, onReasoning) || completed;
            if (done) break;
        }
        if (buffer && !completed) completed = consumeSseEvent(buffer, onChunk, onAction, onReasoning) || completed;
        if (!completed) throw new Error('流式连接意外中断，请重试');
    }

    function consumeSseEvent(event, onChunk, onAction, onReasoning) {
        const lines = event.split(/\r?\n/);
        const eventName = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message';
        const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).replace(/^ /, '')).join('\n');
        if (!data) return false;
        if (eventName === 'action') { try { onAction(JSON.parse(data)); } catch (_) { showToast('操作选项解析失败'); } return false; }
        if (eventName === 'reasoning') { onReasoning(data); return false; }
        if (data.trim() === '[DONE]') return true;
        onChunk(data); return false;
    }

    // 模型常按几个字符发送一次；把 60ms 内的碎片合并后再重绘，既保留实时感，也避免
    // Markdown 全量解析与 DOM 重建在每个 token 上发生而造成明显卡顿。
    function createStreamRenderer(message) {
        let contentBuffer = ''; let reasoningBuffer = ''; let timer = null;
        const flush = () => {
            if (timer) clearTimeout(timer); timer = null;
            if (contentBuffer) { message.content += contentBuffer; contentBuffer = ''; }
            if (reasoningBuffer) { message.reasoning = (message.reasoning || '') + reasoningBuffer; reasoningBuffer = ''; }
            renderMessages();
        };
        const schedule = () => { if (!timer) timer = setTimeout(flush, 60); };
        return {
            content: chunk => { contentBuffer += chunk; schedule(); },
            reasoning: chunk => { reasoningBuffer += chunk; schedule(); },
            flush
        };
    }

    function renderAll() { renderHistory(); renderServerSelection(); renderModelSelection(); renderMessages(); updateStreamingState(); }

    async function resolvePendingActionByText(conversation, text) {
        const message = [...conversation.messages].reverse().find(value =>
            ['PENDING_APPROVAL', 'PENDING_COMMAND_APPROVAL'].includes(value.action?.status));
        if (!message) return false;
        const temporary = message.action.actionType === 'EXECUTE_TEMPORARY_COMMAND';
        const compact = text.replace(/[\s，。！!？?]/g, '');
        const confirm = /^(执行|确认|确认执行|继续执行|同意|添加|确认添加)$/.test(compact);
        const cancel = /^(取消|不执行|取消执行|不添加|取消添加)$/.test(compact);
        if (!confirm && !cancel && !temporary) return false;
        conversation.messages.push({ id: crypto.randomUUID(), role: 'user', content: text });
        conversation.updatedAt = Date.now(); elements.input.value = ''; resizeComposer();
        followConversationTail = true;
        persistConversations(); renderAll();
        if (temporary && !confirm && !cancel) {
            await decideTemporaryOperation(message, 'REJECT_WITH_FEEDBACK', text);
        } else if (message.action.status === 'PENDING_COMMAND_APPROVAL') {
            if (confirm) await approveCommandProposal(message); else await cancelCommandProposal(message);
        } else if (confirm) await approveOperation(message); else await cancelOperation(message);
        return true;
    }

    // 会话没有绑定服务器时自动选第一台启用的；绑定的服务器被停用且会话还没有任何消息时，
        // 允许改绑其他服务器（有过对话的会话则保持原绑定，因为模型记忆里已经认定了那台服务器）。
        function ensureConversationServer() {
        const conversation = currentConversation();
        const enabled = servers.filter(server => server.enabled);
        if (!conversation.serverId && enabled.length) conversation.serverId = enabled[0].id;
        if (conversation.serverId && !enabled.some(server => server.id === conversation.serverId) && !conversation.messages.length) {
            conversation.serverId = enabled[0]?.id || null;
        }
        persistConversations();
    }

    // 服务端把「会话 ↔ 服务器」绑定在内存里，一个对话中途不能换服务器；
        // 所以有历史消息时切换目标 = 为那台服务器新建一个对话，而不是改绑当前对话。
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
        followConversationTail = true;
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

    function toggleModelMenu() {
        if (elements.modelMenuButton.disabled) return;
        const willOpen = elements.modelMenu.hidden;
        elements.modelMenu.hidden = !willOpen;
        elements.modelMenuButton.setAttribute('aria-expanded', String(willOpen));
    }

    function closeModelMenu() {
        elements.modelMenu.hidden = true;
        elements.modelMenuButton.setAttribute('aria-expanded', 'false');
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
                if (!streaming) { currentConversationId = conversation.id; followConversationTail = true; renderAll(); closeMobileSidebar(); }
            };
            row.addEventListener('click', activate); row.addEventListener('keydown', event => { if (event.key === 'Enter') activate(); });
            row.appendChild(title);
            if (!managingConversations) {
                const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'history-delete';
                remove.textContent = '×'; remove.setAttribute('aria-label', `删除对话：${conversation.title}`);
                remove.addEventListener('click', event => { event.stopPropagation(); deleteConversation(conversation); });
                row.appendChild(remove);
            }
            elements.historyList.appendChild(row);
        });
        syncConversationSelectionControls();
    }

    function renderMessages() {
        const conversation = currentConversation(); const hasMessages = conversation.messages.length > 0;
        const preservedScrollTop = elements.messages.scrollTop;
        // replaceChildren 会立刻触发一次 scroll；必须在改 DOM 前进入程序化滚动状态，
        // 否则流式重绘会把“仍在底部”误判成用户主动上滚。
        programmaticScroll = true;
        elements.chatContainer.classList.toggle('centered', !hasMessages); elements.welcome.hidden = hasMessages;
        elements.messages.replaceChildren();
        conversation.messages.forEach(message => elements.messages.appendChild(renderMessage(message)));
        renderMessageOutline(conversation);
        requestAnimationFrame(() => {
            elements.messages.scrollTop = followConversationTail ? elements.messages.scrollHeight : preservedScrollTop;
            updateScrollControls();
            requestAnimationFrame(() => { programmaticScroll = false; });
        });
    }

    function renderMessage(message) {
        const row = document.createElement('article'); row.className = `message ${message.role}`;
        message.id ||= crypto.randomUUID();
        row.id = `message-${message.id}`;
        const stack = document.createElement('div'); stack.className = 'message-stack';
        const content = document.createElement('div'); content.className = 'message-content';
        if (message.role === 'assistant') {
            if (message.reasoning) stack.appendChild(renderReasoning(message));
            content.classList.add('markdown-body');
            if (message.streaming && !message.content) { content.className = 'message-content typing'; content.textContent = '正在检查'; }
            else { content.innerHTML = renderMarkdown(message.content || ''); decorateCodeBlocks(content); }
        } else content.textContent = message.content;
        stack.appendChild(content);
        if (message.action) stack.appendChild(renderAction(message));
        if (!message.streaming && message.content) stack.appendChild(renderCopy(message));
        row.appendChild(stack); return row;
    }

    function renderReasoning(message) {
        const details = document.createElement('details'); details.className = 'reasoning-panel';
        details.open = Boolean(message.reasoningOpen);
        details.addEventListener('toggle', () => { message.reasoningOpen = details.open; });
        const summary = document.createElement('summary');
        const label = document.createElement('span'); label.className = 'reasoning-summary-label';
        label.textContent = message.streaming ? '正在思考' : '思考过程';
        const count = document.createElement('span'); count.className = 'reasoning-summary-count';
        count.textContent = `${message.reasoning.length} 字`;
        summary.append(label, count);
        if (message.streaming) {
            const preview = document.createElement('span'); preview.className = 'reasoning-summary-preview';
            const compact = message.reasoning.replace(/\s+/g, ' ').trim();
            preview.textContent = compact ? `· ${compact.slice(-34)}` : '';
            const pulse = document.createElement('span'); pulse.className = 'reasoning-live-pulse'; pulse.setAttribute('aria-hidden', 'true');
            summary.append(preview, pulse);
        }
        const body = document.createElement('div'); body.className = 'reasoning-content markdown-body';
        body.innerHTML = renderMarkdown(message.reasoning); decorateCodeBlocks(body);
        details.append(summary, body); return details;
    }

    function renderMessageOutline(conversation) {
        const questions = conversation.messages.filter(message => message.role === 'user');
        elements.messageOutline.hidden = questions.length < 6;
        elements.messageOutline.replaceChildren();
        if (questions.length < 6) return;
        const heading = document.createElement('strong'); heading.textContent = '本次提问';
        elements.messageOutline.appendChild(heading);
        questions.forEach((message, index) => {
            message.id ||= crypto.randomUUID();
            const button = document.createElement('button'); button.type = 'button';
            button.dataset.messageId = message.id; button.title = message.content;
            button.textContent = `${index + 1}. ${message.content.replace(/\s+/g, ' ').slice(0, 26)}`;
            button.addEventListener('click', () => {
                followConversationTail = false;
                document.querySelector(`#message-${message.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                updateScrollControls();
            });
            elements.messageOutline.appendChild(button);
        });
        updateOutlineHighlight();
    }

    function handleConversationScroll() {
        if (!programmaticScroll) {
            const distance = elements.messages.scrollHeight - elements.messages.scrollTop - elements.messages.clientHeight;
            followConversationTail = distance <= BOTTOM_FOLLOW_THRESHOLD;
        }
        updateScrollControls();
        updateOutlineHighlight();
    }

    function updateScrollControls() {
        const distance = elements.messages.scrollHeight - elements.messages.scrollTop - elements.messages.clientHeight;
        elements.scrollToBottom.hidden = !currentConversation().messages.length || distance <= BOTTOM_FOLLOW_THRESHOLD;
    }

    function scrollConversationToBottom(smooth = false) {
        followConversationTail = true;
        elements.messages.scrollTo({ top: elements.messages.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
        updateScrollControls();
    }

    function updateOutlineHighlight() {
        if (elements.messageOutline.hidden) return;
        const messageRows = [...elements.messages.querySelectorAll('.message.user')];
        const viewportTop = elements.messages.getBoundingClientRect().top + 50;
        let activeId = messageRows[0]?.id?.replace('message-', '') || '';
        messageRows.forEach(row => { if (row.getBoundingClientRect().top <= viewportTop) activeId = row.id.replace('message-', ''); });
        elements.messageOutline.querySelectorAll('button').forEach(button =>
            button.classList.toggle('active', button.dataset.messageId === activeId));
    }

    function renderCopy(message) {
        const actions = document.createElement('div'); actions.className = 'message-actions';
        const copy = document.createElement('button'); copy.type = 'button'; copy.className = 'message-copy';
        copy.innerHTML = '<svg viewBox="0 0 24 24" fill="none"><rect x="8" y="8" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2" stroke="currentColor" stroke-width="1.8"/></svg><span>复制</span>';
        copy.addEventListener('click', () => copyText(message.content, copy)); actions.appendChild(copy); return actions;
    }

    // 动作卡片：固定命令添加、固定危险命令和 ReAct 临时命令共用状态骨架，临时命令额外
    // 提供补充说明与当前对话精确放行，放行规则始终只保存在服务端。
    // 状态机 PENDING_* → PROCESSING → ADDED/EXECUTED/FAILED/CANCELLED/SUPERSEDED，
    // 只在待确认状态渲染操作按钮；危险命令的提议会提示「添加后执行仍需再次确认」。
    function renderAction(message) {
        const action = message.action; const card = document.createElement('section');
        card.className = `operation-action ${String(action.status || '').toLowerCase()}`;
        const addingCommand = action.actionType === 'ADD_COMMAND';
        const temporary = action.actionType === 'EXECUTE_TEMPORARY_COMMAND';
        const heading = document.createElement('div'); heading.className = 'operation-heading';
        const icon = document.createElement('span'); icon.className = 'operation-icon'; icon.textContent = addingCommand ? '+' : '⚙';
        const text = document.createElement('div');
        const title = document.createElement('h3');
        title.textContent = `${addingCommand ? '添加命令' : temporary ? '审批临时命令' : '执行命令'}：${action.commandName || '未命名命令'}`;
        const meta = document.createElement('p'); meta.textContent = `${action.serverName} · ${action.serverId}`;
        text.append(title, meta); heading.append(icon, text); card.appendChild(heading);
        if (addingCommand && action.commandDescription) {
            const description = document.createElement('p'); description.className = 'operation-description';
            description.textContent = action.commandDescription; card.appendChild(description);
        }
        const reason = document.createElement('p'); reason.className = 'operation-reason'; reason.textContent = action.reason; card.appendChild(reason);
        if (temporary && action.workingDirectory) {
            const directory = document.createElement('div'); directory.className = 'operation-directory';
            directory.textContent = `工作目录：${action.workingDirectory}`; card.appendChild(directory);
        }
        const command = document.createElement('div'); command.className = 'command-preview'; command.textContent = action.commandPreview; card.appendChild(command);
        if (addingCommand && action.parameterSchema && action.parameterSchema !== '[]') {
            const parameters = document.createElement('div'); parameters.className = 'command-preview';
            parameters.textContent = `参数约束：${action.parameterSchema}`; card.appendChild(parameters);
        }
        if (addingCommand) {
            const risk = document.createElement('div');
            risk.className = `proposal-risk ${action.riskLevel === 'DANGEROUS' ? 'dangerous' : ''}`;
            risk.textContent = action.riskLevel === 'DANGEROUS'
                ? '服务端判定：危险命令 · 添加后执行仍需再次确认'
                : '服务端判定：普通命令 · 添加后可直接执行';
            card.appendChild(risk);
        } else if (temporary) {
            const risk = document.createElement('div'); risk.className = 'proposal-risk dangerous';
            risk.textContent = '服务端无法证明该命令只读，尚未执行，也不会写入固定命令列表';
            card.appendChild(risk);
        }
        if (temporary && action.status === 'PENDING_APPROVAL') {
            const feedback = document.createElement('div'); feedback.className = 'operation-feedback';
            const textarea = document.createElement('textarea'); textarea.rows = 2; textarea.maxLength = 1000;
            textarea.placeholder = '可以补充限制或纠正计划，例如：先不要删除，只查看文件大小';
            const submit = actionButton('提交补充', 'revise-operation', () => {
                const value = textarea.value.trim();
                if (!value) { showToast('请先填写补充说明'); textarea.focus(); return; }
                currentConversation().messages.push({ role: 'user', content: value });
                decideTemporaryOperation(message, 'REJECT_WITH_FEEDBACK', value);
            });
            feedback.append(textarea, submit); card.appendChild(feedback);
        }
        const footer = document.createElement('div'); footer.className = 'operation-footer';
        const status = document.createElement('span'); status.textContent = actionStatus(action.status); footer.appendChild(status);
        if (addingCommand && action.status === 'PENDING_COMMAND_APPROVAL') {
            footer.append(actionButton('暂不添加', 'cancel-operation', () => cancelCommandProposal(message)),
                actionButton('添加命令', 'execute-operation', () => approveCommandProposal(message)));
        } else if (temporary && action.status === 'PENDING_APPROVAL') {
            footer.append(actionButton('取消任务', 'cancel-operation', () => cancelOperation(message)),
                actionButton('本对话允许此命令', 'remember-operation', () =>
                    decideTemporaryOperation(message, 'EXECUTE_AND_REMEMBER')),
                actionButton('执行一次', 'execute-operation', () =>
                    decideTemporaryOperation(message, 'EXECUTE_ONCE')));
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

    // 确认执行危险命令。确认后服务端会返回两种情况：
    //   result.success=true  -> 已执行，附执行结果 + continuationId（恢复会话继续原任务）
    //   result.success=false -> 执行结果不确定（可能网络中断），提示用户先核对服务器状态
    // 服务端对同一 actionId 只允许消费一次，双击只会有一个请求生效。
    async function approveOperation(message) {
        if (message.action?.status !== 'PENDING_APPROVAL') return;
        let resolved = false;
        streaming = true; updateStreamingState();
        message.action.status = 'PROCESSING'; renderMessages();
        try {
            const result = await api(`/api/server/v1/operations/${encodeURIComponent(message.action.actionId)}/approve`, { method: 'POST' });
            resolved = true;
            message.action.status = result.success ? 'EXECUTED' : 'FAILED';
            message.action.execution = result.execution; showToast(result.message, 6000);
            await continueAfterAction(message, result.continuationId);
        } catch (error) {
            // resolved=false 说明请求没到服务端（网络/401），动作还处于待确认，恢复按钮让用户重试；
            // 已 resolved 则服务端已消费 actionId，不能再恢复待确认状态。
            if (!resolved) message.action.status = 'PENDING_APPROVAL';
            handleApiError(error);
        }
        finally { streaming = false; persistConversations(); updateStreamingState(); renderAll(); }
    }

    // 临时命令审批是 ReAct 暂停点：执行或补充说明都会得到一次性续跑凭证，随后从同一
    // 会话继续；“本对话允许”只由服务端记录完全相同的目录和命令，前端不保存放行规则。
    async function decideTemporaryOperation(message, decision, feedback = null) {
        if (message.action?.status !== 'PENDING_APPROVAL'
            || message.action.actionType !== 'EXECUTE_TEMPORARY_COMMAND') return;
        let resolved = false;
        streaming = true; updateStreamingState();
        message.action.status = 'PROCESSING'; renderMessages();
        try {
            const result = await api(`/api/server/v1/operations/${encodeURIComponent(message.action.actionId)}/decide`, {
                method: 'POST', body: JSON.stringify({ decision, feedback })
            });
            resolved = true;
            message.action.status = result.status;
            message.action.execution = result.execution;
            showToast(result.message, 6000);
            await continueAfterAction(message, result.continuationId);
        } catch (error) {
            if (!resolved) message.action.status = 'PENDING_APPROVAL';
            handleApiError(error);
        } finally {
            streaming = false; persistConversations(); updateStreamingState(); renderAll();
        }
    }

    // 取消只作废服务端的一次性动作，不调用模型——用一个明确的收尾消息结束本轮。
    async function cancelOperation(message) {
        if (message.action?.status !== 'PENDING_APPROVAL') return;
        streaming = true; message.action.status = 'PROCESSING'; updateStreamingState(); renderMessages();
        try {
            await api(`/api/server/v1/operations/${encodeURIComponent(message.action.actionId)}`, { method: 'DELETE' });
            message.action.status = 'CANCELLED';
            finishCancelledAction('已取消执行命令，本次请求已结束，服务器未执行该操作。');
        } catch (error) {
            message.action.status = 'PENDING_APPROVAL'; handleApiError(error);
        } finally {
            streaming = false; persistConversations(); updateStreamingState(); renderAll();
        }
    }

    // 确认把模型提议的命令写入服务器配置。结果里的 command.id 是服务端生成的正式 ID，
    // 后续模型只能按这个 ID 执行。若设置面板正开着该服务器的命令列表，顺带刷新。
    async function approveCommandProposal(message) {
        if (message.action?.status !== 'PENDING_COMMAND_APPROVAL') return;
        let resolved = false;
        streaming = true; updateStreamingState();
        message.action.status = 'PROCESSING'; renderMessages();
        try {
            const result = await api(`/api/server/v1/command-proposals/${encodeURIComponent(message.action.actionId)}/approve`, { method: 'POST' });
            resolved = true;
            message.action.status = 'ADDED'; message.action.commandId = result.command?.id;
            if (editingServerId === message.action.serverId) {
                try { await loadCommands(editingServerId); }
                catch (error) { showToast(`命令已添加，但刷新配置列表失败：${error.message}`, 7000); }
            }
            showToast(result.message, 7000);
            await continueAfterAction(message, result.continuationId);
        } catch (error) {
            if (!resolved) message.action.status = 'PENDING_COMMAND_APPROVAL';
            handleApiError(error);
        }
        finally { streaming = false; persistConversations(); updateStreamingState(); renderAll(); }
    }

    async function cancelCommandProposal(message) {
        if (message.action?.status !== 'PENDING_COMMAND_APPROVAL') return;
        streaming = true; message.action.status = 'PROCESSING'; updateStreamingState(); renderMessages();
        try {
            await api(`/api/server/v1/command-proposals/${encodeURIComponent(message.action.actionId)}`, { method: 'DELETE' });
            message.action.status = 'CANCELLED';
            finishCancelledAction('已取消添加命令，本次请求已结束，服务器配置未发生变化。');
        } catch (error) {
            message.action.status = 'PENDING_COMMAND_APPROVAL'; handleApiError(error);
        } finally {
            streaming = false; persistConversations(); updateStreamingState(); renderAll();
        }
    }

    // 用服务端签发的一次性 continuationId 恢复同一模型会话（凭证绑定会话+服务器，只能消费一次）。
    // 这样模型能「看到」刚才确认动作的结果并继续原任务，而不需要用户重新描述上下文。
    async function continueAfterAction(actionMessage, continuationId) {
        const conversation = currentConversation();
        if (!conversation.messages.includes(actionMessage) || !continuationId) {
            conversation.messages.push({
                role: 'assistant',
                content: '操作已完成，但缺少对话续跑信息。你可以继续提问以获取结果。'
            });
            conversation.updatedAt = Date.now();
            return;
        }
        const assistantMessage = { role: 'assistant', content: '', streaming: true };
        conversation.messages.push(assistantMessage);
        conversation.messages = conversation.messages.slice(-MAX_MESSAGES);
        conversation.updatedAt = Date.now(); renderAll();
        try {
            const response = await fetch('/api/server/v1/messages/continue', {
                method: 'POST', headers: authHeaders(),
                body: JSON.stringify({
                    conversationId: conversation.id,
                    serverId: conversation.serverId,
                    continuationId,
                    modelId: conversation.modelId || null,
                    reasoningEffort: conversation.reasoningEffort || 'auto'
                })
            });
            if (!response.ok) throw await responseError(response);
            const renderer = createStreamRenderer(assistantMessage);
            await consumeSse(response, renderer.content, action => {
                assistantMessage.action = action; renderMessages();
            }, renderer.reasoning);
            renderer.flush();
            if (!assistantMessage.content) assistantMessage.content = '操作已完成，但暂时没有得到后续说明。';
        } catch (error) {
            assistantMessage.content = `操作已完成，但继续处理失败：${friendlyRequestError(error)}`;
            if (error.status === 401) logout();
        } finally {
            assistantMessage.streaming = false;
            conversation.updatedAt = Date.now();
        }
    }

    function finishCancelledAction(content) {
        const conversation = currentConversation();
        conversation.messages.push({ role: 'assistant', content });
        conversation.messages = conversation.messages.slice(-MAX_MESSAGES);
        conversation.updatedAt = Date.now();
        persistConversations(); renderAll();
    }

    function actionStatus(status) {
        return ({ PENDING_APPROVAL: '等待你的确认', PENDING_COMMAND_APPROVAL: '等待你确认添加', PROCESSING: '正在处理…',
            ADDED: '已添加到当前服务器', EXECUTED: '执行成功', EXECUTED_AND_REMEMBERED: '已执行 · 本对话已放行相同命令',
            REVISED: '已拒绝 · 正按补充说明继续', FAILED: '执行失败', CANCELLED: '已取消', SUPERSEDED: '已失效' })[status] || status;
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
        followConversationTail = true;
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

    // 批量删除本地会话时，必须同步清理服务端状态（模型记忆、会话-服务器绑定、待确认操作/提议），
        // 否则服务端会残留孤儿状态。逐个调 DELETE /conversations/{id}，部分失败时保留本地记录并提示重试。
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

    async function deleteConversation(conversation) {
        if (streaming || !window.confirm(`确定删除对话“${conversation.title}”吗？`)) return;
        try {
            await api(`/api/server/v1/conversations/${encodeURIComponent(conversation.id)}`, { method: 'DELETE' });
            const deletingCurrent = conversation.id === currentConversationId;
            conversations = conversations.filter(value => value.id !== conversation.id);
            if (!conversations.length) conversations.push(createConversation(conversation.serverId || servers.find(server => server.enabled)?.id || null));
            if (deletingCurrent) { currentConversationId = conversations[0].id; followConversationTail = true; }
            persistConversations(); renderAll(); showToast('对话已删除');
        } catch (error) { handleApiError(error); }
    }

    function createConversation(serverId = null) { return { id: crypto.randomUUID(), serverId, modelId: null, reasoningEffort: 'auto', title: '新对话', messages: [], updatedAt: Date.now() }; }
    function currentConversation() { return conversations.find(value => value.id === currentConversationId) || conversations[0]; }
    // 新消息发出后，把该会话待确认的动作全部置为已失效——
        // 服务端生成新动作时也会作废旧动作，前端保持一致，避免点击「执行」一个已不存在的选项。
        function expireActions(conversation) { conversation.messages.forEach(message => {
        if (['PENDING_APPROVAL', 'PENDING_COMMAND_APPROVAL'].includes(message.action?.status)) message.action.status = 'SUPERSEDED';
    }); }
    function loadConversations() {
        try {
            const value = JSON.parse(localStorage.getItem(CONVERSATIONS_KEY));
            return Array.isArray(value) ? value.slice(0, MAX_CONVERSATIONS).map(conversation => ({
                reasoningEffort: 'auto', ...conversation,
                messages: Array.isArray(conversation.messages) ? conversation.messages.map(message => ({ id: message.id || crypto.randomUUID(), ...message })) : []
            })) : [];
        } catch (_) { return []; }
    }
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
        elements.providerSelect.disabled = streaming;
        elements.modelSelect.disabled = streaming || !elements.providerSelect.value;
        elements.reasoningRange.disabled = streaming; elements.modelMenuButton.disabled = streaming;
        if (streaming) { closeServerMenu(); closeModelMenu(); }
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
    function friendlyRequestError(error) {
        return error instanceof TypeError || error?.message === 'network error'
            ? '连接被中断，请检查服务端日志后重试' : (error?.message || '未知错误');
    }
    function showToast(message, duration = 3500) { clearTimeout(toastTimer); elements.toast.textContent = message; elements.toast.hidden = false; toastTimer = setTimeout(() => { elements.toast.hidden = true; }, duration); }
})();
