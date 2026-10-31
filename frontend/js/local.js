/**
 * 本机状态：最近浏览、最近搜索、点赞/收藏、关注、置顶。
 *
 * 后端没有提供「点赞状态 / 收藏状态 / 关注状态」查询接口，也没有列表接口，
 * 因此这些状态在本地按用户维度记录，用于让按钮状态在刷新后仍然保留。
 * 这只是体验补偿，真实状态仍以服务端为准。
 */

import { auth } from './store.js';
import {
  noteCover,
  readJSON,
  readSessionJSON,
  writeJSON,
  writeSessionJSON,
} from './utils.js';

const PREFIX = 'lumenote:';
const RECENT_NOTES_KEY = `${PREFIX}recent-notes`;
const RECENT_SEARCH_KEY = `${PREFIX}recent-searches`;
const META_PREFIX = `${PREFIX}meta:`;

const scope = () => (auth.me?.id ? String(auth.me.id) : 'anon');
const scopedKey = (name) => `${PREFIX}${scope()}:${name}`;

/* ------------------------------- 点赞 / 收藏 ------------------------------ */

export function getInteraction(noteId) {
  const map = readJSON(scopedKey('interactions'), {});
  const item = map[String(noteId)] || {};
  return { liked: Boolean(item.liked), collected: Boolean(item.collected) };
}

export function setInteraction(noteId, patch) {
  const map = readJSON(scopedKey('interactions'), {});
  map[String(noteId)] = { ...getInteraction(noteId), ...patch };
  writeJSON(scopedKey('interactions'), map);
}

/* --------------------------------- 关注 ---------------------------------- */

export function isFollowing(userId) {
  const list = readJSON(scopedKey('following'), []);
  return Array.isArray(list) && list.map(String).includes(String(userId));
}

export function setFollowing(userId, value) {
  const list = new Set(
    (readJSON(scopedKey('following'), []) || []).map(String)
  );
  if (value) list.add(String(userId));
  else list.delete(String(userId));
  writeJSON(scopedKey('following'), [...list]);
}

/* --------------------------------- 置顶 ---------------------------------- */

export function getTopState(noteId) {
  const map = readJSON(scopedKey('topped'), {});
  return Boolean(map[String(noteId)]);
}

export function setTopState(noteId, value) {
  const map = readJSON(scopedKey('topped'), {});
  map[String(noteId)] = Boolean(value);
  writeJSON(scopedKey('topped'), map);
}

/* -------------------------------- 最近浏览 ------------------------------- */

export function pushRecentNote(note) {
  if (!note || note.noteId === undefined || note.noteId === null) return;
  const id = String(note.noteId);
  const item = {
    noteId: id,
    title: note.title || '',
    cover: noteCover(note),
    avatar: note.avatar || '',
    nickname: note.nickname || note.creatorName || '',
    type: note.type === 1 ? 1 : 0,
    videoUri: note.videoUri || '',
    updateTime: note.updateTime || '',
    visitedAt: Date.now(),
  };
  const list = readJSON(RECENT_NOTES_KEY, []).filter(
    (entry) => String(entry?.noteId) !== id
  );
  list.unshift(item);
  writeJSON(RECENT_NOTES_KEY, list.slice(0, 12));
}

export function getRecentNotes() {
  const list = readJSON(RECENT_NOTES_KEY, []);
  return Array.isArray(list) ? list.filter((entry) => entry && entry.noteId) : [];
}

export function clearRecentNotes() {
  writeJSON(RECENT_NOTES_KEY, []);
}

/* -------------------------------- 最近搜索 ------------------------------- */

export function pushRecentSearch(keyword) {
  const text = String(keyword || '').trim();
  if (!text) return;
  const list = readJSON(RECENT_SEARCH_KEY, []).filter((item) => item.keyword !== text);
  list.unshift({ keyword: text, at: Date.now() });
  writeJSON(RECENT_SEARCH_KEY, list.slice(0, 8));
}

export function getRecentSearches() {
  const list = readJSON(RECENT_SEARCH_KEY, []);
  return Array.isArray(list) ? list.filter((item) => item && item.keyword) : [];
}

export function clearRecentSearches() {
  writeJSON(RECENT_SEARCH_KEY, []);
}

/* ------------------------------ 搜索结果快照 ----------------------------- */

export function cacheNoteMeta(note) {
  if (!note) return;
  const id = note.noteId ?? note.id;
  if (id === undefined || id === null) return;
  writeSessionJSON(`${META_PREFIX}${id}`, {
    noteId: String(id),
    title: note.title || '',
    cover: noteCover(note),
    avatar: note.avatar || '',
    nickname: note.nickname || note.creatorName || '',
    type: note.type === 1 ? 1 : 0,
    videoUri: note.videoUri || '',
    updateTime: note.updateTime || '',
    likeTotal: note.likeTotal ?? null,
    commentTotal: note.commentTotal ?? null,
    collectTotal: note.collectTotal ?? null,
  });
}

export function getNoteMeta(noteId) {
  const meta = readSessionJSON(`${META_PREFIX}${noteId}`, null);
  return meta && String(meta.noteId) === String(noteId) ? meta : null;
}

export function patchNoteMeta(noteId, patch) {
  const meta = getNoteMeta(noteId);
  if (!meta) return;
  writeSessionJSON(`${META_PREFIX}${noteId}`, { ...meta, ...patch });
}

export function clearNoteMeta(noteId) {
  try {
    sessionStorage.removeItem(`${META_PREFIX}${noteId}`);
  } catch (err) {
    /* ignore */
  }
}
