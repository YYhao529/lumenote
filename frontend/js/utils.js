/**
 * 纯工具函数：转义、格式化、URL 处理、剪贴板等。
 * 该模块不依赖项目内其它模块，方便被任意层引用。
 */

const ESCAPE_MAP = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapeHtml(value) {
  return String(value === null || value === undefined ? '' : value).replace(
    /[&<>"']/g,
    (ch) => ESCAPE_MAP[ch]
  );
}

/** 本机 MinIO 的绝对地址前缀，例如 http://127.0.0.1:9000/deepsea-lumenote/xxx.mp4 */
const LOCAL_MINIO_RE = /^https?:\/\/(?:127\.0\.0\.1|localhost):9000\//i;

/** 仅放行 http(s)、blob 图片与 data:image，避免 javascript: 注入 */
export function safeUrl(value) {
  if (!value || typeof value !== 'string') return '';
  const raw = value.trim();
  if (!raw) return '';
  if (raw.startsWith('blob:')) return raw;
  if (/^data:image\//i.test(raw)) return raw;

  // 本机 MinIO 的历史地址统一走同源 /minio 代理：
  // 这样内网穿透后远程访客也能加载图片/视频，同时规避 https 页面加载 http 资源的限制。
  // file:// 直接打开时没有同源服务，保留原始绝对地址。
  const normalized =
    window.location.protocol === 'file:' ? raw : raw.replace(LOCAL_MINIO_RE, '/minio/');

  try {
    const url = new URL(normalized, window.location.href);
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.href;
  } catch (err) {
    return '';
  }
  return '';
}

/** 搜索结果里后端会返回带 <strong> 的高亮片段：先整体转义，再还原 strong */
export function highlight(value) {
  return escapeHtml(value)
    .replace(/&lt;strong&gt;/gi, '<strong>')
    .replace(/&lt;\/strong&gt;/gi, '</strong>');
}

export function truncate(value, max = 80) {
  const text = String(value === null || value === undefined ? '' : value);
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

export function formatCount(value) {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'string' && /[^\d.\-]/.test(value)) return value;
  const num = Number(value);
  if (!Number.isFinite(num)) return String(value);
  if (num < 10000) return String(num);
  const trim = (str) => str.replace(/\.0$/, '');
  if (num < 100000000) return `${trim((num / 10000).toFixed(1))}万`;
  return `${trim((num / 100000000).toFixed(1))}亿`;
}

export function parseDate(value) {
  if (value === null || value === undefined || value === '') return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  if (Array.isArray(value)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0] = value;
    const date = new Date(y, mo - 1, d, h, mi, s);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  if (typeof value === 'number') {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const text = String(value).trim();
  if (!text) return null;
  const date = new Date(text.replace(' ', 'T'));
  return Number.isNaN(date.getTime()) ? null : date;
}

const pad2 = (n) => String(n).padStart(2, '0');

/** 2024-05-01 12:30 */
export function formatDateTime(value) {
  const date = parseDate(value);
  if (!date) return value ? String(value) : '—';
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(
    date.getDate()
  )} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

export function formatDate(value) {
  const date = parseDate(value);
  if (!date) return value ? String(value) : '';
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** 刚刚 / 12 分钟前 / 昨天 20:12 / 3 天前 / 05-01 / 2023-05-01 */
export function relativeTime(value) {
  const date = parseDate(value);
  if (!date) return value ? String(value) : '—';
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = now.getTime() - date.getTime();

  if (diff < 60 * 1000) return '刚刚';
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`;

  if (date >= startOfToday) return `${Math.floor(diff / 3600000)} 小时前`;

  const dateDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const days = Math.round((startOfToday.getTime() - dateDay.getTime()) / 86400000);
  if (days === 1) return `昨天 ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  if (days < 7) return `${days} 天前`;
  if (date.getFullYear() === now.getFullYear()) {
    return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  }
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

export function hashHue(text) {
  const str = String(text || '时光笔记');
  let hash = 0;
  for (let i = 0; i < str.length; i += 1) {
    hash = (hash * 31 + str.charCodeAt(i)) >>> 0;
  }
  // 统一收敛到蓝紫区间（200°–280°），与整体配色协调，避免出现暖色系头像
  return 200 + (hash % 80);
}

export function initials(name) {
  const text = String(name || '时光笔记').trim();
  if (!text) return '小';
  return text.slice(0, 1).toUpperCase();
}

export function isDigits(value) {
  return /^\d+$/.test(String(value || '').trim());
}

/** 支持纯数字 ID、带 /note/123 的链接、?id=123 */
export function parseNoteId(input) {
  const text = String(input || '').trim();
  if (!text) return '';
  if (isDigits(text)) return text;
  const match =
    text.match(/\/note\/(\d{1,20})/) ||
    text.match(/[?&](?:id|noteId)=(\d{1,20})/) ||
    text.match(/^(\d{1,20})$/);
  return match ? match[1] : '';
}

export function isAbortError(err) {
  return Boolean(
    err &&
      (err.name === 'AbortError' ||
        err.code === 'ABORT_ERR' ||
        err.name === 'TimeoutError')
  );
}

export function debounce(fn, wait = 240) {
  let timer = null;
  return (...args) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}

export function readJSON(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw);
    return parsed === null || parsed === undefined ? fallback : parsed;
  } catch (err) {
    return fallback;
  }
}

export function writeJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch (err) {
    return false;
  }
}

export function readSessionJSON(key, fallback) {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch (err) {
    return fallback;
  }
}

export function writeSessionJSON(key, value) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch (err) {
    /* ignore */
  }
}

export async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (err) {
    /* fall through */
  }
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(textarea);
    return ok;
  } catch (err) {
    return false;
  }
}

export function toQueryString(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export function scrollToTop(smooth = false) {
  window.scrollTo({ top: 0, behavior: smooth ? 'smooth' : 'auto' });
}

/** 首图：搜索走 cover，详情走 imgUris[0] */
export function noteCover(note) {
  if (!note) return '';
  const candidates = [note.cover, note.coverUrl, note.imgUris?.[0], note.images?.[0]];
  for (const item of candidates) {
    const url = safeUrl(item);
    if (url) return url;
  }
  return '';
}

export function noteTitle(note) {
  const title = note?.title || note?.highlightTitle;
  return title ? String(title) : '无标题笔记';
}

/**
 * 把笔记正文（纯文本）渲染成富文本 HTML。
 *
 * 安全性：先整体转义，再解析轻量标记，因此用户输入里的 HTML 永远不会被执行。
 * 支持的标记：
 *   **粗体**   *斜体*   __下划线__   ~~删除线~~
 *   行首 "- " / "* " 无序列表，行首 "1. " 有序列表
 * 换行依赖容器的 white-space: pre-wrap。
 */
export function renderRichText(value) {
  const text = escapeHtml(String(value === null || value === undefined ? '' : value));
  if (!text) return '';

  // 行内标记：粗体 → 下划线 → 删除线 → 斜体
  const inline = text
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_\n]+)__/g, '<u>$1</u>')
    .replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>');

  const lines = inline.split('\n');
  let out = '';
  let listType = null;

  const closeList = () => {
    if (listType) {
      out += `</${listType}>`;
      listType = null;
    }
  };

  for (const line of lines) {
    const ul = /^\s{0,3}[-*]\s+(.*)$/.exec(line);
    const ol = /^\s{0,3}\d+[.)]\s+(.*)$/.exec(line);

    if (ul) {
      if (listType !== 'ul') {
        closeList();
        out += '<ul>';
        listType = 'ul';
      }
      out += `<li>${ul[1]}</li>`;
    } else if (ol) {
      if (listType !== 'ol') {
        closeList();
        out += '<ol>';
        listType = 'ol';
      }
      out += `<li>${ol[1]}</li>`;
    } else {
      closeList();
      out += `${line}\n`;
    }
  }
  closeList();

  return out.replace(/\n$/, '');
}

export function topicLabel(topicId, topicName) {
  if (topicName) return topicName;
  if (topicId !== null && topicId !== undefined && topicId !== '') return `话题 #${topicId}`;
  return '';
}

export function noteUrl(noteId) {
  const base = `${window.location.origin}${window.location.pathname}`;
  return `${base}#/note/${encodeURIComponent(String(noteId))}`;
}

export function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function pluralText(count, unit) {
  return `${formatCount(count)} ${unit}`;
}
