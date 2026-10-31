/** 我的主页：资料、关注/粉丝、最近浏览、资料与密码设置 */

import { api } from '../api.js';
import { SEX_OPTIONS } from '../constants.js';
import { icons } from '../icons.js';
import {
  clearRecentNotes,
  getRecentNotes,
} from '../local.js';
import { navigate } from '../router.js';
import { logout, resolveMe } from '../session.js';
import { auth } from '../store.js';
import {
  avatar,
  confirmDialog,
  emptyState,
  noteCard,
  openDialog,
  setLoading,
  toast,
} from '../ui.js';
import {
  escapeHtml,
  formatCount,
  isAbortError,
  safeUrl,
} from '../utils.js';
import { createNotesPanel } from './notes-panel.js';
import { createRelationsPanel } from './relations-panel.js';

const NICKNAME_RE = /[!@#$%^&*(),.?":{}|<>]/;
const LUMENOTE_ID_RE = /^[a-zA-Z0-9_]{6,15}$/;
const PHONE_RE = /^\d{11}$/;

export default {
  async mount(root, ctx) {
    if (!auth.isLoggedIn) {
      navigate('/login', { redirect: '/me' });
      return;
    }

    let resolveError = null;
    // 总是向后端刷新一次资料：本地缓存（localStorage）里的头像/背景图
    // 可能是旧值，只在 me 缺失时才拉取会导致更新后一直显示旧图。
    try {
      await resolveMe({ signal: ctx.signal });
    } catch (err) {
      if (isAbortError(err) || ctx.signal.aborted) return;
      resolveError = err;
    }

    if (!auth.me?.id) {
      // 手机号是已知的，说明登录成功但资料接口没返回，
      // 这时不该再让用户“绑定手机号”，否则会诱导用户重复填写。
      if (auth.phone) renderLoadFailCard(root, ctx, resolveError);
      else renderBindCard(root, ctx);
      return;
    }

    const me = auth.me;
    const coverUrl = safeUrl(me.backgroundImg || '');
    const NOTE_TABS = ['publish', 'collect', 'like'];
    const initialTab = [...NOTE_TABS, 'following', 'fans', 'recent'].includes(ctx.query.tab)
      ? ctx.query.tab
      : 'publish';

    root.innerHTML = `<div class="profile-page">
      <section class="profile-hero">
        <div class="profile-banner">
        <div class="profile-cover${coverUrl ? ' has-image' : ''}"${
          coverUrl ? ` style="background-image:url('${escapeHtml(coverUrl)}')"` : ''
        }></div>
        <div class="profile-head">
          <div class="profile-avatar">${avatar(me, 'xl', { ring: true })}</div>
          <div class="profile-info">
            <div class="profile-name">${escapeHtml(me.nickname || '时光笔记用户')}
              <span class="badge badge-brand">我</span>
            </div>
            <div class="profile-id">
              <span>用户 ID <code>${escapeHtml(String(me.id))}</code></span>
              <span class="meta-dot"></span>
              <span>手机号 ${escapeHtml(maskPhone(auth.phone))}</span>
            </div>
            <p class="profile-bio">${escapeHtml(me.introduction || '这个人很懒，还没有填写个人简介。')}</p>
          </div>
          <div class="profile-actions">
            <button class="btn btn-primary btn-sm" type="button" id="edit-profile">${icons.edit()} 编辑资料</button>
            <button class="btn btn-outline btn-sm" type="button" id="change-password">${icons.key()} 修改密码</button>
            <button class="btn btn-ghost btn-sm" type="button" id="logout">${icons.logout()} 退出登录</button>
          </div>
        </div>

        <div class="profile-stats">
          <div class="stat is-link" role="button" tabindex="0" data-goto-tab="following">
            <span class="stat-value" id="stat-following">—</span><span class="stat-label">关注</span>
          </div>
          <div class="stat is-link" role="button" tabindex="0" data-goto-tab="fans">
            <span class="stat-value" id="stat-fans">—</span><span class="stat-label">粉丝</span>
          </div>
          <div class="stat">
            <span class="stat-value" id="stat-like">—</span><span class="stat-label">获赞</span>
          </div>
          <div class="stat">
            <span class="stat-value" id="stat-collect">—</span><span class="stat-label">被收藏</span>
          </div>
        </div>
        </div>

        <div class="profile-tabs-wrap">
          <div class="tabs-line" role="tablist" aria-label="个人主页内容">
            <button class="tab${initialTab === 'publish' ? ' is-active' : ''}" type="button" data-tab="publish" role="tab">
              笔记 <span class="tab-count" id="count-publish">—</span>
            </button>
            <button class="tab${initialTab === 'collect' ? ' is-active' : ''}" type="button" data-tab="collect" role="tab">
              收藏 <span class="tab-count" id="count-collect">—</span>
            </button>
            <button class="tab${initialTab === 'like' ? ' is-active' : ''}" type="button" data-tab="like" role="tab">
              赞过 <span class="tab-count" id="count-like">—</span>
            </button>
            <button class="tab${initialTab === 'following' ? ' is-active' : ''}" type="button" data-tab="following" role="tab">
              关注 <span class="tab-count" id="count-following">—</span>
            </button>
            <button class="tab${initialTab === 'fans' ? ' is-active' : ''}" type="button" data-tab="fans" role="tab">
              粉丝 <span class="tab-count" id="count-fans">—</span>
            </button>
            <button class="tab${initialTab === 'recent' ? ' is-active' : ''}" type="button" data-tab="recent" role="tab">
              最近浏览
            </button>
          </div>
        </div>

        <div class="profile-body" id="profile-body"></div>
      </section>
    </div>`;

    const bodyEl = root.querySelector('#profile-body');
    const tabs = [...root.querySelectorAll('[data-tab]')];
    let currentTab = initialTab;

    /* ------------------------------ 笔记列表 ------------------------------ */

    const notes = createNotesPanel({
      container: bodyEl,
      userId: me.id,
      signal: ctx.signal,
      onTotal(tab, total) {
        const badge = root.querySelector(`#count-${tab}`);
        if (badge) badge.textContent = formatCount(total);
      },
    });

    /* ------------------------------ 关注 / 粉丝 ----------------------------- */

    const relations = createRelationsPanel({
      container: bodyEl,
      userId: me.id,
      signal: ctx.signal,
      onTotal(tab, total) {
        const badge = root.querySelector(
          tab === 'following' ? '#count-following' : '#count-fans'
        );
        if (badge) badge.textContent = formatCount(total);
      },
    });

    /* -------------------------------- 统计数字 ------------------------------ */

    const loadStats = async () => {
      const [followingRes, fansRes] = await Promise.allSettled([
        api.followingList(me.id, 1, { signal: ctx.signal }),
        api.fansList(me.id, 1, { signal: ctx.signal }),
      ]);

      if (ctx.signal.aborted) return;

      if (followingRes.status === 'fulfilled') {
        const total = Number(followingRes.value?.totalCount) || 0;
        const el = root.querySelector('#stat-following');
        if (el) el.textContent = formatCount(total);
        const badge = root.querySelector('#count-following');
        if (badge) badge.textContent = formatCount(total);
      }

      if (fansRes.status === 'fulfilled') {
        const total = Number(fansRes.value?.totalCount) || 0;
        const el = root.querySelector('#stat-fans');
        if (el) el.textContent = formatCount(total);
        const badge = root.querySelector('#count-fans');
        if (badge) badge.textContent = formatCount(total);
      }

      // 获赞 / 被收藏：来自计数服务（同时会触发计数 Hash 的懒初始化）
      try {
        const count = await api.userCount(me.id, { signal: ctx.signal });
        if (ctx.signal.aborted || !count) return;

        const likeEl = root.querySelector('#stat-like');
        if (likeEl) likeEl.textContent = formatCount(Number(count.likeTotal) || 0);

        const collectEl = root.querySelector('#stat-collect');
        if (collectEl) collectEl.textContent = formatCount(Number(count.collectTotal) || 0);
      } catch (err) {
        /* 计数服务不可用时保持 "—" */
      }
    };

    /* -------------------------------- 最近浏览 ------------------------------ */

    const renderRecent = () => {
      const recent = getRecentNotes();
      if (!recent.length) {
        bodyEl.innerHTML = emptyState({
          icon: 'clock',
          title: '还没有浏览记录',
          desc: '最近浏览只保存在当前浏览器，去发现页搜索或打开一篇笔记吧。',
          actions: [{ label: '去发现', href: '#/explore', variant: 'btn-primary' }],
        });
        return;
      }

      bodyEl.innerHTML = `<div class="row-between" style="margin-bottom:14px">
        <span class="section-desc">仅保存在当前浏览器，最多保留 12 篇</span>
        <button class="link-btn" type="button" id="clear-recent">清空记录</button>
      </div>
      <div class="note-grid">${recent.map((note) => noteCard(note)).join('')}</div>`;

      bodyEl.querySelector('#clear-recent')?.addEventListener('click', async () => {
        const ok = await confirmDialog({
          title: '清空最近浏览',
          message: '仅清空本地记录，不影响服务器数据。',
          confirmText: '清空',
          danger: true,
        });
        if (!ok) return;
        clearRecentNotes();
        renderRecent();
        toast('已清空最近浏览', 'success');
      });
    };

    /* --------------------------------- Tab 切换 ----------------------------- */

    const activateTab = (next) => {
      currentTab = next;
      tabs.forEach((tab) => {
        tab.classList.toggle('is-active', tab.dataset.tab === next);
      });

      const url = new URL(window.location.href);
      url.hash = `#/me?tab=${next}`;
      window.history.replaceState(null, '', url);

      if (next === 'recent') renderRecent();
      else if (NOTE_TABS.includes(next)) notes.setTab(next);
      else relations.setTab(next);
    };

    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        if (tab.dataset.tab !== currentTab) activateTab(tab.dataset.tab);
      });
    });

    /* --------------------------- 点击统计数字切 Tab -------------------------- */

    root.querySelector('.profile-stats')?.addEventListener('click', (event) => {
      const stat = event.target.closest('[data-goto-tab]');
      if (stat && stat.dataset.gotoTab !== currentTab) activateTab(stat.dataset.gotoTab);
    });

    /* --------------------------------- 资料编辑 ----------------------------- */

    const openEditProfile = () => {
      const current = auth.me;
      let avatarFile = null;
      let backgroundFile = null;
      let avatarPreviewUrl = null;

      openDialog({
        title: '编辑资料',
        desc: '留空的字段将保持原样。',
        size: 'lg',
        body: `<div class="stack">
          <div class="form-grid">
            <div class="field span-2">
              <span class="field-label">头像</span>
              <div class="row">
                <span id="edit-avatar-preview">${avatar(current, 'lg')}</span>
                <input id="edit-avatar-input" type="file" accept="image/*" hidden>
                <button class="btn btn-outline btn-sm" type="button" id="pick-avatar">${icons.upload()} 选择头像</button>
                <span class="field-hint">jpg / png，建议 200×200，不超过 5MB</span>
              </div>
            </div>

            <div class="field span-2">
              <span class="field-label">背景图</span>
              <input id="edit-background-input" type="file" accept="image/*" class="input" style="padding:9px 12px">
              <span class="field-hint">建议上传横向图片，展示效果更好。</span>
            </div>

            <div class="field">
              <label class="field-label" for="edit-nickname">昵称</label>
              <input class="input" id="edit-nickname" type="text" maxlength="24"
                value="${escapeHtml(current.nickname || '')}" placeholder="2 - 24 个字符">
              <span class="field-hint">不能包含 @ # $ % 等特殊字符</span>
            </div>

            <div class="field">
              <label class="field-label" for="edit-lumenote">时光笔记号</label>
              <input class="input" id="edit-lumenote" type="text" maxlength="15"
                placeholder="留空则不修改">
              <span class="field-hint">6 - 15 位字母、数字或下划线</span>
            </div>

            <div class="field">
              <label class="field-label" for="edit-sex">性别</label>
              <select class="select" id="edit-sex">
                ${SEX_OPTIONS.map(
                  (item) =>
                    `<option value="${escapeHtml(item.value)}">${escapeHtml(item.label)}</option>`
                ).join('')}
              </select>
            </div>

            <div class="field">
              <label class="field-label" for="edit-birthday">生日</label>
              <input class="input" id="edit-birthday" type="date">
            </div>

            <div class="field span-2">
              <label class="field-label" for="edit-introduction">个人简介</label>
              <textarea class="textarea" id="edit-introduction" maxlength="100"
                placeholder="介绍一下自己（不超过 100 个字符）">${escapeHtml(current.introduction || '')}</textarea>
            </div>
          </div>
          <p class="field-error" id="profile-form-error" hidden></p>
        </div>`,
        footer: `<button class="btn btn-ghost" type="button" data-role="cancel">取消</button>
          <button class="btn btn-primary" type="button" id="save-profile">保存修改</button>`,
        onMount({ dialog, close }) {
          const errorEl = dialog.querySelector('#profile-form-error');
          const saveBtn = dialog.querySelector('#save-profile');
          const avatarInput = dialog.querySelector('#edit-avatar-input');
          const previewEl = dialog.querySelector('#edit-avatar-preview');

          const showError = (message) => {
            errorEl.textContent = message;
            errorEl.hidden = false;
          };

          dialog.querySelector('[data-role="cancel"]')?.addEventListener('click', () => close());

          dialog.querySelector('#pick-avatar')?.addEventListener('click', () => {
            avatarInput.click();
          });

          avatarInput?.addEventListener('change', () => {
            const file = avatarInput.files?.[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
              showError('请选择图片文件作为头像');
              return;
            }
            if (file.size > 5 * 1024 * 1024) {
              showError('头像不能超过 5MB');
              return;
            }
            avatarFile = file;
            errorEl.hidden = true;
            if (avatarPreviewUrl) URL.revokeObjectURL(avatarPreviewUrl);
            avatarPreviewUrl = URL.createObjectURL(file);
            previewEl.innerHTML = `<span class="avatar avatar-lg"><img src="${avatarPreviewUrl}" alt="头像预览"></span>`;
          });

          dialog
            .querySelector('#edit-background-input')
            ?.addEventListener('change', (event) => {
              const file = event.target.files?.[0];
              if (!file) return;
              if (!file.type.startsWith('image/')) {
                showError('请选择图片文件作为背景图');
                return;
              }
              if (file.size > 8 * 1024 * 1024) {
                showError('背景图不能超过 8MB');
                return;
              }
              backgroundFile = file;
              errorEl.hidden = true;
            });

          saveBtn?.addEventListener('click', async () => {
            const nickname = dialog.querySelector('#edit-nickname').value.trim();
            const lumenoteId = dialog.querySelector('#edit-lumenote').value.trim();
            const sex = dialog.querySelector('#edit-sex').value;
            const birthday = dialog.querySelector('#edit-birthday').value;
            const introduction = dialog
              .querySelector('#edit-introduction')
              .value.trim();

            if (nickname && (nickname.length < 2 || nickname.length > 24)) {
              showError('昵称需要 2 - 24 个字符');
              return;
            }
            if (nickname && NICKNAME_RE.test(nickname)) {
              showError('昵称不能包含 @ # $ % 等特殊字符');
              return;
            }
            if (lumenoteId && !LUMENOTE_ID_RE.test(lumenoteId)) {
              showError('时光笔记号需要 6 - 15 位字母、数字或下划线');
              return;
            }
            if (introduction.length > 100) {
              showError('个人简介不能超过 100 个字符');
              return;
            }

            const formData = new FormData();
            let changed = false;

            if (nickname && nickname !== (current.nickname || '')) {
              formData.append('nickname', nickname);
              changed = true;
            }
            if (lumenoteId) {
              formData.append('lumenoteId', lumenoteId);
              changed = true;
            }
            if (sex !== '') {
              formData.append('sex', sex);
              changed = true;
            }
            if (birthday) {
              formData.append('birthday', birthday);
              changed = true;
            }
            if (introduction && introduction !== (current.introduction || '')) {
              formData.append('introduction', introduction);
              changed = true;
            }
            if (avatarFile) {
              formData.append('avatar', avatarFile, avatarFile.name);
              changed = true;
            }
            if (backgroundFile) {
              formData.append('backgroundImg', backgroundFile, backgroundFile.name);
              changed = true;
            }

            if (!changed) {
              showError('没有需要保存的修改');
              return;
            }

            errorEl.hidden = true;
            setLoading(saveBtn, true);

            try {
              await api.updateUserInfo(formData, { signal: ctx.signal });
              await resolveMe({ signal: ctx.signal }).catch(() => {});

              close();
              toast('资料已更新', 'success');
              navigate('/me', {}, { replace: true });
            } catch (err) {
              if (isAbortError(err) || ctx.signal.aborted) return;
              showError(err.message || '保存失败');
              toast(err.message || '保存失败', 'error');
            } finally {
              setLoading(saveBtn, false);
            }
          });
        },
        onClose() {
          if (avatarPreviewUrl) URL.revokeObjectURL(avatarPreviewUrl);
        },
      });
    };

    /* --------------------------------- 修改密码 ----------------------------- */

    const openChangePassword = () => {
      openDialog({
        title: '修改密码',
        desc: '修改成功后，下次登录请使用新密码。',
        size: 'sm',
        body: `<div class="stack">
          <div class="field">
            <label class="field-label" for="new-password">新密码</label>
            <input class="input" id="new-password" type="password" autocomplete="new-password"
              placeholder="至少 6 位">
          </div>
          <div class="field">
            <label class="field-label" for="confirm-password">确认新密码</label>
            <input class="input" id="confirm-password" type="password" autocomplete="new-password"
              placeholder="再次输入新密码">
          </div>
          <p class="field-error" id="password-error" hidden></p>
        </div>`,
        footer: `<button class="btn btn-ghost" type="button" data-role="cancel">取消</button>
          <button class="btn btn-primary" type="button" id="save-password">确认修改</button>`,
        onMount({ dialog, close }) {
          const errorEl = dialog.querySelector('#password-error');
          const saveBtn = dialog.querySelector('#save-password');
          const newInput = dialog.querySelector('#new-password');
          const confirmInput = dialog.querySelector('#confirm-password');

          dialog.querySelector('[data-role="cancel"]')?.addEventListener('click', () => close());

          saveBtn?.addEventListener('click', async () => {
            const password = newInput.value;
            const confirm = confirmInput.value;

            errorEl.hidden = true;
            if (password.length < 6) {
              errorEl.textContent = '新密码至少 6 位';
              errorEl.hidden = false;
              return;
            }
            if (password !== confirm) {
              errorEl.textContent = '两次输入的密码不一致';
              errorEl.hidden = false;
              return;
            }

            setLoading(saveBtn, true);
            try {
              await api.updatePassword(password, { signal: ctx.signal });
              close();
              toast('密码已更新', 'success');
            } catch (err) {
              if (isAbortError(err) || ctx.signal.aborted) return;
              errorEl.textContent = err.message || '修改密码失败';
              errorEl.hidden = false;
            } finally {
              setLoading(saveBtn, false);
            }
          });
        },
      });
    };

    /* ---------------------------------- 交互绑定 ---------------------------- */

    root.querySelector('#edit-profile')?.addEventListener('click', openEditProfile);
    root.querySelector('#change-password')?.addEventListener('click', openChangePassword);
    root.querySelector('#logout')?.addEventListener('click', async () => {
      const ok = await confirmDialog({
        title: '退出登录',
        message: '哎呀，这就要走啦？',
        confirmText: '退出登录',
        danger: true,
      });
      if (!ok) return;
      await logout();
      toast('已退出登录', 'success');
      navigate('/explore', {}, { replace: true });
    });

    /* ---------------------------------- 初始化 ------------------------------ */

    loadStats();
    if (currentTab === 'recent') renderRecent();
    else if (NOTE_TABS.includes(currentTab)) notes.setTab(currentTab);
    else relations.setTab(currentTab);

    // 预加载其它笔记 tab 的数量，避免未访问的 tab 一直显示「—」
    notes.preloadTotals(NOTE_TABS.includes(currentTab) ? currentTab : undefined);

    // 从顶部头像菜单点「编辑资料」进来（#/me?edit=1）时，自动打开编辑弹窗
    if (ctx.query.edit) {
      window.history.replaceState(null, '', '#/me');
      openEditProfile();
    }
  },
};

function maskPhone(phone) {
  const text = String(phone || '');
  if (!text) return '未记录';
  if (text.length !== 11) return text;
  return `${text.slice(0, 3)}****${text.slice(7)}`;
}

function renderBindCard(root, ctx) {
  root.innerHTML = `<div class="page">
    <div class="panel panel-pad" style="max-width:560px;margin:28px auto">
      <h1 class="page-title">需要绑定登录手机号</h1>
      <p class="page-sub">请填写本次登录使用的手机号，以便继续。</p>
      <form id="bind-form" class="stack" style="margin-top:20px">
        <div class="field">
          <label class="field-label" for="bind-phone">登录手机号</label>
          <input class="input" id="bind-phone" type="tel" inputmode="numeric" maxlength="11"
            placeholder="请输入 11 位手机号" value="${escapeHtml(auth.phone || '')}">
        </div>
        <button class="btn btn-primary" type="submit" id="bind-submit">继续</button>
      </form>
      <p class="field-hint" style="margin-top:12px">如果手机号不正确，请退出后重新登录。</p>
      <button class="btn btn-ghost" type="button" id="bind-logout" style="margin-top:10px">退出登录</button>
    </div>
  </div>`;

  root.querySelector('#bind-form')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const input = root.querySelector('#bind-phone');
    const phone = input.value.trim();
    if (!PHONE_RE.test(phone)) {
      toast('请输入 11 位手机号', 'warning');
      input.focus();
      return;
    }

    auth.setPhone(phone);
    const btn = root.querySelector('#bind-submit');
    setLoading(btn, true);

    try {
      const me = await resolveMe({ signal: ctx.signal });
      if (!me) throw new Error('未找到该手机号对应的用户，请确认后重试');
      toast('已获取用户资料', 'success');
      navigate('/me', {}, { replace: true });
    } catch (err) {
      if (isAbortError(err) || ctx.signal.aborted) return;
      toast(err.message || '获取用户资料失败', 'error');
    } finally {
      setLoading(btn, false);
    }
  });

  root.querySelector('#bind-logout')?.addEventListener('click', async () => {
    await logout();
    toast('已退出登录', 'success');
    navigate('/login', {}, { replace: true });
  });
}

/**
 * 已登录且手机号已知，但资料接口没返回（网关/用户服务异常）。
 * 这个场景下再让用户“绑定手机号”是误导，直接给出失败原因和重试入口。
 */
function renderLoadFailCard(root, ctx, error) {
  const reason = error?.message || '资料接口暂时不可用';
  root.innerHTML = `<div class="page">
    <div class="panel panel-pad" style="max-width:560px;margin:28px auto">
      <h1 class="page-title">资料加载失败</h1>
      <p class="page-sub">已登录（手机号 ${escapeHtml(maskPhone(auth.phone))}），但没能取到你的资料。</p>
      <p class="field-hint" style="margin-top:8px">原因：${escapeHtml(reason)}</p>
      <div style="display:flex;gap:10px;margin-top:20px">
        <button class="btn btn-primary" type="button" id="profile-retry">重试</button>
        <button class="btn btn-ghost" type="button" id="profile-logout">退出登录</button>
      </div>
    </div>
  </div>`;

  root.querySelector('#profile-retry')?.addEventListener('click', async () => {
    const btn = root.querySelector('#profile-retry');
    setLoading(btn, true);

    try {
      const me = await resolveMe({ signal: ctx.signal });
      if (!me) throw new Error('未查询到该手机号对应的用户，请确认手机号是否正确');
      toast('已获取用户资料', 'success');
      navigate('/me', {}, { replace: true });
    } catch (err) {
      if (isAbortError(err) || ctx.signal.aborted) return;
      toast(err.message || '资料加载失败', 'error');
    } finally {
      setLoading(btn, false);
    }
  });

  root.querySelector('#profile-logout')?.addEventListener('click', async () => {
    await logout();
    toast('已退出登录', 'success');
    navigate('/login', {}, { replace: true });
  });
}
