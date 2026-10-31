/**
 * UI 基础组件：Toast、Dialog、头像、笔记卡片、空状态、骨架屏、分页等。
 */

import { icons } from './icons.js';
import { getInteraction } from './local.js';
import {
  escapeHtml,
  formatCount,
  hashHue,
  highlight,
  initials,
  noteCover,
  safeUrl,
} from './utils.js';

/* --------------------------------- Toast --------------------------------- */

const TOAST_ICONS = {
  success: icons.check,
  error: icons.alert,
  warning: icons.alert,
  info: icons.info,
};

export function toast(message, type = 'info', duration = 2800) {
  const root = document.getElementById('toast-root');
  if (!root) return;

  while (root.children.length >= 4) root.firstElementChild?.remove();

  const el = document.createElement('div');
  el.className = `toast toast-${TOAST_ICONS[type] ? type : 'info'}`;
  const icon = (TOAST_ICONS[type] || TOAST_ICONS.info)();
  el.innerHTML = `${icon}<span>${escapeHtml(message)}</span>`;

  const remove = () => {
    if (!el.isConnected || el.classList.contains('is-leaving')) return;
    el.classList.add('is-leaving');
    setTimeout(() => el.remove(), 220);
  };

  el.addEventListener('click', remove);
  root.appendChild(el);
  setTimeout(remove, Math.max(1200, duration));
}

/* --------------------------------- Dialog -------------------------------- */

let openDialogCount = 0;

function syncBodyLock() {
  document.body.classList.toggle('has-dialog', openDialogCount > 0);
}

/**
 * 打开原生 <dialog> 弹窗。
 * @returns {{dialog: HTMLDialogElement, bodyEl: HTMLElement, close: Function}}
 */
export function openDialog({
  title = '',
  desc = '',
  body = '',
  footer = '',
  size = '',
  dismissible = true,
  onMount,
  onClose,
} = {}) {
  const root = document.getElementById('dialog-root') || document.body;
  const dialog = document.createElement('dialog');
  dialog.className = `modal${size ? ` modal-${size}` : ''}`;
  dialog.innerHTML = `
    <div class="modal-card">
      <div class="modal-head">
        <div class="grow">
          ${title ? `<h2 class="modal-title">${escapeHtml(title)}</h2>` : ''}
          ${desc ? `<p class="modal-desc">${escapeHtml(desc)}</p>` : ''}
        </div>
        <button class="modal-close" type="button" aria-label="关闭弹窗">${icons.close()}</button>
      </div>
      <div class="modal-body">${body}</div>
      ${footer ? `<div class="modal-foot">${footer}</div>` : ''}
    </div>`;

  root.appendChild(dialog);
  openDialogCount += 1;
  syncBodyLock();

  const bodyEl = dialog.querySelector('.modal-body');
  const close = (value) => {
    if (dialog.open) dialog.close(value);
    else dialog.remove();
  };

  dialog.querySelector('.modal-close')?.addEventListener('click', () => close());

  if (dismissible) {
    dialog.addEventListener('click', (event) => {
      if (event.target === dialog) close();
    });
  } else {
    dialog.addEventListener('cancel', (event) => event.preventDefault());
  }

  dialog.addEventListener('close', () => {
    openDialogCount = Math.max(0, openDialogCount - 1);
    syncBodyLock();
    if (dialog.isConnected) dialog.remove();
    onClose?.();
  });

  dialog.showModal();
  onMount?.({ dialog, bodyEl, close });

  return { dialog, bodyEl, close };
}

/**
 * 二次确认弹窗。
 * @returns {Promise<boolean>}
 */
export function confirmDialog({
  title = '确认操作',
  message = '',
  confirmText = '确定',
  cancelText = '取消',
  danger = false,
  extraHtml = '',
} = {}) {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      resolve(value);
    };

    openDialog({
      title,
      size: 'sm',
      body: `${message ? `<p class="modal-desc">${escapeHtml(message)}</p>` : ''}${extraHtml}`,
      footer: `
        <button class="btn btn-ghost" type="button" data-role="cancel">${escapeHtml(cancelText)}</button>
        <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" type="button" data-role="confirm">${escapeHtml(confirmText)}</button>`,
      onMount({ dialog, close }) {
        dialog.querySelector('[data-role="cancel"]')?.addEventListener('click', () => {
          finish(false);
          close();
        });
        dialog.querySelector('[data-role="confirm"]')?.addEventListener('click', () => {
          finish(true);
          close();
        });
        dialog.addEventListener('close', () => finish(false));
      },
    });
  });
}

/* ------------------------------ 图片灯箱 -------------------------------- */

export function showLightbox(images = [], startIndex = 0) {
  const list = images.map((src) => safeUrl(src)).filter(Boolean);
  if (!list.length) return;

  let index = Math.min(Math.max(0, startIndex), list.length - 1);

  const dialog = document.createElement('dialog');
  dialog.className = 'lightbox';
  dialog.innerHTML = `
    <img class="lightbox-img" src="${escapeHtml(list[index])}" alt="笔记图片">
    <div class="lightbox-bar">
      <button type="button" data-role="prev" aria-label="上一张" ${list.length < 2 ? 'hidden' : ''}>${icons.chevronLeft()}</button>
      <span data-role="counter">${index + 1} / ${list.length}</span>
      <button type="button" data-role="next" aria-label="下一张" ${list.length < 2 ? 'hidden' : ''}>${icons.chevronRight()}</button>
      <button type="button" data-role="close" aria-label="关闭">${icons.close()}</button>
    </div>`;

  document.getElementById('dialog-root')?.appendChild(dialog);
  openDialogCount += 1;
  syncBodyLock();

  const img = dialog.querySelector('.lightbox-img');
  const counter = dialog.querySelector('[data-role="counter"]');

  const render = () => {
    img.src = list[index];
    counter.textContent = `${index + 1} / ${list.length}`;
  };

  const step = (delta) => {
    if (list.length < 2) return;
    index = (index + delta + list.length) % list.length;
    render();
  };

  dialog.querySelector('[data-role="prev"]')?.addEventListener('click', () => step(-1));
  dialog.querySelector('[data-role="next"]')?.addEventListener('click', () => step(1));
  dialog.querySelector('[data-role="close"]')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });
  dialog.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowLeft') step(-1);
    if (event.key === 'ArrowRight') step(1);
  });
  dialog.addEventListener('close', () => {
    openDialogCount = Math.max(0, openDialogCount - 1);
    syncBodyLock();
    dialog.remove();
  });

  dialog.showModal();
  dialog.focus();
}

/* --------------------------------- 头像 ---------------------------------- */

export function avatar(user, size = 'md', { ring = false } = {}) {
  const isObject = user && typeof user === 'object';
  const name = isObject
    ? user.nickname || user.creatorName || user.name || '时光笔记'
    : String(user || '时光笔记');
  const rawAvatar = isObject ? safeUrl(user.avatar) : '';
  const hue = hashHue(name);
  return `<span class="avatar avatar-${size}${ring ? ' avatar-ring' : ''}" style="--avatar-hue:${hue}" title="${escapeHtml(name)}">
    <span class="avatar-initial">${escapeHtml(initials(name))}</span>
    ${rawAvatar ? `<img src="${escapeHtml(rawAvatar)}" alt="" loading="lazy" data-fallback="1">` : ''}
  </span>`;
}

/* -------------------------------- 笔记卡片 -------------------------------- */

const RATIO_CLASSES = ['ratio-a', 'ratio-b', 'ratio-c'];

export function noteCard(note, { showPrivate = true } = {}) {
  const id = note?.noteId ?? note?.id ?? '';
  const idText = String(id);
  const titleHtml = note?.highlightTitle
    ? highlight(note.highlightTitle)
    : escapeHtml(note?.title || '无标题笔记');
  const cover = noteCover(note);
  const type = Number(note?.type) === 1 ? 1 : 0;
  const videoUri = type === 1 ? safeUrl(note?.videoUri) : '';
  // 视频笔记没有图片封面时，直接用视频首帧当封面
  const videoAsCover = Boolean(videoUri) && !cover;
  const nickname = note?.nickname || note?.creatorName || '';
  const ratio = RATIO_CLASSES[hashHue(idText) % RATIO_CLASSES.length];
  const likeTotal =
    note?.likeTotal !== undefined && note?.likeTotal !== null && note?.likeTotal !== ''
      ? formatCount(note.likeTotal)
      : '';
  // 当前用户是否点过赞（本地记录），点过则卡片上显示红心
  const liked = getInteraction(idText).liked;

  return `<article class="note-card ${ratio}" data-note-id="${escapeHtml(idText)}">
    <a class="note-cover${videoAsCover ? ' has-video-cover' : ''}" href="#/note/${encodeURIComponent(idText)}" aria-label="${escapeHtml(note?.title || '查看笔记')}">
      <span class="cover-fallback">${icons.image()}<span>${type === 1 ? '视频笔记' : '图文笔记'}</span></span>
      ${
        cover
          ? `<img src="${escapeHtml(cover)}" alt="" loading="lazy" decoding="async" data-fallback="1">`
          : ''
      }
      ${
        videoUri
          ? `<video class="note-video${videoAsCover ? ' is-cover' : ''}" muted playsinline loop preload="${
              videoAsCover ? 'metadata' : 'none'
            }" ${
              videoAsCover
                ? `src="${escapeHtml(videoUri)}#t=0.1"`
                : `data-src="${escapeHtml(videoUri)}"`
            }></video>`
          : ''
      }
      ${
        type === 1
          ? `<span class="play-badge"${
              videoUri
                ? ' data-video-toggle="1" role="button" tabindex="0" aria-label="预览视频"'
                : ' aria-hidden="true"'
            }>${icons.play()}</span>`
          : ''
      }
      ${
        showPrivate && Number(note?.visible) === 1
          ? `<span class="cover-tag is-private">${icons.lock()} 仅自己可见</span>`
          : ''
      }
    </a>
    <div class="note-body">
      <a class="note-title" href="#/note/${encodeURIComponent(idText)}">${titleHtml}</a>
      <div class="note-meta">
        <span class="note-author">
          ${nickname ? avatar({ nickname, avatar: note.avatar }, 'xs') : ''}
          <span class="name">${escapeHtml(nickname || '时光笔记用户')}</span>
        </span>
        ${
          likeTotal !== '' || liked
            ? `<span class="note-stat${liked ? ' is-liked' : ''}"${
                liked ? ' title="已点赞"' : ''
              }>${icons.heart()} ${escapeHtml(likeTotal)}</span>`
            : ''
        }
      </div>
    </div>
  </article>`;
}

/* ------------------------------ 笔记视频预览 ------------------------------ */

let activePreview = null;

/** 全局声音偏好：true=所有笔记的悬停预览都有声，false=默认静音（用户任一调整即对全部笔记生效） */
let soundEnabled = false;
/** 记录最近一次「由代码设置」的 muted 值，用于区分用户的音量操作 */
const expectedMuted = new WeakMap();

function applyMuted(video, muted) {
  expectedMuted.set(video, muted);
  video.muted = muted;
}

function bindSoundPreference(video) {
  if (video.dataset.soundBound === '1') return;
  video.dataset.soundBound = '1';

  // 用户通过原生控制条调整音量/静音时，更新全局偏好
  video.addEventListener('volumechange', () => {
    const expected = expectedMuted.get(video);
    if (expected !== undefined && expected === video.muted) {
      expectedMuted.delete(video);
      return; // 代码设置触发的变更，不算用户偏好
    }
    soundEnabled = !video.muted && video.volume > 0;
  });
}

function startVideoPreview(video, { muted = true } = {}) {
  if (!video) return;
  if (video.dataset.src && !video.getAttribute('src')) {
    video.src = video.dataset.src;
  }
  bindSoundPreference(video);

  // 全局声音偏好：在任意笔记上开启过声音，所有笔记的预览都保持有声
  applyMuted(video, muted && !soundEnabled);
  if (!video.muted) soundEnabled = true;

  // 悬停预览时展示原生控制条（进度、音量、全屏等）
  video.controls = true;
  video.closest('.note-cover')?.classList.add('is-previewing');

  const playing = video.play();
  if (playing && typeof playing.catch === 'function') {
    playing.catch(() => {
      // 浏览器拦截有声自动播放时，退化为静音播放
      applyMuted(video, true);
      const mutedPlaying = video.play();
      if (mutedPlaying && typeof mutedPlaying.catch === 'function') mutedPlaying.catch(() => {});
    });
  }
}

function stopVideoPreview(video) {
  if (!video) return;
  video.controls = false;
  video.pause();
  try {
    video.currentTime = 0;
  } catch (err) {
    /* 元数据未加载时忽略 */
  }
  video.closest('.note-cover')?.classList.remove('is-previewing');
}

/**
 * 列表卡片内的视频预览：
 * - 鼠标悬停自动静音播放，移出即暂停复位；
 * - 点击播放按钮在「播放（带声音）/ 暂停」之间切换，不会跳转到详情页。
 */
export function initNoteVideoPreview() {
  if (window.__xhsVideoPreviewBound) return;
  window.__xhsVideoPreviewBound = true;

  document.addEventListener('pointerover', (event) => {
    const cover = event.target.closest?.('.note-cover');
    const video = cover?.querySelector('.note-video');
    if (!video || cover.contains(event.relatedTarget)) return;

    if (activePreview && activePreview !== video) stopVideoPreview(activePreview);
    activePreview = video;
    startVideoPreview(video, { muted: true });
  });

  document.addEventListener('pointerout', (event) => {
    const cover = event.target.closest?.('.note-cover');
    const video = cover?.querySelector('.note-video');
    if (!video || cover.contains(event.relatedTarget)) return;

    stopVideoPreview(video);
    if (activePreview === video) activePreview = null;
  });

  document.addEventListener('click', (event) => {
    const videoEl = event.target.closest?.('.note-video');
    if (videoEl) {
      // 只有点击底部控制条区域时才交给播放器（进度、音量、全屏），
      // 点击视频其它位置保持原有交互：进入笔记详情页。
      const rect = videoEl.getBoundingClientRect();
      const controlBand = Math.min(56, Math.max(36, rect.height * 0.22));
      const onControls = event.clientY >= rect.bottom - controlBand;

      if (onControls) {
        event.preventDefault();
        event.stopPropagation();
      }
      return;
    }

    const toggle = event.target.closest?.('[data-video-toggle]');
    if (!toggle) return;

    // 阻止卡片链接跳转，直接在列表内预览
    event.preventDefault();
    event.stopPropagation();

    const cover = toggle.closest('.note-cover');
    const video = cover?.querySelector('.note-video');
    if (!video) return;

    if (video.paused) {
      activePreview = video;
      startVideoPreview(video, { muted: false });
    } else {
      video.pause();
      applyMuted(video, true);
    }
  });

  // 视频加载失败（地址失效/格式不支持）时回退到占位封面
  document.addEventListener(
    'error',
    (event) => {
      const video = event.target;
      if (!(video instanceof HTMLVideoElement) || !video.classList.contains('note-video')) return;

      const cover = video.closest('.note-cover');
      if (!cover) return;

      cover.classList.add('has-video-error');
      cover.classList.remove('has-video-cover', 'is-previewing');
    },
    true
  );

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const toggle = event.target.closest?.('[data-video-toggle]');
    if (!toggle) return;
    event.preventDefault();
    toggle.click();
  });
}

/* -------------------------------- 用户行 --------------------------------- */

export function userRow(user, { actionHtml = '', showBio = true } = {}) {
  const id = String(user?.userId ?? user?.id ?? '');
  const name = user?.nickname || '时光笔记用户';
  const meta = [];
  if (user?.lumenoteId) meta.push(`时光笔记号 ${user.lumenoteId}`);
  if (user?.noteTotal !== undefined && user?.noteTotal !== null) {
    meta.push(`${formatCount(user.noteTotal)} 笔记`);
  }
  if (user?.fansTotal !== undefined && user?.fansTotal !== null) {
    meta.push(`${formatCount(user.fansTotal)} 粉丝`);
  }

  return `<div class="user-row" data-user-id="${escapeHtml(id)}">
    <a href="#/user/${encodeURIComponent(id)}" aria-label="查看 ${escapeHtml(name)} 的主页">${avatar(user, 'md')}</a>
    <div class="user-row-main">
      <a class="user-row-name" href="#/user/${encodeURIComponent(id)}">${escapeHtml(name)}</a>
      ${showBio && user?.introduction ? `<span class="user-row-bio">${escapeHtml(user.introduction)}</span>` : ''}
      ${meta.length ? `<span class="user-row-meta">${escapeHtml(meta.join(' · '))}</span>` : ''}
    </div>
    ${actionHtml}
  </div>`;
}

/* ------------------------------- 空 / 错误 ------------------------------- */

export function emptyState({
  icon = 'sparkles',
  title = '这里空空如也',
  desc = '',
  actions = [],
} = {}) {
  const iconFn = icons[icon] || icons.sparkles;
  const actionsHtml = actions
    .map((action) => {
      const cls = `btn ${action.variant || 'btn-outline'}`;
      if (action.href) {
        return `<a class="${cls}" href="${escapeHtml(action.href)}"${
          action.id ? ` id="${escapeHtml(action.id)}"` : ''
        }>${escapeHtml(action.label)}</a>`;
      }
      return `<button class="${cls}" type="button"${
        action.id ? ` id="${escapeHtml(action.id)}"` : ''
      }${action.action ? ` data-action="${escapeHtml(action.action)}"` : ''}>${escapeHtml(
        action.label
      )}</button>`;
    })
    .join('');

  return `<div class="empty">
    <span class="empty-icon">${iconFn()}</span>
    <h3 class="empty-title">${escapeHtml(title)}</h3>
    ${desc ? `<p class="empty-desc">${escapeHtml(desc)}</p>` : ''}
    ${actionsHtml ? `<div class="empty-actions">${actionsHtml}</div>` : ''}
  </div>`;
}

export function errorState({
  error,
  title = '加载失败',
  desc = '',
  retryId = 'retry-btn',
  extraActions = [],
  showCode = true,
} = {}) {
  const message = desc || error?.message || '请求失败，请稍后重试';
  const isNetwork = error?.code === 'NETWORK_ERROR' || error?.code === 'TIMEOUT';
  const hint = isNetwork
    ? '请稍后重试；如果持续失败，请联系管理员。'
    : '';
  const codeText =
    showCode && (error?.code || error?.status)
      ? `<span class="error-code">${escapeHtml(
          [error?.code, error?.status].filter(Boolean).join(' · ')
        )}</span>`
      : '';

  const actions = [
    `<button class="btn btn-primary" type="button" id="${escapeHtml(retryId)}">${icons.refresh()} 重新加载</button>`,
    ...extraActions,
  ];

  return `<div class="error-state">
    <span class="error-icon">${icons.alert()}</span>
    <h3 class="error-title">${escapeHtml(title)}</h3>
    <p class="error-desc">${escapeHtml(message)}${
      hint ? `<br><span class="text-muted">${escapeHtml(hint)}</span>` : ''
    }</p>
    ${codeText}
    <div class="error-actions">${actions.join('')}</div>
  </div>`;
}

/* --------------------------------- 骨架屏 -------------------------------- */

export function skeletonGrid(count = 8) {
  const items = Array.from({ length: count })
    .map(
      () => `<div class="skeleton-card">
        <div class="skeleton skeleton-cover"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton skeleton-line is-short"></div>
      </div>`
    )
    .join('');
  return `<div class="skeleton-grid" aria-hidden="true">${items}</div>`;
}

export function skeletonRows(count = 4) {
  const items = Array.from({ length: count })
    .map(
      () => `<div class="skeleton-row">
        <div class="skeleton skeleton-avatar"></div>
        <div class="skeleton-stack">
          <div class="skeleton skeleton-line" style="width:40%"></div>
          <div class="skeleton skeleton-line" style="width:65%"></div>
        </div>
      </div>`
    )
    .join('');
  return `<div class="user-list" aria-hidden="true">${items}</div>`;
}

/* --------------------------------- 分页 ---------------------------------- */

export function pagination({ pageNo = 1, totalPage = 1, totalCount = 0, id = 'pager' } = {}) {
  const page = Math.max(1, Number(pageNo) || 1);
  const total = Math.max(1, Number(totalPage) || 1);
  if (total <= 1 && Number(totalCount) <= 10) return '';

  return `<nav class="pagination" id="${escapeHtml(id)}" aria-label="分页导航">
    <button class="page-btn" type="button" data-page="${page - 1}" ${
      page <= 1 ? 'disabled' : ''
    }>${icons.chevronLeft()} 上一页</button>
    <span class="page-info">第 <strong>${page}</strong> / ${total} 页 · 共 ${formatCount(
      totalCount
    )} 条</span>
    <button class="page-btn" type="button" data-page="${page + 1}" ${
      page >= total ? 'disabled' : ''
    }>下一页 ${icons.chevronRight()}</button>
  </nav>`;
}

export function bindPagination(container, handler) {
  if (!container) return;
  container.querySelectorAll('.page-btn[data-page]').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (btn.disabled) return;
      const page = Number(btn.dataset.page);
      if (Number.isFinite(page) && page > 0) handler(page);
    });
  });
}

/* --------------------------------- 小工具 -------------------------------- */

export function setLoading(button, loading) {
  if (!button) return;
  button.classList.toggle('is-loading', Boolean(loading));
  if (loading) button.setAttribute('aria-busy', 'true');
  else button.removeAttribute('aria-busy');
  if ('disabled' in button) button.disabled = Boolean(loading);
}

export function spinner() {
  return '<span class="spinner" aria-hidden="true"></span>';
}

export function formatError(error) {
  if (!error) return '操作失败，请稍后重试';
  return error.message || String(error);
}

export function followButton(userId, followed, { small = true } = {}) {
  return `<button class="btn ${small ? 'btn-sm' : ''} ${
    followed ? 'btn-outline' : 'btn-primary'
  }" type="button" data-follow-user="${escapeHtml(String(userId))}" data-followed="${
    followed ? '1' : '0'
  }">${followed ? '已关注' : '关注'}</button>`;
}

export function badge(text, tone = '', icon = '') {
  const iconFn = icon ? icons[icon] : null;
  return `<span class="badge ${tone ? `badge-${tone}` : ''}">${
    iconFn ? iconFn() : ''
  }${escapeHtml(text)}</span>`;
}
