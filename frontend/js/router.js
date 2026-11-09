/**
 * 基于 hash 的前端路由。
 *
 * 路由模块按需动态 import，页面通过 default.mount(root, ctx) 渲染。
 * ctx 提供 params / query / signal / navigate / onCleanup。
 */

import { auth } from './store.js';
import { isAbortError, toQueryString } from './utils.js';
import { emptyState, errorState } from './ui.js';

const routes = [
  {
    name: 'explore',
    pattern: '/explore',
    title: '发现',
    load: () => import('./views/explore.js'),
  },
  {
    name: 'search',
    pattern: '/search',
    title: '搜索',
    load: () => import('./views/search.js'),
  },
  {
    name: 'note',
    pattern: '/note/:id',
    title: '笔记详情',
    load: () => import('./views/note-detail.js'),
    auth: true,
  },
  {
    name: 'note-edit',
    pattern: '/note/:id/edit',
    title: '编辑笔记',
    load: () => import('./views/note-editor.js'),
    auth: true,
  },
  {
    name: 'publish',
    pattern: '/publish',
    title: '发布笔记',
    load: () => import('./views/note-editor.js'),
    auth: true,
  },
  {
    name: 'user',
    pattern: '/user/:id',
    title: '用户主页',
    load: () => import('./views/user.js'),
    auth: true,
  },
  {
    name: 'me',
    pattern: '/me',
    title: '我的主页',
    load: () => import('./views/profile.js'),
    auth: true,
  },
  {
    name: 'assistant',
    pattern: '/assistant',
    title: '智能助手',
    load: () => import('./views/assistant.js'),
    auth: true,
  },
  {
    name: 'login',
    pattern: '/login',
    title: '登录',
    load: () => import('./views/login.js'),
  },
  {
    name: 'api',
    pattern: '/api',
    title: '接口联调',
    load: () => import('./views/api-docs.js'),
  },
];

const NAV_ALIAS = {
  'note-edit': 'publish',
};

const routeListeners = new Set();

let activeController = null;
let cleanupFns = [];

function parseHash() {
  const raw = window.location.hash.replace(/^#/, '') || '/explore';
  const [pathPart, queryPart = ''] = raw.split('?');
  const path = `/${pathPart.replace(/^\/+/, '')}`.replace(/\/+$/, '') || '/explore';
  return {
    path,
    query: Object.fromEntries(new URLSearchParams(queryPart)),
  };
}

function matchRoute(path) {
  const segments = path.split('/').filter(Boolean);
  for (const route of routes) {
    const patternSegments = route.pattern.split('/').filter(Boolean);
    if (patternSegments.length !== segments.length) continue;

    const params = {};
    let matched = true;

    for (let i = 0; i < patternSegments.length; i += 1) {
      const part = patternSegments[i];
      if (part.startsWith(':')) {
        try {
          params[part.slice(1)] = decodeURIComponent(segments[i]);
        } catch (err) {
          params[part.slice(1)] = segments[i];
        }
      } else if (part !== segments[i]) {
        matched = false;
        break;
      }
    }

    if (matched) return { route, params };
  }
  return null;
}

export function navigate(path, query = {}, { replace = false } = {}) {
  const target = `#${path}${toQueryString(query)}`;
  if (replace) {
    window.history.replaceState(null, '', target);
    render();
    return;
  }
  if (window.location.hash === target) return;
  window.location.hash = target;
}

export function back(fallback = '/explore') {
  if (window.history.length > 1) window.history.back();
  else navigate(fallback);
}

export function getCurrentRoute() {
  return parseHash();
}

export function onRouteChange(handler) {
  routeListeners.add(handler);
  return () => routeListeners.delete(handler);
}

function notifyRoute(payload) {
  routeListeners.forEach((handler) => {
    try {
      handler(payload);
    } catch (err) {
      console.error('[router] route listener error', err);
    }
  });
}

function setActiveNav(name) {
  const activeName = NAV_ALIAS[name] || name;
  document.querySelectorAll('[data-nav]').forEach((el) => {
    el.classList.toggle('is-active', el.dataset.nav === activeName);
  });
}

function runCleanups() {
  cleanupFns.forEach((fn) => {
    try {
      fn();
    } catch (err) {
      console.error('[router] cleanup error', err);
    }
  });
  cleanupFns = [];
}

async function render() {
  const { path, query } = parseHash();
  const matched = matchRoute(path);
  const root = document.getElementById('view');

  if (!root) return;

  // 路由切换时关闭仍打开的弹窗，避免弹窗停留在新页面上
  document.querySelectorAll('#dialog-root dialog[open]').forEach((dialog) => {
    try {
      dialog.close();
    } catch (err) {
      /* ignore */
    }
  });

  if (!matched) {
    if (activeController) activeController.abort();
    runCleanups();
    setActiveNav('');
    document.title = '页面不存在 · 拾光笔记';
    root.innerHTML = `<div class="page">${emptyState({
      icon: 'compass',
      title: '页面走丢了',
      desc: `没有找到 ${path} 对应的页面，可以回到发现页继续浏览。`,
      actions: [{ label: '回到发现', href: '#/explore', variant: 'btn-primary' }],
    })}</div>`;
    return;
  }

  const { route, params } = matched;

  if (route.auth && !auth.isLoggedIn) {
    const redirect = encodeURIComponent(`${path}${toQueryString(query)}`);
    window.location.replace(`#/login?redirect=${redirect}`);
    return;
  }

  if (activeController) activeController.abort();
  activeController = new AbortController();
  runCleanups();

  document.body.dataset.page = route.name;
  document.title = `${route.title} · 拾光笔记`;
  setActiveNav(route.name);
  root.innerHTML = '';
  window.scrollTo({ top: 0 });
  notifyRoute({ name: route.name, path, query, params, title: route.title });

  const ctx = {
    params,
    query,
    path,
    signal: activeController.signal,
    navigate,
    onCleanup(fn) {
      if (typeof fn === 'function') cleanupFns.push(fn);
    },
    setTitle(title) {
      if (title) document.title = `${title} · 拾光笔记`;
    },
  };

  try {
    const module = await route.load();
    if (activeController.signal.aborted) return;
    if (!module?.default?.mount) throw new Error(`页面模块加载失败：${route.name}`);
    await module.default.mount(root, ctx);
    if (!activeController.signal.aborted) {
      root.focus({ preventScroll: true });
    }
  } catch (err) {
    if (isAbortError(err) || activeController.signal.aborted) return;
    console.error('[router] 页面渲染失败', err);
    root.innerHTML = `<div class="page">${errorState({
      error: err,
      title: '页面加载失败',
      desc: err?.message || '页面脚本执行出错，请查看浏览器控制台。',
      retryId: 'route-retry',
    })}</div>`;
    document.getElementById('route-retry')?.addEventListener('click', () => render());
  }
}

export function start() {
  if (!window.location.hash) {
    window.history.replaceState(null, '', '#/explore');
  }
  window.addEventListener('hashchange', render);
  render();
}
