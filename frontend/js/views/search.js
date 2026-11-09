/** 搜索页：笔记 / 用户双 Tab，支持类型、排序、时间范围筛选与分页 */

import { api } from '../api.js';
import {
  HOT_KEYWORDS,
  NOTE_RANGE_OPTIONS,
  NOTE_SORT_OPTIONS,
  NOTE_TYPE_OPTIONS,
} from '../constants.js';
import { icons } from '../icons.js';
import {
  getRecentSearches,
  cacheNoteMeta,
  clearRecentSearches,
  isFollowing,
  pushRecentSearch,
  setFollowing,
} from '../local.js';
import { navigate } from '../router.js';
import { auth } from '../store.js';
import {
  bindPagination,
  emptyState,
  errorState,
  followButton,
  noteCard,
  pagination,
  skeletonGrid,
  skeletonRows,
  toast,
  userRow,
} from '../ui.js';
import {
  escapeHtml,
  formatCount,
  isAbortError,
  relativeTime,
  scrollToTop,
} from '../utils.js';

export default {
  async mount(root, ctx) {
    const tab = ctx.query.tab === 'users' ? 'users' : 'notes';
    const state = {
      q: String(ctx.query.q || '').trim().slice(0, 60),
      tab,
      page: Math.max(1, Number(ctx.query.page) || 1),
      type: ctx.query.type ?? '',
      sort: ctx.query.sort ?? '',
      range: ctx.query.range ?? '',
    };

    root.innerHTML = `<div class="search-page">
      <section class="search-head">
        <div class="search-head-top">
          <div>
            <h1 class="page-title">搜索</h1>
            <p class="page-sub">搜索笔记标题、话题与用户昵称</p>
          </div>
          <div class="tabs" role="tablist" aria-label="搜索类型">
            <button class="tab${state.tab === 'notes' ? ' is-active' : ''}" type="button"
              data-tab="notes" role="tab" aria-selected="${state.tab === 'notes'}">笔记</button>
            <button class="tab${state.tab === 'users' ? ' is-active' : ''}" type="button"
              data-tab="users" role="tab" aria-selected="${state.tab === 'users'}">用户</button>
          </div>
        </div>
        <form class="search-big" id="search-form" role="search">
          <div class="input-wrap">
            <label class="sr-only" for="search-keyword">搜索关键词</label>
            <input class="input" id="search-keyword" type="search" name="q"
              placeholder="搜索笔记、用户，例如：咖啡、旅行" autocomplete="off"
              value="${escapeHtml(state.q)}">
          </div>
          <button class="btn btn-primary" type="submit">${icons.search()} 搜索</button>
        </form>
      </section>

      <div id="search-filters"></div>
      <div id="search-results"></div>
    </div>`;

    const form = root.querySelector('#search-form');
    const keywordInput = root.querySelector('#search-keyword');
    const filtersEl = root.querySelector('#search-filters');
    const resultsEl = root.querySelector('#search-results');
    const tabButtons = [...root.querySelectorAll('[data-tab]')];

    let requestSeq = 0;

    /* ------------------------------ URL 同步 ------------------------------ */

    const urlForState = () => {
      const params = new URLSearchParams();
      if (state.q) params.set('q', state.q);
      if (state.tab !== 'notes') params.set('tab', state.tab);
      if (state.page > 1) params.set('page', String(state.page));
      if (state.type !== '') params.set('type', state.type);
      if (state.sort !== '') params.set('sort', state.sort);
      if (state.range !== '') params.set('range', state.range);
      const qs = params.toString();
      return `#/search${qs ? `?${qs}` : ''}`;
    };

    const syncUrl = (push = false) => {
      const target = urlForState();
      const headerInput = document.getElementById('header-search-input');
      if (headerInput) headerInput.value = state.q;
      if (window.location.hash === target) return;
      if (push) window.history.pushState(null, '', target);
      else window.history.replaceState(null, '', target);
    };

    /* ------------------------------- 筛选栏 ------------------------------- */

    const optionHtml = (options, current) =>
      options
        .map(
          (item) =>
            `<option value="${escapeHtml(item.value)}"${
              String(item.value) === String(current) ? ' selected' : ''
            }>${escapeHtml(item.label)}</option>`
        )
        .join('');

    const renderFilters = () => {
      if (state.tab !== 'notes' || !state.q) {
        filtersEl.innerHTML = '';
        return;
      }

      const hasFilter = state.type !== '' || state.sort !== '' || state.range !== '';
      filtersEl.innerHTML = `<div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">类型</span>
          <select class="select" data-filter="type" aria-label="笔记类型">
            ${optionHtml(NOTE_TYPE_OPTIONS, state.type)}
          </select>
        </div>
        <div class="filter-group">
          <span class="filter-label">排序</span>
          <select class="select" data-filter="sort" aria-label="排序方式">
            ${optionHtml(NOTE_SORT_OPTIONS, state.sort)}
          </select>
        </div>
        <div class="filter-group">
          <span class="filter-label">时间</span>
          <select class="select" data-filter="range" aria-label="发布时间范围">
            ${optionHtml(NOTE_RANGE_OPTIONS, state.range)}
          </select>
        </div>
        <span class="filter-spacer"></span>
        ${
          hasFilter
            ? '<button class="btn btn-ghost btn-xs" type="button" id="reset-filters">重置筛选</button>'
            : ''
        }
      </div>`;

      filtersEl.querySelectorAll('[data-filter]').forEach((select) => {
        select.addEventListener('change', () => {
          state[select.dataset.filter] = select.value;
          state.page = 1;
          syncUrl(true);
          renderFilters();
          load();
        });
      });

      filtersEl.querySelector('#reset-filters')?.addEventListener('click', () => {
        state.type = '';
        state.sort = '';
        state.range = '';
        state.page = 1;
        syncUrl(true);
        renderFilters();
        load();
      });
    };

    /* ------------------------------- 结果渲染 ------------------------------ */

    const renderNoteResults = (res) => {
      const list = Array.isArray(res?.data) ? res.data : [];
      const total = Number(res?.totalCount) || 0;
      const totalPage = Number(res?.totalPage) || 1;
      const pageNo = Number(res?.pageNo) || state.page;

      list.forEach((note) => cacheNoteMeta(note));

      const header = `<div class="results-head">
        <span>找到 <strong>${formatCount(total)}</strong> 条与「${escapeHtml(state.q)}」相关的笔记</span>
        <span class="badge badge-outline">点击卡片查看详情</span>
      </div>`;

      if (!list.length) {
        resultsEl.innerHTML = `${header}${emptyState({
          icon: 'search',
          title: `没有找到「${state.q}」相关的笔记`,
          desc: '试试更换关键词，或者放宽类型、排序与时间范围；新发布的笔记可能需要稍等片刻才会出现。',
          actions: [
            { label: '重置筛选', id: 'empty-reset' },
            { label: '搜索用户', href: '#/search?tab=users' },
          ],
        })}`;
        resultsEl.querySelector('#empty-reset')?.addEventListener('click', () => {
          state.type = '';
          state.sort = '';
          state.range = '';
          state.page = 1;
          syncUrl(true);
          renderFilters();
          load();
        });
        return;
      }

      resultsEl.innerHTML = `${header}
        <div class="note-grid">${list.map((note) => noteCard(note)).join('')}</div>
        ${pagination({ pageNo, totalPage, totalCount: total, id: 'search-pager' })}`;

      bindPagination(resultsEl.querySelector('#search-pager'), (page) => {
        state.page = page;
        syncUrl(true);
        load();
        scrollToTop(true);
      });
    };

    const renderUserResults = (res) => {
      const list = Array.isArray(res?.data) ? res.data : [];
      const total = Number(res?.totalCount) || 0;
      const totalPage = Number(res?.totalPage) || 1;
      const pageNo = Number(res?.pageNo) || state.page;

      const header = `<div class="results-head">
        <span>找到 <strong>${formatCount(total)}</strong> 个与「${escapeHtml(state.q)}」相关的用户</span>
      </div>`;

      if (!list.length) {
        resultsEl.innerHTML = `${header}${emptyState({
          icon: 'users',
          title: `没有找到「${state.q}」相关的用户`,
          desc: '可以尝试昵称的一部分，或者使用拾光笔记号搜索。',
          actions: [
            { label: '搜索笔记', href: `#/search?q=${encodeURIComponent(state.q)}` },
            { label: '回到发现', href: '#/explore' },
          ],
        })}`;
        return;
      }

      resultsEl.innerHTML = `${header}
        <div class="user-list">
          ${list
            .map((user) =>
              userRow(user, {
                actionHtml: followButton(
                  user.userId,
                  isFollowing(user.userId)
                ),
              })
            )
            .join('')}
        </div>
        ${pagination({ pageNo, totalPage, totalCount: total, id: 'search-pager' })}`;

      bindPagination(resultsEl.querySelector('#search-pager'), (page) => {
        state.page = page;
        syncUrl(true);
        load();
        scrollToTop(true);
      });

      bindFollowButtons(resultsEl);
    };

    const renderLanding = () => {
      const recent = getRecentSearches();
      resultsEl.innerHTML = `<div class="search-landing">
        <section class="search-block">
          <h3>${icons.zap()} 热门搜索</h3>
          <div class="hot-keywords">
            ${HOT_KEYWORDS.map(
              (keyword) =>
                `<button class="chip" type="button" data-keyword="${escapeHtml(keyword)}">${escapeHtml(
                  keyword
                )}</button>`
            ).join('')}
          </div>
          <div class="search-hint">
            输入关键词开始搜索。
          </div>
        </section>

        <section class="search-block">
          <h3>${icons.clock()} 最近搜索</h3>
          ${
            recent.length
              ? `<div class="recent-searches">
                  ${recent
                    .map(
                      (item) => `<button class="recent-search" type="button" data-keyword="${escapeHtml(
                        item.keyword
                      )}">
                        ${icons.search()}
                        <span>${escapeHtml(item.keyword)}</span>
                        <span class="time">${relativeTime(item.at)}</span>
                      </button>`
                    )
                    .join('')}
                </div>
                <div class="row" style="margin-top:10px">
                  <button class="link-btn" type="button" id="clear-searches">清空最近搜索</button>
                </div>`
              : `<p class="text-muted text-sm">还没有搜索记录，试试上面的热门关键词。</p>`
          }
        </section>
      </div>`;

      resultsEl.querySelectorAll('[data-keyword]').forEach((button) => {
        button.addEventListener('click', () => {
          applyKeyword(button.dataset.keyword);
        });
      });

      resultsEl.querySelector('#clear-searches')?.addEventListener('click', () => {
        clearRecentSearches();
        renderLanding();
        toast('已清空最近搜索', 'success');
      });
    };

    /* --------------------------------- 加载 -------------------------------- */

    const load = async () => {
      const seq = ++requestSeq;
      renderFilters();

      if (!state.q) {
        renderLanding();
        return;
      }

      resultsEl.innerHTML =
        state.tab === 'notes' ? skeletonGrid(8) : skeletonRows(5);

      try {
        const res =
          state.tab === 'notes'
            ? await api.searchNotes(
                {
                  keyword: state.q,
                  pageNo: state.page,
                  type: state.type,
                  sort: state.sort,
                  publishTimeRange: state.range,
                },
                { signal: ctx.signal }
              )
            : await api.searchUsers(
                { keyword: state.q, pageNo: state.page },
                { signal: ctx.signal }
              );

        if (seq !== requestSeq || ctx.signal.aborted) return;
        if (state.tab === 'notes') renderNoteResults(res);
        else renderUserResults(res);
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        if (seq !== requestSeq) return;

        resultsEl.innerHTML = errorState({
          error: err,
          title: '搜索失败',
          desc: '搜索服务暂时不可用，请稍后重试。',
          retryId: 'search-retry',
        });
        resultsEl.querySelector('#search-retry')?.addEventListener('click', () => load());
      }
    };

    /* -------------------------------- 交互绑定 ------------------------------ */

    const applyKeyword = (keyword, { push = true } = {}) => {
      const value = String(keyword || '').trim().slice(0, 60);
      if (!value) {
        toast('请输入搜索关键词', 'warning');
        keywordInput.focus();
        return;
      }
      state.q = value;
      state.page = 1;
      keywordInput.value = value;
      pushRecentSearch(value);
      syncUrl(push);
      load();
      scrollToTop(true);
    };

    form?.addEventListener('submit', (event) => {
      event.preventDefault();
      applyKeyword(keywordInput.value);
    });

    tabButtons.forEach((button) => {
      button.addEventListener('click', () => {
        const next = button.dataset.tab;
        if (next === state.tab) return;
        state.tab = next;
        state.page = 1;
        tabButtons.forEach((item) => {
          const active = item.dataset.tab === next;
          item.classList.toggle('is-active', active);
          item.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        syncUrl(true);
        load();
      });
    });

    /* -------------------------------- 关注逻辑 ------------------------------ */

    async function handleFollow(button) {
      const userId = button.dataset.followUser;
      if (!userId) return;

      if (!auth.isLoggedIn) {
        toast('登录后即可关注用户', 'warning');
        navigate('/login', {
          redirect: window.location.hash.replace(/^#/, '') || '/search',
        });
        return;
      }

      const followed = isFollowing(userId);
      button.disabled = true;

      try {
        if (followed) await api.unfollow(userId, { signal: ctx.signal });
        else await api.follow(userId, { signal: ctx.signal });

        setFollowing(userId, !followed);
        paintFollowButton(button, !followed);
        toast(followed ? '已取消关注' : '关注成功', 'success');
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;

        if (err.code === 'RELATION-20004') {
          setFollowing(userId, true);
          paintFollowButton(button, true);
          toast('已经关注过该用户', 'info');
        } else if (err.code === 'RELATION-20006') {
          setFollowing(userId, false);
          paintFollowButton(button, false);
          toast('原本就未关注该用户', 'info');
        } else {
          toast(err.message || '关注操作失败', 'error');
        }
      } finally {
        button.disabled = false;
      }
    }

    function paintFollowButton(button, followed) {
      button.dataset.followed = followed ? '1' : '0';
      button.textContent = followed ? '已关注' : '关注';
      button.classList.toggle('btn-primary', !followed);
      button.classList.toggle('btn-outline', followed);
    }

    function bindFollowButtons(container) {
      container.querySelectorAll('[data-follow-user]').forEach((button) => {
        button.addEventListener('click', () => handleFollow(button));
      });
    }

    /* --------------------------------- 启动 -------------------------------- */

    const headerInput = document.getElementById('header-search-input');
    if (headerInput) headerInput.value = state.q;

    if (state.q) {
      pushRecentSearch(state.q);
      await load();
    } else {
      renderFilters();
      renderLanding();
    }
  },
};
