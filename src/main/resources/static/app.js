let stompClient = null;
let currentRoomId = null;
let currentRoomSubscription = null;
let dmSubscription = null;
let isRegister = false;
let typingTimer = null;
let currentUser = null;

// ── Notifications ──
let audioCtx = null;

function getAudioCtx() {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    return audioCtx;
}

function playNotificationSound() {
    try {
        const ctx  = getAudioCtx();
        const t    = ctx.currentTime;

        // FB Messenger-style: two soft sine pings (C6 → E6)
        [[1046.5, 0], [1318.5, 0.1]].forEach(([freq, offset]) => {
            const osc  = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.type = 'sine';
            osc.frequency.value = freq;
            gain.gain.setValueAtTime(0, t + offset);
            gain.gain.linearRampToValueAtTime(0.8, t + offset + 0.01);
            gain.gain.exponentialRampToValueAtTime(0.001, t + offset + 0.4);
            osc.start(t + offset);
            osc.stop(t + offset + 0.4);
        });
    } catch (_) {}
}

function showBrowserNotification(title, body) {
    if (document.visibilityState === 'visible') return;
    if (Notification.permission !== 'granted') return;
    const n = new Notification(title, { body, silent: true });
    n.onclick = () => { window.focus(); n.close(); };
    setTimeout(() => n.close(), 5000);
}

function notify(title, body) {
    playNotificationSound();
    showBrowserNotification(title, body);
}

function requestNotificationPermission() {
    if ('Notification' in window && Notification.permission === 'default') {
        Notification.requestPermission();
    }
}

let reconnectAttempts = 0;
let reconnectTimer = null;
const WS_MAX_DELAY = 30000;

let presenceSubscription = null;
const onlineUsers = new Set();

// 'room' | 'dm'
let currentMode = 'room';
let currentDmPartnerId = null;

// Map<partnerId (number), {id, username, displayName, unread}>
const dmPartners = new Map();

let dmSearchTimer = null;

// ─── Auth ─────────────────────────────────────────────────────────────────────

function toggleAuth() {
    isRegister = !isRegister;
    document.getElementById('auth-title').textContent = isRegister ? 'Register' : 'Login';
    document.getElementById('auth-btn').textContent   = isRegister ? 'Register' : 'Login';
    document.getElementById('email').style.display    = isRegister ? 'block'    : 'none';
    document.getElementById('auth-toggle').innerHTML  = isRegister
        ? 'Already have an account? <span onclick="toggleAuth()">Login</span>'
        : 'Don\'t have an account? <span onclick="toggleAuth()">Register</span>';
    document.getElementById('error-msg').textContent = '';
}

async function doAuth() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const email    = document.getElementById('email').value.trim();

    if (!username || !password || (isRegister && !email)) {
        showError('Please fill in all fields');
        return;
    }

    const path = isRegister ? '/api/auth/register' : '/api/auth/login';
    const body = isRegister ? { username, email, password } : { username, password };

    try {
        const res  = await fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const data = await res.json();

        if (!data.success) {
            showError(data.message);
            return;
        }

        localStorage.setItem('accessToken',  data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);
        await loadApp();
    } catch (e) {
        showError('Connection error');
    }
}

async function doLogout() {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
    reconnectAttempts = 0;

    const rt = localStorage.getItem('refreshToken');
    if (rt) {
        await apiFetch('/api/auth/logout', {
            method: 'POST',
            body: JSON.stringify({ refreshToken: rt }),
        });
    }
    localStorage.clear();
    if (stompClient) stompClient.disconnect();
    location.reload();
}

function showError(msg) {
    document.getElementById('error-msg').textContent = msg;
}

// ─── API ──────────────────────────────────────────────────────────────────────

async function apiFetch(path, options = {}) {
    const token = localStorage.getItem('accessToken');
    const headers = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(options.headers || {}),
    };

    let res = await fetch(path, { ...options, headers });

    if (res.status === 401) {
        const refreshed = await tryRefresh();
        if (!refreshed) {
            doLogout();
            return null;
        }
        headers.Authorization = 'Bearer ' + localStorage.getItem('accessToken');
        res = await fetch(path, { ...options, headers });
    }

    return res.json();
}

async function tryRefresh() {
    const rt = localStorage.getItem('refreshToken');
    if (!rt) return false;

    const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: rt }),
    });

    if (!res.ok) return false;

    const data = await res.json();
    if (!data.success) return false;

    localStorage.setItem('accessToken',  data.data.accessToken);
    localStorage.setItem('refreshToken', data.data.refreshToken);
    return true;
}

// ─── App init ─────────────────────────────────────────────────────────────────

async function loadApp() {
    const meRes = await apiFetch('/api/users/me');
    if (!meRes || !meRes.success) {
        doLogout();
        return;
    }

    currentUser = meRes.data;
    requestNotificationPermission();
    document.getElementById('user-display').textContent  = currentUser.displayName || currentUser.username;
    document.getElementById('auth-section').style.display = 'none';
    document.getElementById('app-section').style.display  = 'flex';

    await loadRooms();
    await loadDmConversations();
    connectWebSocket();
}

// ─── Sidebar tabs ─────────────────────────────────────────────────────────────

function showTab(tab) {
    document.getElementById('tab-panel-rooms').classList.toggle('hidden', tab !== 'rooms');
    document.getElementById('tab-panel-dms').classList.toggle('hidden',  tab !== 'dms');
    document.getElementById('tab-btn-rooms').classList.toggle('active',  tab === 'rooms');
    document.getElementById('tab-btn-dms').classList.toggle('active',    tab === 'dms');
    if (tab === 'dms') clearDmBadge();
}

// ─── Rooms ────────────────────────────────────────────────────────────────────

async function loadRooms() {
    const res = await apiFetch('/api/rooms');
    if (!res || !res.success) return;

    const list = document.getElementById('room-list');
    list.innerHTML = '';

    res.data.forEach(room => {
        const div = document.createElement('div');
        div.className    = 'room-item' + (room.id === currentRoomId ? ' active' : '');
        div.dataset.roomId = room.id;
        div.innerHTML    = `
            <div class="item-name">${escHtml(room.name)}</div>
            <div class="item-meta">${room.type} · ${room.memberCount} members</div>
        `;
        div.onclick = () => openRoom(room);
        list.appendChild(div);
    });
}

async function createRoom() {
    const name = prompt('Room name:');
    if (!name) return;

    const type = confirm('Make it private? (Cancel = Public)') ? 'PRIVATE' : 'PUBLIC';
    await apiFetch('/api/rooms', {
        method: 'POST',
        body: JSON.stringify({ name, type }),
    });
    await loadRooms();
}

async function openRoom(room) {
    currentMode         = 'room';
    currentRoomId       = room.id;
    currentDmPartnerId  = null;

    document.querySelectorAll('.room-item').forEach(el => el.classList.remove('active'));
    const el = document.querySelector(`[data-room-id="${room.id}"]`);
    if (el) el.classList.add('active');
    document.querySelectorAll('.dm-item').forEach(el => el.classList.remove('active'));

    showChatMain(room.name, null);
    document.getElementById('typing-indicator').style.display = 'block';

    if (!room.isMember) {
        await apiFetch(`/api/rooms/${room.id}/join`, { method: 'POST' });
        room.isMember = true;
    }

    const res = await apiFetch(`/api/rooms/${room.id}/messages?page=0&size=50`);
    if (res && res.success) {
        [...res.data.content].reverse().forEach(m => appendMessage(m));
        scrollToBottom();
    }

    if (currentRoomSubscription) currentRoomSubscription.unsubscribe();
    if (stompClient && stompClient.connected) subscribeToRoom(room.id);
}

// ─── Direct Messages ──────────────────────────────────────────────────────────

async function loadDmConversations() {
    const res = await apiFetch('/api/dm/conversations');
    if (!res || !res.success) return;
    res.data.forEach(user => {
        if (!dmPartners.has(user.id)) {
            dmPartners.set(user.id, { id: user.id, username: user.username, displayName: user.displayName, unread: 0 });
        }
    });
    renderDmSidebar();
}

function onDmSearchInput() {
    const q = document.getElementById('dm-user-search-input').value.trim();
    clearTimeout(dmSearchTimer);
    if (!q) { hideDmSearch(); return; }
    dmSearchTimer = setTimeout(() => fetchDmSearch(q), 250);
}

async function fetchDmSearch(q) {
    const res = await apiFetch('/api/users/search?q=' + encodeURIComponent(q));
    const dropdown = document.getElementById('dm-search-results');
    dropdown.innerHTML = '';
    if (!res || !res.success || res.data.length === 0) {
        dropdown.innerHTML = '<div class="dm-search-empty">No users found</div>';
        dropdown.style.display = 'block';
        return;
    }
    res.data
        .filter(u => u.id !== currentUser?.id)
        .forEach(user => {
            const name    = user.displayName || user.username;
            const initial = name.charAt(0).toUpperCase();
            const item    = document.createElement('div');
            item.className = 'dm-search-item';
            item.innerHTML = `
                <div class="dm-search-avatar">${escHtml(initial)}</div>
                <div class="dm-search-info">
                    <div class="dm-search-name">${escHtml(name)}</div>
                    <div class="dm-search-sub">@${escHtml(user.username)}</div>
                </div>`;
            item.onmousedown = () => selectDmUser(user);
            dropdown.appendChild(item);
        });
    dropdown.style.display = 'block';
}

function hideDmSearch() {
    setTimeout(() => {
        const dropdown = document.getElementById('dm-search-results');
        if (dropdown) dropdown.style.display = 'none';
    }, 150);
}

function selectDmUser(user) {
    document.getElementById('dm-user-search-input').value = '';
    document.getElementById('dm-search-results').style.display = 'none';
    if (!dmPartners.has(user.id)) {
        dmPartners.set(user.id, { id: user.id, username: user.username, displayName: user.displayName, unread: 0 });
        renderDmSidebar();
    }
    openDm(user.id);
}

async function openDm(partnerId) {
    currentMode        = 'dm';
    currentDmPartnerId = partnerId;
    currentRoomId      = null;

    document.querySelectorAll('.dm-item').forEach(el => el.classList.remove('active'));
    const el = document.querySelector(`[data-dm-partner-id="${partnerId}"]`);
    if (el) el.classList.add('active');
    document.querySelectorAll('.room-item').forEach(el => el.classList.remove('active'));

    const partner     = dmPartners.get(partnerId);
    const partnerName = partner?.displayName || partner?.username || ('User #' + partnerId);
    showChatMain(partnerName, 'DM');
    document.getElementById('typing-indicator').style.display = 'none';

    if (partner) {
        partner.unread = 0;
        renderDmSidebar();
    }
    updateDmBadge();

    const res = await apiFetch(`/api/dm/${partnerId}/messages?page=0&size=50`);
    if (res && res.success) {
        [...res.data.content].reverse().forEach(m => appendDmMessage(m));
        scrollToBottom();
    }
}

function appendDmMessage(msg) {
    const container = document.getElementById('messages');
    const isMe      = currentUser && msg.senderId === currentUser.id;
    const div       = document.createElement('div');
    div.className   = 'message ' + (isMe ? 'mine' : 'other');
    div.innerHTML   = `
        ${makeSenderHtml(msg.senderDisplayName, msg.senderUsername, msg.senderId)}
        ${escHtml(msg.content)}
        <div class="msg-time">${formatTime(msg.createdAt)}</div>
    `;
    container.appendChild(div);
}

function sendDm() {
    const input   = document.getElementById('message-input');
    const content = input.value.trim();
    if (!content || !currentDmPartnerId || !stompClient || !stompClient.connected) return;

    stompClient.send('/app/dm.send', {}, JSON.stringify({ recipientId: currentDmPartnerId, content }));
    input.value = '';
    input.focus();
}

function handleDmEvent(event) {
    const msg             = event.message;
    const isMe            = currentUser && msg.senderId === currentUser.id;
    const partnerId       = isMe ? msg.recipientId   : msg.senderId;
    const partnerUsername = isMe ? msg.recipientUsername : msg.senderUsername;

    // Register partner on first contact
    if (!dmPartners.has(partnerId)) {
        dmPartners.set(partnerId, { id: partnerId, username: partnerUsername, displayName: null, unread: 0 });
    } else {
        dmPartners.get(partnerId).username = partnerUsername;
    }

    const isActiveConversation = currentMode === 'dm' && currentDmPartnerId === partnerId;

    if (!isMe) notify(`💬 ${partnerUsername}`, msg.content);

    if (isActiveConversation) {
        appendDmMessage(msg);
        scrollToBottom();
    } else if (!isMe) {
        dmPartners.get(partnerId).unread = (dmPartners.get(partnerId).unread || 0) + 1;
        updateDmBadge();
        // Auto-switch to DMs tab so badge is visible
        const dmTabActive = document.getElementById('tab-btn-dms').classList.contains('active');
        if (!dmTabActive) bumpDmBadge();
    }

    renderDmSidebar();
}

function renderDmSidebar() {
    const list = document.getElementById('dm-list');
    list.innerHTML = '';

    dmPartners.forEach((partner, partnerId) => {
        const name     = partner.displayName || partner.username;
        const initial  = name.charAt(0).toUpperCase();
        const isActive = currentMode === 'dm' && currentDmPartnerId === partnerId;

        const div = document.createElement('div');
        div.className         = 'dm-item' + (isActive ? ' active' : '');
        div.dataset.dmPartnerId = partnerId;
        div.innerHTML         = `
            <div class="dm-avatar-wrap">
                <div class="dm-avatar">${escHtml(initial)}</div>
                <span class="dm-presence${onlineUsers.has(partnerId) ? ' online' : ''}"></span>
            </div>
            <div class="dm-info">
                <div class="item-name">${escHtml(name)}</div>
                <div class="item-meta">ID: ${partnerId}</div>
            </div>
            ${partner.unread > 0 ? '<div class="dm-unread"></div>' : ''}
        `;
        div.onclick = () => { showTab('dms'); openDm(partnerId); };
        list.appendChild(div);
    });
}

function bumpDmBadge() {
    const badge = document.getElementById('dm-badge');
    const total = [...dmPartners.values()].reduce((s, p) => s + (p.unread || 0), 0);
    badge.textContent  = total;
    badge.style.display = total > 0 ? 'inline-block' : 'none';
}

function updateDmBadge() {
    bumpDmBadge();
}

function clearDmBadge() {
    dmPartners.forEach(p => { p.unread = 0; });
    document.getElementById('dm-badge').style.display = 'none';
}

// ─── Shared chat UI ───────────────────────────────────────────────────────────

function showChatMain(title, badgeText) {
    document.getElementById('no-room').style.display    = 'none';
    document.getElementById('chat-main').style.display  = 'flex';
    document.getElementById('chat-header-title').textContent = title;
    document.getElementById('messages').innerHTML        = '';

    const badge = document.getElementById('chat-header-badge');
    if (badgeText) {
        badge.textContent   = badgeText;
        badge.style.display = 'inline-block';
    } else {
        badge.style.display = 'none';
    }
}

function makeSenderHtml(displayName, username, senderId) {
    const isOnline = onlineUsers.has(senderId);
    return `<div class="sender">
        <span class="msg-online-dot${isOnline ? ' online' : ''}" data-uid="${senderId}"></span>
        ${escHtml(displayName || username)}
    </div>`;
}

function appendMessage(msg) {
    const container = document.getElementById('messages');
    const isMe      = currentUser && msg.senderId === currentUser.id;
    const isSystem  = msg.type === 'SYSTEM';
    const div       = document.createElement('div');

    div.className = 'message' + (isSystem ? ' system' : isMe ? ' mine' : ' other');
    div.innerHTML = isSystem
        ? escHtml(msg.content)
        : `${makeSenderHtml(msg.senderDisplayName, msg.senderUsername, msg.senderId)}
           ${escHtml(msg.content)}
           <div class="msg-time">${formatTime(msg.createdAt)}</div>`;

    container.appendChild(div);
}

function sendMessage() {
    if (currentMode === 'dm') { sendDm(); return; }

    const input   = document.getElementById('message-input');
    const content = input.value.trim();
    if (!content || !currentRoomId || !stompClient || !stompClient.connected) return;

    stompClient.send('/app/chat.send', {}, JSON.stringify({ roomId: currentRoomId, content, type: 'TEXT' }));
    input.value = '';
    input.focus();
    sendTyping(false);
}

function onKeyPress(e) {
    if (e.key === 'Enter') sendMessage();
}

function onTyping() {
    if (currentMode === 'dm') return;
    sendTyping(true);
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => sendTyping(false), 2000);
}

function sendTyping(isTyping) {
    if (!stompClient || !stompClient.connected || !currentRoomId) return;
    stompClient.send('/app/chat.typing', {}, JSON.stringify({ roomId: currentRoomId, typing: isTyping }));
}

function scrollToBottom() {
    const msgs    = document.getElementById('messages');
    msgs.scrollTop = msgs.scrollHeight;
}

// ─── WebSocket ────────────────────────────────────────────────────────────────

function connectWebSocket() {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const socket = new SockJS('/ws/chat');
    stompClient  = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect(
        { Authorization: 'Bearer ' + token },
        onWsConnected,
        onWsError
    );
}

function onWsConnected() {
    reconnectAttempts = 0;
    setWsStatus('connected');
    subscribeToPresence();
    if (currentRoomId) subscribeToRoom(currentRoomId);
    subscribeToDMs();
}

async function onWsError() {
    stompClient = null;
    presenceSubscription = null;
    onlineUsers.clear();
    renderDmSidebar();
    setWsStatus('disconnected');

    // Token may have expired — try to refresh before reconnecting
    await tryRefresh();

    if (!localStorage.getItem('accessToken')) {
        doLogout();
        return;
    }

    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), WS_MAX_DELAY);
    reconnectAttempts++;
    setWsStatus('reconnecting', delay);
    reconnectTimer = setTimeout(connectWebSocket, delay);
}

function setWsStatus(status, delayMs) {
    const el  = document.getElementById('ws-status');
    const dot = document.getElementById('user-dot');
    if (dot) dot.className = 'presence-dot ' + (status === 'connected' ? 'online' : '');
    if (!el) return;
    if (status === 'connected') {
        el.textContent = '';
        el.className = 'ws-status';
    } else if (status === 'disconnected') {
        el.textContent = 'Disconnected';
        el.className = 'ws-status disconnected';
    } else if (status === 'reconnecting') {
        el.textContent = `Reconnecting in ${Math.round(delayMs / 1000)}s…`;
        el.className = 'ws-status reconnecting';
    }
}

function subscribeToPresence() {
    if (presenceSubscription) presenceSubscription.unsubscribe();
    presenceSubscription = stompClient.subscribe(
        '/topic/presence',
        frame => handlePresenceEvent(JSON.parse(frame.body))
    );
}

function handlePresenceEvent(wsMsg) {
    if (wsMsg.extra) {
        onlineUsers.add(wsMsg.userId);
    } else {
        onlineUsers.delete(wsMsg.userId);
    }
    document.querySelectorAll(`.msg-online-dot[data-uid="${wsMsg.userId}"]`).forEach(dot => {
        dot.classList.toggle('online', !!wsMsg.extra);
    });
    if (dmPartners.has(wsMsg.userId)) renderDmSidebar();
}

function subscribeToRoom(roomId) {
    if (currentRoomSubscription) currentRoomSubscription.unsubscribe();
    currentRoomSubscription = stompClient.subscribe(
        '/topic/rooms/' + roomId,
        frame => handleRoomMessage(JSON.parse(frame.body))
    );
}

function subscribeToDMs() {
    if (dmSubscription) dmSubscription.unsubscribe();
    dmSubscription = stompClient.subscribe(
        '/user/queue/direct-messages',
        frame => handleDmEvent(JSON.parse(frame.body))
    );
}

function handleRoomMessage(wsMsg) {
    switch (wsMsg.eventType) {
        case 'CHAT_MESSAGE':
        case 'SYSTEM_MESSAGE':
            if (wsMsg.roomId === currentRoomId) {
                appendMessage(wsMsg.message);
                scrollToBottom();
            }
            if (wsMsg.eventType === 'CHAT_MESSAGE' && wsMsg.message?.senderId !== currentUser?.id) {
                const sender = wsMsg.message?.senderDisplayName || wsMsg.message?.senderUsername || 'Someone';
                notify(`#${wsMsg.roomName || wsMsg.roomId} — ${sender}`, wsMsg.message?.content || '');
            }
            break;

        case 'TYPING':
            if (wsMsg.roomId === currentRoomId && wsMsg.userId !== currentUser?.id) {
                const indicator = document.getElementById('typing-indicator');
                indicator.textContent = wsMsg.extra ? `${escHtml(wsMsg.username)} is typing…` : '';
                if (wsMsg.extra) {
                    clearTimeout(indicator._timer);
                    indicator._timer = setTimeout(() => { indicator.textContent = ''; }, 3000);
                }
            }
            break;
    }
}

// ─── Utils ────────────────────────────────────────────────────────────────────

function escHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function formatTime(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    if (isNaN(d)) return '';
    const now   = new Date();
    const hh    = String(d.getHours()).padStart(2, '0');
    const mm    = String(d.getMinutes()).padStart(2, '0');
    const sameDay = d.getFullYear() === now.getFullYear()
                 && d.getMonth()    === now.getMonth()
                 && d.getDate()     === now.getDate();
    if (sameDay) return `${hh}:${mm}`;
    return `${d.getDate()}/${d.getMonth() + 1} ${hh}:${mm}`;
}

// ─── Boot ─────────────────────────────────────────────────────────────────────

window.onload = async () => {
    if (localStorage.getItem('accessToken')) await loadApp();
};
