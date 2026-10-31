/** 他人主页：资料、关注状态、关注 / 粉丝列表 */

import { api } from '../api.js';
import { icons } from '../icons.js';
import { isFollowing, setFollowing } from '../local.js';
import { back, navigate } from '../router.js';
import { auth } from '../store.js';
import {
  avatar,
  emptyState,
  errorState,
  toast,
} from '../ui.js';
import { escapeHtml, formatCount, isAbortError, safeUrl } from '../utils.js';
import { createNotesPanel } from './notes-panel.js';
import { createRelationsPanel } from './relations-panel.js';

export default {
  async mount(root, ctx) {
    const userId = String(ctx.params.id || '');

    if (!/^\d+$/.test(userId)) {
      root.innerHTML = `<div class="page">${emptyState({
        icon: 'alert',
        title: '用户 ID 不合法',
        desc: '请从用户卡片或笔记作者入口进入主页。',
        actions: [{ label: '回到发现', href: '#/explore', variant: 'btn-primary' }],
      })}</div>`;
      return;
    }

    if (auth.me?.id && String(auth.me.id) === userId) {
      navigate('/me', {}, { replace: true });
      return;
    }

    root.innerHTML = `<div class="page">
      <div class="skeleton" style="height:180px;border-radius:24px"></div>
      <div class="skeleton-card" style="padding:24px;gap:14px">
        <div class="skeleton skeleton-line" style="width:35%;height:20px"></div>
        <div class="skeleton skeleton-line" style="width:55%"></div>
        <div class="skeleton skeleton-line" style="width:70%"></div>
      </div>
    </div>`;

    let user = null;
    let followed = isFollowing(userId);
    const NOTE_TABS = ['publish', 'collect', 'like'];
    let initialTab = [...NOTE_TABS, 'following', 'fans'].includes(ctx.query.tab)
      ? ctx.query.tab
      : 'publish';

    try {
      user = await api.findById(userId, { signal: ctx.signal });
      if (!user) throw Object.assign(new Error('该用户不存在'), { code: 'USER-20007' });
    } catch (err) {
      if (isAbortError(err) || ctx.signal.aborted) return;

      root.innerHTML = `<div class="page">
        <div class="detail-topbar">
          <button class="back-link" type="button" id="user-back">${icons.arrowLeft()} 返回</button>
        </div>
        ${errorState({
          error: err,
          title: err.code === 'USER-20007' ? '用户不存在' : '用户资料加载失败',
          retryId: 'user-retry',
        })}
      </div>`;
      root.querySelector('#user-back')?.addEventListener('click', () => back('/explore'));
      root.querySelector('#user-retry')?.addEventListener('click', () => {
        navigate(`/user/${userId}`, {}, { replace: true });
      });
      return;
    }

    const displayName = user.nickname || '时光笔记用户';
    const coverUrl = safeUrl(user.backgroundImg || '');

    root.innerHTML = `<div class="profile-page">
      <div class="detail-topbar">
        <button class="back-link" type="button" id="user-back">${icons.arrowLeft()} 返回</button>
        <span class="badge badge-outline">用户主页</span>
      </div>

      <section class="profile-hero">
        <div class="profile-banner">
        <div class="profile-cover${coverUrl ? ' has-image' : ''}"${
          coverUrl ? ` style="background-image:url('${escapeHtml(coverUrl)}')"` : ''
        }></div>
        <div class="profile-head">
          <div class="profile-avatar">${avatar(user, 'xl', { ring: true })}</div>
          <div class="profile-info">
            <div class="profile-name">${escapeHtml(displayName)}</div>
            <div class="profile-id">
              <span>用户 ID <code>${escapeHtml(String(user.id))}</code></span>
            </div>
            <p class="profile-bio">${escapeHtml(user.introduction || '这个人很懒，还没有填写个人简介。')}</p>
          </div>
          <div class="profile-actions">
            <button class="btn ${followed ? 'btn-outline' : 'btn-primary'} btn-sm" type="button"
              id="user-follow" data-followed="${followed ? '1' : '0'}">
              ${followed ? '已关注' : '关注'}
            </button>
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
          <div class="tabs-line" role="tablist" aria-label="用户内容">
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
          </div>
        </div>

        <div class="profile-body" id="user-body"></div>
      </section>
    </div>`;

    const bodyEl = root.querySelector('#user-body');
    const tabs = [...root.querySelectorAll('[data-tab]')];

    root.querySelector('#user-back')?.addEventListener('click', () => back('/explore'));

    const notes = createNotesPanel({
      container: bodyEl,
      userId,
      signal: ctx.signal,
      onTotal(tab, total) {
        const badge = root.querySelector(`#count-${tab}`);
        if (badge) badge.textContent = formatCount(total);
      },
    });

    const relations = createRelationsPanel({
      container: bodyEl,
      userId,
      signal: ctx.signal,
      onTotal(tab, total) {
        const badge = root.querySelector(
          tab === 'following' ? '#count-following' : '#count-fans'
        );
        if (badge) badge.textContent = formatCount(total);
      },
    });

    const activateTab = (next) => {
      tabs.forEach((item) => item.classList.toggle('is-active', item.dataset.tab === next));
      initialTab = next;
      if (NOTE_TABS.includes(next)) notes.setTab(next);
      else relations.setTab(next);
    };

    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        if (tab.dataset.tab !== initialTab) activateTab(tab.dataset.tab);
      });
    });

    /* --------------------------- 点击统计数字切 Tab -------------------------- */

    root.querySelector('.profile-stats')?.addEventListener('click', (event) => {
      const stat = event.target.closest('[data-goto-tab]');
      if (stat && stat.dataset.gotoTab !== initialTab) activateTab(stat.dataset.gotoTab);
    });

    /* ------------------------------- 统计数字 ------------------------------- */

    api
      .followingList(userId, 1, { signal: ctx.signal })
      .then((res) => {
        if (ctx.signal.aborted) return;
        const total = Number(res?.totalCount) || 0;
        const el = root.querySelector('#stat-following');
        if (el) el.textContent = formatCount(total);
        const badge = root.querySelector('#count-following');
        if (badge) badge.textContent = formatCount(total);
      })
      .catch(() => {});

    api
      .fansList(userId, 1, { signal: ctx.signal })
      .then((res) => {
        if (ctx.signal.aborted) return;
        const total = Number(res?.totalCount) || 0;
        const el = root.querySelector('#stat-fans');
        if (el) el.textContent = formatCount(total);
        const badge = root.querySelector('#count-fans');
        if (badge) badge.textContent = formatCount(total);
      })
      .catch(() => {});

    // 获赞 / 被收藏（计数服务）
    api
      .userCount(userId, { signal: ctx.signal })
      .then((count) => {
        if (ctx.signal.aborted || !count) return;

        const likeEl = root.querySelector('#stat-like');
        if (likeEl) likeEl.textContent = formatCount(Number(count.likeTotal) || 0);

        const collectEl = root.querySelector('#stat-collect');
        if (collectEl) collectEl.textContent = formatCount(Number(count.collectTotal) || 0);
      })
      .catch(() => {});

    /* -------------------------------- 关注按钮 ------------------------------ */

    root.querySelector('#user-follow')?.addEventListener('click', async (event) => {
      const button = event.currentTarget;
      button.disabled = true;
      const next = !followed;

      try {
        if (next) await api.follow(userId, { signal: ctx.signal });
        else await api.unfollow(userId, { signal: ctx.signal });

        followed = next;
        setFollowing(userId, followed);
        paint(button);
        toast(followed ? '关注成功' : '已取消关注', 'success');
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;

        if (err.code === 'RELATION-20004') {
          followed = true;
          setFollowing(userId, true);
          paint(button);
          toast('已经关注过该用户', 'info');
        } else if (err.code === 'RELATION-20006') {
          followed = false;
          setFollowing(userId, false);
          paint(button);
          toast('之前并未关注该用户', 'info');
        } else if (err.code === 'RELATION-20001') {
          toast('不能关注自己', 'warning');
        } else {
          toast(err.message || '关注操作失败', 'error');
        }
      } finally {
        button.disabled = false;
      }
    });

    function paint(button) {
      button.textContent = followed ? '已关注' : '关注';
      button.classList.toggle('btn-primary', !followed);
      button.classList.toggle('btn-outline', followed);
      button.dataset.followed = followed ? '1' : '0';
    }

    if (NOTE_TABS.includes(initialTab)) notes.setTab(initialTab);
    else relations.setTab(initialTab);

    // 预加载其它笔记 tab 的数量
    notes.preloadTotals(NOTE_TABS.includes(initialTab) ? initialTab : undefined);
  },
};
