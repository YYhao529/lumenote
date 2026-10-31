/**
 * 应用入口：全局壳层、登录态、导航、路由启动。
 */

import { auth, onAuthChange } from './store.js';
import { logout, resolveMe } from './session.js';
import { api } from './api.js';
import { isFileProtocol } from './config.js';
import { icons } from './icons.js';
import { setInteraction } from './local.js';
import { avatar, initNoteVideoPreview, toast } from './ui.js';
import { escapeHtml } from './utils.js';
import { navigate, onRouteChange, start } from './router.js';

/* ------------------------------- 顶部登录态 ------------------------------- */

const THEME_KEY = 'lumenote:theme';

function getTheme() {
  try {
    return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light';
  } catch (err) {
    return 'light';
  }
}

function syncThemeOptions() {
  const current = getTheme();
  document.querySelectorAll('[data-theme-set]').forEach((button) => {
    button.classList.toggle('is-active', button.dataset.themeSet === current);
    button.setAttribute('aria-pressed', String(button.dataset.themeSet === current));
  });
}

function applyTheme(theme) {
  const next = theme === 'dark' ? 'dark' : 'light';

  if (next === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
  else document.documentElement.removeAttribute('data-theme');

  try {
    localStorage.setItem(THEME_KEY, next);
  } catch (err) {
    /* 隐私模式下仅当前页面生效 */
  }

  syncThemeOptions();
  toast(next === 'dark' ? '已切换为深色主题' : '已切换为浅色主题', 'success');
}

function closeUserMenu() {
  document.querySelector('.user-menu')?.classList.remove('is-open');
  document.querySelector('.user-menu-trigger')?.setAttribute('aria-expanded', 'false');
}

function renderAuthSlot() {
  const slot = document.getElementById('auth-slot');
  if (!slot) return;

  // 登录页不再重复展示登录按钮
  if (window.location.hash.startsWith('#/login')) {
    slot.innerHTML = '';
    return;
  }

  if (auth.isLoggedIn && auth.me) {
    slot.innerHTML = `<div class="user-menu">
      <button class="user-menu-trigger" type="button" aria-haspopup="true" aria-expanded="false">
        ${avatar(auth.me, 'sm')}
        <span class="auth-name">${escapeHtml(auth.me.nickname || '我的主页')}</span>
      </button>
      <div class="user-menu-pop" role="menu">
        <button class="user-menu-item" type="button" role="menuitem" data-menu="profile">
          ${icons.user()} 我的主页
        </button>
        <button class="user-menu-item" type="button" role="menuitem" data-menu="edit">
          ${icons.edit()} 编辑资料
        </button>
        <div class="user-menu-group">
          <span class="user-menu-label">更换主题</span>
          <div class="theme-switch" role="group" aria-label="主题">
            <button class="theme-option" type="button" data-theme-set="light">
              ${icons.sun ? icons.sun() : ''} 浅色
            </button>
            <button class="theme-option" type="button" data-theme-set="dark">
              ${icons.moon ? icons.moon() : ''} 深色
            </button>
          </div>
        </div>
        <button class="user-menu-item is-danger" type="button" role="menuitem" data-menu="logout">
          ${icons.logout()} 退出登录
        </button>
      </div>
    </div>`;

    syncThemeOptions();
    return;
  }

  if (auth.isLoggedIn) {
    slot.innerHTML = '<a class="btn btn-outline btn-sm" href="#/me">我的</a>';
    return;
  }

  slot.innerHTML = '<a class="btn btn-outline btn-sm" href="#/login">登录</a>';
}

/* ----------------------------- 头像菜单交互 ------------------------------ */

async function handleAuthMenuClick(event) {
  const trigger = event.target.closest('.user-menu-trigger');
  if (trigger) {
    const menu = trigger.closest('.user-menu');
    const open = !menu.classList.contains('is-open');
    menu.classList.toggle('is-open', open);
    trigger.setAttribute('aria-expanded', String(open));
    return;
  }

  const themeButton = event.target.closest('[data-theme-set]');
  if (themeButton) {
    applyTheme(themeButton.dataset.themeSet);
    return;
  }

  const item = event.target.closest('[data-menu]');
  if (!item) return;

  const action = item.dataset.menu;
  closeUserMenu();

  if (action === 'profile') {
    navigate('/me');
  } else if (action === 'edit') {
    navigate('/me', { edit: 1 });
  } else if (action === 'logout') {
    await logout();
    toast('已退出登录', 'success');
    navigate('/explore', {}, { replace: true });
  }
}

/* --------------------------------- 环境提示 ------------------------------- */

function mountEnvWarning() {
  if (!isFileProtocol()) return;
  const main = document.getElementById('view');
  if (!main) return;
  const warning = document.createElement('div');
  warning.className = 'notice notice-brand';
  warning.style.margin = '16px auto 0';
  warning.style.maxWidth = '1240px';
  warning.innerHTML = `${icons.alert()}<div>
    <strong>请通过本地服务器访问本项目。</strong>
    当前是 <code>file://</code> 方式，浏览器会阻止接口请求。请在 <code>frontend</code> 目录执行
    <code>node server.js</code>，然后打开 <code>http://127.0.0.1:5180</code>。
  </div>`;
  main.parentNode?.insertBefore(warning, main);
}

/* --------------------------------- 事件绑定 ------------------------------- */

function bindHeaderSearch() {
  const form = document.getElementById('header-search');
  if (!form) return;

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const input = document.getElementById('header-search-input');
    const keyword = String(input?.value || '').trim();
    navigate('/search', keyword ? { q: keyword } : {});
    input?.blur();
  });
}

function bindUnauthorized() {
  window.addEventListener('lumenote:unauthorized', (event) => {
    const wasLoggedIn = auth.isLoggedIn;
    auth.clearSession();

    if (window.location.hash.startsWith('#/login')) return;

    toast(event.detail?.message || '登录状态已失效，请重新登录', 'warning');
    if (wasLoggedIn) {
      const current = window.location.hash.replace(/^#/, '') || '/explore';
      navigate('/login', { redirect: current });
    }
  });
}

function bindImageFallback() {
  // 图片加载失败时移除 <img>，露出底层的渐变 / 首字母占位
  document.addEventListener(
    'error',
    (event) => {
      const target = event.target;
      if (target instanceof HTMLImageElement && target.dataset.fallback !== undefined) {
        target.remove();
      }
    },
    true
  );
}

function bindSkipLink() {
  document.querySelector('.skip-link')?.addEventListener('click', (event) => {
    event.preventDefault();
    const view = document.getElementById('view');
    view?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    view?.focus({ preventScroll: true });
  });
}

function bindHeaderMenu() {
  const menu = document.getElementById('header-menu');
  if (!menu) return;

  menu.querySelectorAll('.header-menu-panel a').forEach((link) => {
    link.addEventListener('click', () => {
      menu.open = false;
    });
  });

  document.addEventListener('click', (event) => {
    if (menu.open && !menu.contains(event.target)) menu.open = false;
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menu.open) menu.open = false;
  });
}

function bindKeyboardShortcuts() {
  document.addEventListener('keydown', (event) => {
    if (event.metaKey || event.ctrlKey || event.altKey) return;

    const target = event.target;
    const isTyping =
      target &&
      (target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.tagName === 'SELECT' ||
        target.isContentEditable);

    if (isTyping) return;

    // 桌面端快捷搜索：按 / 聚焦顶部搜索框
    if (event.key === '/') {
      const input = document.getElementById('header-search-input');
      if (input && input.offsetParent !== null) {
        event.preventDefault();
        input.focus();
        input.select();
      }
    }
  });
}

function syncHeaderSearch({ name, query }) {
  const input = document.getElementById('header-search-input');
  if (!input) return;
  if (name === 'search') input.value = query?.q || '';
  else if (document.activeElement !== input) input.value = '';
}

/* ------------------------------ 点赞状态同步 ------------------------------ */

let likedSyncTimer = null;
let likedSyncCooldownUntil = 0;

/**
 * 用服务端状态校准卡片上的红心：
 * 本地点赞记录只覆盖当前浏览器，换设备后靠这个接口补齐。
 */
function syncLikedStates() {
  // 接口异常时冷却 30 秒，避免频繁重试
  if (Date.now() < likedSyncCooldownUntil) return;

  const cards = [...document.querySelectorAll('.note-card[data-note-id]')].filter(
    (card) => card.dataset.likedSynced !== '1'
  );
  if (!cards.length) return;

  // 先标记，避免同一批卡片重复请求
  cards.forEach((card) => {
    card.dataset.likedSynced = '1';
  });

  if (!auth.isLoggedIn) return;

  const ids = [...new Set(cards.map((card) => String(card.dataset.noteId)))].slice(0, 50);

  api
    .noteLikedStatus(ids)
    .then((likedIds) => {
      const likedSet = new Set((likedIds || []).map(String));

      cards.forEach((card) => {
        const noteId = String(card.dataset.noteId);
        const liked = likedSet.has(noteId);

        // 与服务端对齐本地记录
        setInteraction(noteId, { liked });

        let stat = card.querySelector('.note-stat');
        if (!stat && liked) {
          const meta = card.querySelector('.note-meta');
          if (meta) {
            stat = document.createElement('span');
            stat.className = 'note-stat';
            stat.innerHTML = `${icons.heart()} 0`;
            meta.appendChild(stat);
          }
        }
        if (stat) stat.classList.toggle('is-liked', liked);
      });
    })
    .catch(() => {
      // 请求失败则冷却一段时间，并解除标记以便稍后重试
      likedSyncCooldownUntil = Date.now() + 30000;
      cards.forEach((card) => delete card.dataset.likedSynced);
    });
}

function scheduleLikedSync() {
  window.clearTimeout(likedSyncTimer);
  likedSyncTimer = window.setTimeout(syncLikedStates, 200);
}

/* ---------------------------------- 启动 --------------------------------- */

function boot() {
  bindHeaderSearch();
  bindUnauthorized();
  bindImageFallback();
  bindSkipLink();
  bindHeaderMenu();
  bindKeyboardShortcuts();
  mountEnvWarning();
  initNoteVideoPreview();

  // 页面上的笔记卡片渲染/翻页后自动校准点赞状态
  new MutationObserver(scheduleLikedSync).observe(document.body, {
    childList: true,
    subtree: true,
  });

  renderAuthSlot();
  onAuthChange(() => renderAuthSlot());

  // 头像下拉菜单：点击展开/收起，点击空白处或 Esc 关闭
  document.getElementById('auth-slot')?.addEventListener('click', handleAuthMenuClick);
  document.addEventListener('click', (event) => {
    if (event.target.closest?.('.user-menu')) return;
    closeUserMenu();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeUserMenu();
  });

  onRouteChange((payload) => {
    syncHeaderSearch(payload);
    renderAuthSlot();
    const menu = document.getElementById('header-menu');
    if (menu) menu.open = false;
    scheduleLikedSync();
  });

  if (auth.isLoggedIn && auth.phone && !auth.me) {
    resolveMe({ timeout: 8000 }).catch((err) => {
      if (err?.status !== 401) console.warn('[app] 启动时获取用户资料失败：', err);
    });
  }

  start();

  // 方便在控制台排查：window.__XHS__.auth
  window.__XHS__ = { auth };
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot, { once: true });
} else {
  boot();
}
