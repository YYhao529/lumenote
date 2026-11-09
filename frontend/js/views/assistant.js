/** 智能助手：与 AIGC 服务（DeepSeek）流式对话 */

import { CONFIG } from '../config.js';
import { auth } from '../store.js';
import { icons } from '../icons.js';
import { navigate } from '../router.js';
import { escapeHtml } from '../utils.js';
import { toast } from '../ui.js';

const SESSION_KEY = 'lumenote:aigc:session';

const SUGGESTIONS = [
  { icon: 'sparkles', text: '帮我想 5 个「周末探店」的笔记标题' },
  { icon: 'pencil', text: '把「今天去了一家很棒的咖啡店」扩写成 200 字笔记' },
  { icon: 'search', text: '给我这篇笔记想 3 个合适的话题标签' },
];

let state = null;

function apiBase() {
  return (CONFIG.apiBase || '').replace(/\/+$/, '');
}

function newSessionId() {
  try {
    return crypto.randomUUID ? `s-${crypto.randomUUID()}` : `s-${Date.now().toString(36)}`;
  } catch (err) {
    return `s-${Date.now().toString(36)}`;
  }
}

function currentSessionId() {
  try {
    let id = sessionStorage.getItem(SESSION_KEY);
    if (!id) {
      id = newSessionId();
      sessionStorage.setItem(SESSION_KEY, id);
    }
    return id;
  } catch (err) {
    return newSessionId();
  }
}

/** 极简 Markdown 渲染：代码块 / 行内代码 / 加粗 / 列表 / 换行 */
function renderMarkdown(text) {
  let html = escapeHtml(text || '');

  html = html.replace(/```(?:\w+)?\n([\s\S]*?)```/g, (m, code) => `<pre class="md-code">${code.replace(/\n$/, '')}</pre>`);
  html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>');
  html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/^[-*]\s+(.+)$/gm, '<span class="md-li">• $1</span>');

  return html.replace(/\n/g, '<br>');
}

function messageHtml(msg) {
  if (msg.role === 'user') {
    return `<div class="msg msg-user">
      <div class="msg-bubble">${escapeHtml(msg.content)}</div>
    </div>`;
  }

  const body = msg.content
    ? renderMarkdown(msg.content)
    : (msg.streaming ? '<span class="msg-thinking">正在思考…</span>' : '');

  return `<div class="msg msg-ai">
    <div class="msg-avatar">${icons.sparkles()}</div>
    <div class="msg-bubble ${msg.error ? 'is-error' : ''}">${body}${msg.streaming && msg.content ? '<span class="msg-cursor"></span>' : ''}</div>
  </div>`;
}

function emptyStateHtml() {
  return `<div class="assistant-empty">
    <div class="assistant-empty-icon">${icons.sparkles()}</div>
    <p class="assistant-empty-title">小拾能帮你做这些</p>
    <div class="assistant-suggestions">
      ${SUGGESTIONS.map(
        (item, idx) => `<button class="suggestion" data-suggestion="${idx}">
          <span class="suggestion-icon">${icons[item.icon] ? icons[item.icon]() : icons.sparkles()}</span>
          <span>${escapeHtml(item.text)}</span>
        </button>`
      ).join('')}
    </div>
  </div>`;
}

export default {
  async mount(root, ctx) {
    if (!auth.isLoggedIn) {
      navigate('/login', { redirect: '/assistant' }, { replace: true });
      return;
    }

    state = {
      sessionId: currentSessionId(),
      messages: [],
      streaming: false,
      abort: null,
      renderPending: false,
    };

    root.innerHTML = `<div class="assistant-page">
      <header class="assistant-head">
        <div class="assistant-head-left">
          <span class="assistant-logo">${icons.sparkles()}</span>
          <div>
            <h1>小拾 · 智能助手</h1>
            <p>写文案、起标题、想话题，随时问</p>
          </div>
        </div>
        <button class="btn btn-ghost btn-sm" id="assistant-clear" type="button">清空对话</button>
      </header>

      <div class="assistant-messages" id="assistant-messages"></div>

      <form class="assistant-composer" id="assistant-form">
        <textarea id="assistant-input" rows="1" maxlength="2000"
          placeholder="想让小拾帮你写点什么？Enter 发送，Shift + Enter 换行"></textarea>
        <button class="btn btn-primary" id="assistant-send" type="submit">发送</button>
      </form>
    </div>`;

    const listEl = root.querySelector('#assistant-messages');
    const formEl = root.querySelector('#assistant-form');
    const inputEl = root.querySelector('#assistant-input');
    const sendEl = root.querySelector('#assistant-send');
    const clearEl = root.querySelector('#assistant-clear');

    const render = () => {
      state.renderPending = false;
      if (!state.messages.length) {
        listEl.innerHTML = emptyStateHtml();
      } else {
        listEl.innerHTML = state.messages.map(messageHtml).join('');
      }
      listEl.scrollTop = listEl.scrollHeight;
    };

    const scheduleRender = () => {
      if (state.renderPending) return;
      state.renderPending = true;
      requestAnimationFrame(render);
    };

    const updateSendState = () => {
      sendEl.disabled = state.streaming;
      sendEl.textContent = state.streaming ? '生成中…' : '发送';
    };

    const autoGrow = () => {
      inputEl.style.height = 'auto';
      inputEl.style.height = `${Math.min(inputEl.scrollHeight, 160)}px`;
    };

    const handleFrame = (frame, aiMsg) => {
      const lines = frame.split('\n');
      let event = 'message';
      const dataLines = [];
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
      }
      const data = dataLines.join('\n');

      if (event === 'error') {
        aiMsg.error = true;
        aiMsg.content = data || 'AI 服务暂时不可用，请稍后再试';
        return;
      }
      if (data) aiMsg.content += data;
    };

    const send = async (text) => {
      const message = String(text || '').trim();
      if (!message || state.streaming) return;

      state.messages.push({ role: 'user', content: message });
      const aiMsg = { role: 'assistant', content: '', streaming: true, error: false };
      state.messages.push(aiMsg);

      state.streaming = true;
      inputEl.value = '';
      autoGrow();
      updateSendState();
      render();

      const controller = new AbortController();
      state.abort = controller;

      try {
        const res = await fetch(`${apiBase()}/aigc/chat/stream`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json;charset=UTF-8',
            ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
          },
          body: JSON.stringify({ sessionId: state.sessionId, message }),
          signal: controller.signal,
          cache: 'no-store',
        });

        if (res.status === 401) throw new Error('登录状态已失效，请重新登录');
        if (!res.ok) throw new Error(`服务返回 ${res.status}`);
        if (!res.body) throw new Error('当前浏览器不支持流式响应');

        const reader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          let idx;
          while ((idx = buffer.indexOf('\n\n')) !== -1) {
            handleFrame(buffer.slice(0, idx), aiMsg);
            buffer = buffer.slice(idx + 2);
          }
          scheduleRender();
        }
        if (buffer.trim()) handleFrame(buffer, aiMsg);
      } catch (err) {
        if (err.name === 'AbortError') return;
        aiMsg.error = true;
        aiMsg.content = aiMsg.content || `请求失败：${err.message}`;
      } finally {
        aiMsg.streaming = false;
        state.streaming = false;
        state.abort = null;
        updateSendState();
        render();
      }
    };

    const clearChat = async () => {
      const previousSessionId = state.sessionId;
      state.messages = [];
      state.sessionId = newSessionId();
      try {
        sessionStorage.setItem(SESSION_KEY, state.sessionId);
      } catch (err) {
        /* ignore */
      }
      render();
      try {
        await fetch(`${apiBase()}/aigc/chat/clear`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json;charset=UTF-8',
            ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
          },
          body: JSON.stringify({ sessionId: previousSessionId }),
        });
      } catch (err) {
        /* 清空失败不影响本地视图 */
      }
      toast('对话已清空');
    };

    listEl.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-suggestion]');
      if (!btn) return;
      const item = SUGGESTIONS[Number(btn.dataset.suggestion)];
      if (item) send(item.text);
    });

    formEl.addEventListener('submit', (event) => {
      event.preventDefault();
      send(inputEl.value);
    });

    inputEl.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        send(inputEl.value);
      }
    });

    inputEl.addEventListener('input', autoGrow);
    clearEl.addEventListener('click', clearChat);

    ctx.onCleanup(() => {
      state?.abort?.abort();
      state = null;
    });

    render();
    updateSendState();
    inputEl.focus({ preventScroll: true });
  },
};
