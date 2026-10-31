/** 笔记详情：媒体、正文、点赞/收藏/关注、作者工具 */

import { api } from '../api.js';
import { icons } from '../icons.js';
import {
  cacheNoteMeta,
  getInteraction,
  getNoteMeta,
  getTopState,
  isFollowing,
  pushRecentNote,
  setFollowing,
  setInteraction,
  setTopState,
} from '../local.js';
import { back, navigate } from '../router.js';
import { auth } from '../store.js';
import {
  avatar,
  badge,
  confirmDialog,
  emptyState,
  errorState,
  followButton,
  showLightbox,
  toast,
} from '../ui.js';
import {
  copyText,
  escapeHtml,
  formatCount,
  formatDateTime,
  isAbortError,
  isDigits,
  noteCover,
  noteUrl,
  renderRichText,
  safeUrl,
  topicLabel,
} from '../utils.js';

export default {
  async mount(root, ctx) {
    const noteId = String(ctx.params.id || '');

    if (!isDigits(noteId)) {
      root.innerHTML = `<div class="page">${emptyState({
        icon: 'alert',
        title: '笔记 ID 不合法',
        desc: '请从笔记卡片进入，或在发现页粘贴正确的笔记链接。',
        actions: [{ label: '回到发现', href: '#/explore', variant: 'btn-primary' }],
      })}</div>`;
      return;
    }

    root.innerHTML = `<div class="note-page">
      <div class="detail-topbar">
        <button class="back-link" type="button" id="detail-back">${icons.arrowLeft()} 返回</button>
      </div>
      <div class="detail-grid">
        <div class="skeleton" style="aspect-ratio:4/5;border-radius:22px"></div>
        <div class="skeleton-card" style="padding:24px;gap:16px">
          <div class="skeleton skeleton-avatar"></div>
          <div class="skeleton skeleton-line" style="width:80%;height:22px"></div>
          <div class="skeleton skeleton-line"></div>
          <div class="skeleton skeleton-line"></div>
          <div class="skeleton skeleton-line is-short"></div>
        </div>
      </div>
    </div>`;

    const backButton = root.querySelector('#detail-back');

    let note = null;
    let images = [];
    let currentImage = 0;
    let interaction = getInteraction(noteId);
    let liked = interaction.liked;
    let collected = interaction.collected;
    let followed = false;
    let topped = getTopState(noteId);
    let meta = getNoteMeta(noteId);
    // 真实计数（来自计数服务，缺失时回退到本地快照 meta）
    let counts = null;

    const loadCounts = async () => {
      try {
        const res = await api.noteCount(noteId, { signal: ctx.signal });
        if (!res) return;

        counts = {
          likeTotal: Number(res.likeTotal) || 0,
          collectTotal: Number(res.collectTotal) || 0,
          commentTotal: Number(res.commentTotal) || 0,
        };
      } catch (err) {
        // 计数查询失败不影响笔记主体展示
        if (!isAbortError(err)) console.warn('[detail] 计数查询失败：', err);
      }
    };

    const paintActionCounts = () => {
      if (!counts) return;
      const likeNum = root.querySelector('[data-action="like"] .action-num');
      const collectNum = root.querySelector('[data-action="collect"] .action-num');
      if (likeNum) likeNum.textContent = formatCount(counts.likeTotal);
      if (collectNum) collectNum.textContent = formatCount(counts.collectTotal);
    };

    // 点赞/收藏后计数由 MQ 异步累加（聚合约 2 秒），延迟两次对账，
    // 避免刚点完就被缓存里的旧值覆盖。
    const scheduleCountsRefresh = () => {
      [2600, 5200].forEach((delay) => {
        window.setTimeout(async () => {
          if (ctx.signal.aborted) return;
          await loadCounts();
          paintActionCounts();
        }, delay);
      });
    };

    const load = async () => {
      try {
        note = await api.noteDetail(noteId, { signal: ctx.signal });
        if (!note) throw new Error('笔记不存在');

        images = (Array.isArray(note.imgUris) ? note.imgUris : [])
          .map((uri) => safeUrl(uri))
          .filter(Boolean);

        followed = isFollowing(note.creatorId);
        if (!meta) {
          cacheNoteMeta({ ...note, noteId: note.id });
          meta = getNoteMeta(noteId);
        }

        pushRecentNote({ ...note, noteId: note.id });
        await loadCounts();
        renderDetail();
        autoplayDetailVideo();
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        renderError(err);
      }
    };

    const renderError = (err) => {
      const isNotFound = err.code === 'NOTE-20002';
      const isPrivate = err.code === 'NOTE-20003';

      if (isNotFound || isPrivate) {
        root.innerHTML = `<div class="page">
          <div class="detail-topbar">
            <button class="back-link" type="button" id="detail-back">${icons.arrowLeft()} 返回</button>
          </div>
          <div class="detail-error">${emptyState({
            icon: isPrivate ? 'lock' : 'file',
            title: isPrivate ? '作者已将该笔记设为私密' : '笔记不存在或已被删除',
            desc: isPrivate
              ? '这篇笔记仅作者本人可见。'
              : '它可能已被删除，或者链接中的 ID 不正确。',
            actions: [
              { label: '回到发现', href: '#/explore', variant: 'btn-primary' },
              { label: '去搜索', href: '#/search' },
            ],
          })}</div>
        </div>`;
      } else {
        root.innerHTML = `<div class="page">
          <div class="detail-topbar">
            <button class="back-link" type="button" id="detail-back">${icons.arrowLeft()} 返回</button>
          </div>
          <div class="detail-error">${errorState({
            error: err,
            title: '笔记加载失败',
            retryId: 'detail-retry',
          })}</div>
        </div>`;
        root.querySelector('#detail-retry')?.addEventListener('click', () => {
          root.innerHTML = '<div class="page"><div class="empty"><span class="empty-icon">' + icons.clock() + '</span><p class="empty-title">正在重新加载…</p></div></div>';
          load();
        });
      }
      bindBack();
    };

    const bindBack = () => {
      root.querySelector('#detail-back')?.addEventListener('click', () => back('/explore'));
    };

    const isOwner = () =>
      Boolean(auth.me?.id) && String(auth.me.id) === String(note?.creatorId);

    /**
     * 进入详情页自动播放视频：
     * 优先带声音播放（从列表点击进来时同文档内有点击手势，通常允许）；
     * 被浏览器自动播放策略拦截时，退化为静音播放，用户可通过控制条开声音。
     */
    const autoplayDetailVideo = () => {
      const video = root.querySelector('.media-main video');
      if (!video) return;

      const playMuted = () => {
        video.muted = true;
        const mutedPlay = video.play();
        if (mutedPlay && typeof mutedPlay.catch === 'function') mutedPlay.catch(() => {});
      };

      const playing = video.play();
      if (playing && typeof playing.catch === 'function') playing.catch(playMuted);
    };

    const renderDetail = () => {
      const cover = noteCover({ imgUris: images });
      const isVideo = Number(note.type) === 1 && note.videoUri;
      const topic = topicLabel(note.topicId, note.topicName);
      const visible = Number(note.visible) === 1;
      const title = note.title || '无标题笔记';

      // 优先使用计数服务的真实数字，缺失时回退到本地快照
      const likeNum = counts ? counts.likeTotal : meta?.likeTotal;
      const collectNum = counts ? counts.collectTotal : meta?.collectTotal;
      const hasLikeNum = likeNum !== null && likeNum !== undefined && likeNum !== '';
      const hasCollectNum =
        collectNum !== null && collectNum !== undefined && collectNum !== '';

      root.innerHTML = `<div class="note-page">
        <div class="detail-topbar">
          <button class="back-link" type="button" id="detail-back">${icons.arrowLeft()} 返回</button>
          ${visible ? badge('仅自己可见', 'amber', 'lock') : badge('公开笔记', 'green', 'unlock')}
        </div>

        <div class="detail-grid">
          <div class="detail-media">
            <div class="media-main" id="media-main">
              <div class="media-empty">${icons.image()}<span>${isVideo ? '视频加载中…' : '暂无图片'}</span></div>
              ${
                isVideo
                  ? `<video controls playsinline preload="metadata" src="${escapeHtml(
                      safeUrl(note.videoUri)
                    )}"${cover ? ` poster="${escapeHtml(cover)}"` : ''}></video>`
                  : images.length
                  ? `<img id="main-image" src="${escapeHtml(images[0])}" alt="${escapeHtml(
                      title
                    )}" data-zoom="0">`
                  : ''
              }
              ${
                !isVideo && images.length
                  ? `<button class="media-zoom" type="button" id="media-zoom">${icons.search()} 查看大图</button>`
                  : ''
              }
            </div>
            ${
              images.length > 1
                ? `<div class="media-thumbs" id="media-thumbs">
                    ${images
                      .map(
                        (src, index) => `<button class="media-thumb${
                          index === 0 ? ' is-active' : ''
                        }" type="button" data-index="${index}" aria-label="查看第 ${index + 1} 张图片">
                          <img src="${escapeHtml(src)}" alt="" loading="lazy" data-fallback="1">
                        </button>`
                      )
                      .join('')}
                  </div>`
                : ''
            }
          </div>

          <div class="detail-panel">
            <div class="detail-author">
              <a href="#/user/${encodeURIComponent(String(note.creatorId))}" aria-label="查看作者主页">
                ${avatar({ nickname: note.creatorName, avatar: note.avatar }, 'md')}
              </a>
              <div class="author-name">
                <a href="#/user/${encodeURIComponent(String(note.creatorId))}"><strong>${escapeHtml(
        note.creatorName || '时光笔记用户'
      )}</strong></a>
                <span>时光笔记创作者</span>
              </div>
              ${
                !isOwner()
                  ? followButton(note.creatorId, followed, { small: true })
                  : '<span class="badge badge-brand">我的笔记</span>'
              }
            </div>

            <h1 class="detail-title">${escapeHtml(title)}</h1>

            <div class="detail-meta" id="detail-meta">
              ${Number(note.type) === 1 ? badge('视频', 'blue', 'video') : badge('图文', 'blue', 'image')}
              ${visible ? badge('仅自己可见', 'amber', 'lock') : ''}
              <span>编辑于 ${escapeHtml(formatDateTime(note.updateTime))}</span>
              <span class="meta-dot"></span>
              <span>笔记 ID ${escapeHtml(String(note.id))}</span>
            </div>

            ${topic ? `<span class="note-topic">${icons.grid()} ${escapeHtml(topic)}</span>` : ''}

            <div class="note-content${note.content ? '' : ' is-empty'}">${
        note.content ? renderRichText(note.content) : '作者暂未填写正文内容。'
      }</div>

            <div class="detail-actions">
              <button class="action-btn${liked ? ' is-active' : ''}" type="button" data-action="like"
                aria-pressed="${liked}" aria-label="${liked ? '取消点赞' : '点赞'}">
                ${icons.heart()}
                <span class="action-label">${liked ? '已点赞' : '点赞'}</span>
                ${
                  hasLikeNum
                    ? `<span class="action-num">${formatCount(likeNum)}</span>`
                    : ''
                }
              </button>

              <button class="action-btn${collected ? ' is-active' : ''}" type="button" data-action="collect"
                aria-pressed="${collected}" aria-label="${collected ? '取消收藏' : '收藏'}">
                ${icons.star()}
                <span class="action-label">${collected ? '已收藏' : '收藏'}</span>
                ${
                  hasCollectNum
                    ? `<span class="action-num">${formatCount(collectNum)}</span>`
                    : ''
                }
              </button>

              <button class="action-btn" type="button" data-action="share" aria-label="分享">
                ${icons.share()}<span class="action-label">分享</span>
              </button>
            </div>

            ${
              isOwner()
                ? `<div class="owner-bar" id="owner-bar">
                    <span class="owner-label">作者工具</span>
                    <button class="btn btn-outline btn-xs" type="button" data-owner="top">
                      ${icons.pin()} <span data-top-label>${topped ? '取消置顶' : '置顶'}</span>
                    </button>
                    <button class="btn btn-outline btn-xs" type="button" data-owner="visible">
                      ${visible ? icons.unlock() : icons.lock()}
                      <span>${visible ? '设为公开' : '设为仅自己可见'}</span>
                    </button>
                    <button class="btn btn-danger btn-xs" type="button" data-owner="delete">
                      ${icons.trash()} 删除
                    </button>
                  </div>`
                : ''
            }
          </div>
        </div>
      </div>`;

      bindEvents(visible);
    };

    const bindEvents = (visible) => {
      bindBack();

      // 图片切换
      root.querySelector('#media-thumbs')?.addEventListener('click', (event) => {
        const thumb = event.target.closest('[data-index]');
        if (!thumb) return;
        currentImage = Number(thumb.dataset.index) || 0;
        const mainImage = root.querySelector('#main-image');
        if (mainImage) mainImage.src = images[currentImage];
        root.querySelectorAll('.media-thumb').forEach((item) => {
          item.classList.toggle('is-active', item === thumb);
        });
      });

      const openLightbox = () => {
        if (images.length) showLightbox(images, currentImage);
      };
      root.querySelector('#media-zoom')?.addEventListener('click', openLightbox);
      root.querySelector('#main-image')?.addEventListener('click', openLightbox);

      // 点赞 / 收藏 / 分享
      root.querySelector('[data-action="like"]')?.addEventListener('click', (event) => {
        toggleLike(event.currentTarget);
      });
      root.querySelector('[data-action="collect"]')?.addEventListener('click', (event) => {
        toggleCollect(event.currentTarget);
      });
      root.querySelector('[data-action="share"]')?.addEventListener('click', async () => {
        const ok = await copyText(noteUrl(note.id));
        toast(ok ? '链接已复制，可以分享给朋友了' : '复制失败，请手动复制地址栏链接', ok ? 'success' : 'error');
      });

      // 关注作者
      root.querySelector(`[data-follow-user]`)?.addEventListener('click', (event) => {
        toggleFollow(event.currentTarget);
      });

      // 作者工具
      root.querySelector('[data-owner="top"]')?.addEventListener('click', toggleTop);
      root.querySelector('[data-owner="visible"]')?.addEventListener('click', () => {
        // 私密 → 公开；公开 → 私密
        if (Number(note.visible) === 1) makePublic();
        else makePrivate();
      });

      root.querySelector('[data-owner="delete"]')?.addEventListener('click', async () => {
        const ok = await confirmDialog({
          title: '删除笔记',
          message: '删除后将无法恢复，操作不可撤销。',
          confirmText: '确认删除',
          danger: true,
        });
        if (!ok) return;

        try {
          await api.deleteNote(note.id, { signal: ctx.signal });
          toast('笔记已删除', 'success');
          navigate('/explore', {}, { replace: true });
        } catch (err) {
          if (isAbortError(err)) return;
          toast(err.message || '删除失败', 'error');
        }
      });
    };

    const requireLogin = () => {
      if (auth.isLoggedIn) return true;
      toast('请先登录', 'warning');
      navigate('/login', { redirect: `/note/${noteId}` });
      return false;
    };

    const paintAction = (button, active, label, activeLabel) => {
      if (!button) return;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
      button.setAttribute('aria-label', active ? `取消${label}` : label);
      const labelEl = button.querySelector('.action-label');
      if (labelEl) labelEl.textContent = active ? activeLabel : label;
    };

    async function toggleLike(button) {
      if (!requireLogin()) return;
      button.disabled = true;
      const next = !liked;

      try {
        if (next) await api.like(noteId, { signal: ctx.signal });
        else await api.unlike(noteId, { signal: ctx.signal });

        liked = next;
        setInteraction(noteId, { liked });
        paintAction(button, liked, '点赞', '已点赞');

        // 乐观更新计数，随后延迟对账（计数由 MQ 异步累加）
        if (counts) {
          counts.likeTotal = Math.max(0, counts.likeTotal + (liked ? 1 : -1));
          paintActionCounts();
        }
        scheduleCountsRefresh();

        toast(liked ? '已点赞' : '已取消点赞', 'success');
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;

        if (err.code === 'NOTE-20010') {
          liked = true;
          setInteraction(noteId, { liked: true });
          paintAction(button, true, '点赞', '已点赞');
          toast('已经点赞过这篇笔记', 'info');
        } else if (err.code === 'NOTE-20011') {
          liked = false;
          setInteraction(noteId, { liked: false });
          paintAction(button, false, '点赞', '已点赞');
          toast('之前并未点赞这篇笔记', 'info');
        } else {
          toast(err.message || '点赞失败', 'error');
        }
      } finally {
        button.disabled = false;
      }
    }

    async function toggleCollect(button) {
      if (!requireLogin()) return;
      button.disabled = true;
      const next = !collected;

      try {
        if (next) await api.collect(noteId, { signal: ctx.signal });
        else await api.uncollect(noteId, { signal: ctx.signal });

        collected = next;
        setInteraction(noteId, { collected });
        paintAction(button, collected, '收藏', '已收藏');

        // 乐观更新计数，随后延迟对账
        if (counts) {
          counts.collectTotal = Math.max(0, counts.collectTotal + (collected ? 1 : -1));
          paintActionCounts();
        }
        scheduleCountsRefresh();

        toast(collected ? '已收藏' : '已取消收藏', 'success');
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;

        if (err.code === 'NOTE-20012') {
          collected = true;
          setInteraction(noteId, { collected: true });
          paintAction(button, true, '收藏', '已收藏');
          toast('已经收藏过这篇笔记', 'info');
        } else if (err.code === 'NOTE-20013') {
          collected = false;
          setInteraction(noteId, { collected: false });
          paintAction(button, false, '收藏', '已收藏');
          toast('之前并未收藏这篇笔记', 'info');
        } else {
          toast(err.message || '收藏失败', 'error');
        }
      } finally {
        button.disabled = false;
      }
    }

    async function toggleFollow(button) {
      if (!requireLogin()) return;
      const userId = button.dataset.followUser;
      if (!userId) return;

      button.disabled = true;
      const next = !followed;

      try {
        if (next) await api.follow(userId, { signal: ctx.signal });
        else await api.unfollow(userId, { signal: ctx.signal });

        followed = next;
        setFollowing(userId, followed);
        button.textContent = followed ? '已关注' : '关注';
        button.classList.toggle('btn-primary', !followed);
        button.classList.toggle('btn-outline', followed);
        toast(followed ? '关注成功' : '已取消关注', 'success');
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;

        if (err.code === 'RELATION-20004') {
          followed = true;
          setFollowing(userId, true);
          button.textContent = '已关注';
          button.classList.add('btn-outline');
          button.classList.remove('btn-primary');
          toast('已经关注过该作者', 'info');
        } else if (err.code === 'RELATION-20006') {
          followed = false;
          setFollowing(userId, false);
          button.textContent = '关注';
          button.classList.add('btn-primary');
          button.classList.remove('btn-outline');
          toast('之前并未关注该作者', 'info');
        } else {
          toast(err.message || '关注操作失败', 'error');
        }
      } finally {
        button.disabled = false;
      }
    }

    async function toggleTop() {
      const button = root.querySelector('[data-owner="top"]');
      if (!button) return;
      button.disabled = true;
      const next = !topped;

      try {
        await api.topNote(noteId, next, { signal: ctx.signal });
        topped = next;
        setTopState(noteId, topped);
        const label = button.querySelector('[data-top-label]');
        if (label) label.textContent = topped ? '取消置顶' : '置顶';
        button.classList.toggle('btn-soft', topped);
        toast(topped ? '已置顶笔记' : '已取消置顶', 'success');
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        toast(err.message || '置顶操作失败', 'error');
      } finally {
        button.disabled = false;
      }
    }

    // 统一刷新页面上的可见性展示（私密徽标 / 作者工具按钮 / 顶部徽标）
    function paintVisibleState(visible) {
      const metaEl = root.querySelector('#detail-meta');
      if (metaEl) {
        const exist = metaEl.querySelector('[data-private-badge]');
        if (visible && !exist) {
          const tag = document.createElement('span');
          tag.className = 'badge badge-amber';
          tag.setAttribute('data-private-badge', '');
          tag.innerHTML = `${icons.lock()} 仅自己可见`;
          metaEl.insertBefore(tag, metaEl.children[1] || null);
        } else if (!visible && exist) {
          exist.remove();
        }
      }

      const visibleBtn = root.querySelector('[data-owner="visible"]');
      if (visibleBtn) {
        visibleBtn.innerHTML = `${visible ? icons.unlock() : icons.lock()} <span>${
          visible ? '设为公开' : '设为仅自己可见'
        }</span>`;
      }

      const topBadge = root.querySelector('.detail-topbar .badge');
      if (topBadge) {
        topBadge.className = visible ? 'badge badge-amber' : 'badge badge-green';
        topBadge.innerHTML = visible
          ? `${icons.lock()} 仅自己可见`
          : `${icons.unlock()} 公开笔记`;
      }
    }

    async function makePrivate() {
      const ok = await confirmDialog({
        title: '设为仅自己可见',
        message: '设置后，这篇笔记只有你自己能看到。',
        extraHtml:
          '<p class="notice notice-brand" style="margin-top:12px">设为私密后，笔记会从发现页和搜索结果中移除；之后仍可随时在详情页点「设为公开」恢复。</p>',
        confirmText: '确认设置',
        danger: true,
      });
      if (!ok) return;

      try {
        await api.visibleOnlyMe(noteId, { signal: ctx.signal });
        note.visible = 1;
        toast('已设为仅自己可见', 'success');
        paintVisibleState(true);

        // 设为私密后自动刷新页面，保持与后端状态一致
        window.setTimeout(() => {
          if (!ctx.signal.aborted) window.location.reload();
        }, 600);
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        if (err.code === 'NOTE-20008') {
          toast('这篇笔记当前无法设置为仅自己可见', 'warning');
        } else {
          toast(err.message || '设置失败', 'error');
        }
      }
    }

    async function makePublic() {
      const ok = await confirmDialog({
        title: '设为公开',
        message: '设置后，这篇笔记所有人可见，会重新出现在发现页和搜索结果中。',
        confirmText: '确认公开',
      });
      if (!ok) return;

      try {
        await api.visiblePublic(noteId, { signal: ctx.signal });
        note.visible = 0;
        toast('已设为公开可见', 'success');
        paintVisibleState(false);

        // 恢复公开后自动刷新页面：重新拉取详情，并让发现页/搜索的索引状态同步
        window.setTimeout(() => {
          if (!ctx.signal.aborted) window.location.reload();
        }, 600);
      } catch (err) {
        if (isAbortError(err) || ctx.signal.aborted) return;
        if (err.code === 'NOTE-20014') {
          toast('这篇笔记当前无法设置为公开', 'warning');
        } else {
          toast(err.message || '设置失败', 'error');
        }
      }
    }


    await load();
  },
};
