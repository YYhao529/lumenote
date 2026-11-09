/**
 * 后端接口客户端。
 *
 * 说明：
 * 1. 所有接口统一返回 { success, message, errorCode, data }，业务失败时 HTTP 仍可能是 200，
 *    所以这里统一判断 success === false 并抛出 ApiError。
 * 2. 后端 Long 型 ID 是 19 位雪花 ID，超过 JS 安全整数范围。这里对 16 位以上的 ID 字段
 *    做“加引号”处理后再 JSON.parse，全程以字符串传递，避免精度丢失；Spring 的 Long
 *    反序列化可以接受数字字符串。
 * 3. 网关 Sa-Token 使用 Authorization: Bearer <token>。
 */

import { CONFIG, isFileProtocol } from './config.js';
import { auth } from './store.js';

export class ApiError extends Error {
  constructor(message, { code = '', status = 0, raw = null } = {}) {
    super(message || '请求失败');
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.raw = raw;
  }
}

const BIG_ID_FIELDS = [
  'id',
  'noteId',
  'creatorId',
  'userId',
  'followUserId',
  'unfollowUserId',
  'topicId',
];

const BIG_ID_RE = new RegExp(
  `"(${BIG_ID_FIELDS.join('|')})"\\s*:\\s*(-?\\d{16,})`,
  'g'
);

export function parseJsonSafely(text) {
  if (!text) return null;
  const patched = text.replace(BIG_ID_RE, '"$1":"$2"');
  try {
    return JSON.parse(patched);
  } catch (err) {
    return null;
  }
}

function buildUrl(path) {
  const base = CONFIG.apiBase ? CONFIG.apiBase.replace(/\/+$/, '') : '';
  return `${base}${path}`;
}

async function doFetch(
  path,
  {
    method = 'POST',
    data,
    formData,
    needAuth = false,
    signal,
    timeout = CONFIG.requestTimeout,
  } = {}
) {
  if (isFileProtocol()) {
    throw new ApiError(
      '当前以 file:// 方式打开页面，无法访问接口。请运行 `node server.js` 后通过 http://127.0.0.1:5180 访问。',
      { code: 'FILE_PROTOCOL' }
    );
  }

  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort(new DOMException('请求超时', 'TimeoutError'));
  }, timeout);

  const forwardAbort = () => controller.abort(signal?.reason);
  if (signal) {
    if (signal.aborted) forwardAbort();
    else signal.addEventListener('abort', forwardAbort, { once: true });
  }

  const headers = {};
  if (needAuth && auth.token) headers.Authorization = `Bearer ${auth.token}`;

  let body;
  if (formData) {
    body = formData;
  } else if (data !== undefined) {
    headers['Content-Type'] = 'application/json;charset=UTF-8';
    body = JSON.stringify(data);
  }

  try {
    const res = await fetch(buildUrl(path), {
      method,
      headers,
      body,
      signal: controller.signal,
      credentials: 'omit',
      cache: 'no-store',
    });
    const text = await res.text();
    return { res, text, json: parseJsonSafely(text) };
  } catch (err) {
    if (timedOut) throw new ApiError('请求超时，请检查后端服务是否已启动', { code: 'TIMEOUT' });
    if (err?.name === 'AbortError') throw err;
    throw new ApiError(
      '网络异常：无法连接到后端服务。请确认网关（8000）已启动，并通过 server.js 访问页面。',
      { code: 'NETWORK_ERROR' }
    );
  } finally {
    clearTimeout(timer);
    if (signal) signal.removeEventListener('abort', forwardAbort);
  }
}

async function request(path, options = {}) {
  const { res, text, json } = await doFetch(path, options);

  if (res.status === 401) {
    if (options.handle401 !== false) {
      window.dispatchEvent(
        new CustomEvent('lumenote:unauthorized', {
          detail: { message: json?.message || '登录状态已失效，请重新登录' },
        })
      );
    }
    throw new ApiError(json?.message || '登录状态已失效，请重新登录', {
      code: json?.errorCode || '401',
      status: 401,
      raw: text,
    });
  }

  if (!res.ok) {
    // 开发代理连接不上后端时会返回 PROXY_ERROR，归一化成更友好的提示
    if (json?.errorCode === 'PROXY_ERROR') {
      console.warn('[api] 代理连接失败：', json.message);
      throw new ApiError('后端服务暂时不可用，请稍后重试', {
        code: 'NETWORK_ERROR',
        status: res.status,
        raw: json,
      });
    }

    throw new ApiError(json?.message || `请求失败（HTTP ${res.status}）`, {
      code: json?.errorCode || String(res.status),
      status: res.status,
      raw: text,
    });
  }

  if (!json) {
    throw new ApiError('后端返回了非 JSON 响应，请检查代理与微服务状态', {
      code: 'BAD_RESPONSE',
      status: res.status,
      raw: text,
    });
  }

  if (json.success === false) {
    throw new ApiError(json.message || '请求失败', {
      code: json.errorCode || '',
      status: res.status,
      raw: json,
    });
  }

  return json;
}

const data = (promise) => promise.then((json) => json?.data);

export const api = {
  /* ================================ 认证服务 =============================== */

  sendCode: (phone, options = {}) =>
    request('/auth/verification/code/send', { ...options, data: { phone } }),

  login: (payload, options = {}) =>
    data(request('/auth/login', { ...options, data: payload, timeout: 30000 })),

  logout: (options = {}) =>
    request('/auth/logout', { ...options, needAuth: true, timeout: 8000 }),

  updatePassword: (oldPassword, newPassword, options = {}) =>
    request('/auth/password/update', {
      ...options,
      data: { oldPassword, newPassword },
      needAuth: true,
    }),

  /* ================================ 用户服务 =============================== */

  findByPhone: (phone, options = {}) =>
    data(
      request('/user/findByPhone', {
        ...options,
        data: { phone },
        needAuth: true,
      })
    ),

  findById: (id, options = {}) =>
    data(
      request('/user/findById', {
        ...options,
        data: { id: String(id) },
        needAuth: true,
      })
    ),

  findByIds: (ids = [], options = {}) =>
    data(
      request('/user/findByIds', {
        ...options,
        data: { ids: ids.map(String) },
        needAuth: true,
      })
    ),

  updateUserInfo: (formData, options = {}) =>
    request('/user/update', {
      ...options,
      formData,
      needAuth: true,
      timeout: CONFIG.uploadTimeout,
    }),

  /* ================================ 笔记服务 =============================== */

  publishNote: (payload, options = {}) =>
    request('/note/publish', { ...options, data: payload, needAuth: true }),

  noteDetail: (id, options = {}) =>
    data(
      request('/note/detail', {
        ...options,
        data: { id: String(id) },
        needAuth: true,
      })
    ),

  updateNote: (payload, options = {}) =>
    request('/note/update', { ...options, data: payload, needAuth: true }),

  deleteNote: (noteId, options = {}) =>
    request('/note/delete', {
      ...options,
      data: { noteId: String(noteId) },
      needAuth: true,
    }),

  visibleOnlyMe: (id, options = {}) =>
    request('/note/visible/onlyme', {
      ...options,
      data: { id: String(id) },
      needAuth: true,
    }),

  visiblePublic: (id, options = {}) =>
    request('/note/visible/public', {
      ...options,
      data: { id: String(id) },
      needAuth: true,
    }),

  topNote: (id, isTop, options = {}) =>
    request('/note/top', {
      ...options,
      data: { id: String(id), isTop: Boolean(isTop) },
      needAuth: true,
    }),

  like: (id, options = {}) =>
    request('/note/like', { ...options, data: { id: String(id) }, needAuth: true }),

  unlike: (id, options = {}) =>
    request('/note/unlike', { ...options, data: { id: String(id) }, needAuth: true }),

  collect: (id, options = {}) =>
    request('/note/collect', { ...options, data: { id: String(id) }, needAuth: true }),

  uncollect: (id, options = {}) =>
    request('/note/uncollect', { ...options, data: { id: String(id) }, needAuth: true }),

  userPublishedNotes: (userId, pageNo = 1, options = {}) =>
    request('/note/list/publish', {
      ...options,
      data: { userId: String(userId), pageNo: Number(pageNo) || 1 },
      needAuth: true,
    }),

  userCollectedNotes: (userId, pageNo = 1, options = {}) =>
    request('/note/list/collect', {
      ...options,
      data: { userId: String(userId), pageNo: Number(pageNo) || 1 },
      needAuth: true,
    }),

  userLikedNotes: (userId, pageNo = 1, options = {}) =>
    request('/note/list/like', {
      ...options,
      data: { userId: String(userId), pageNo: Number(pageNo) || 1 },
      needAuth: true,
    }),

  /** 批量查询当前用户是否点赞：返回已点赞的笔记 ID 列表（字符串） */
  noteLikedStatus: (noteIds = [], options = {}) =>
    data(
      request('/note/liked/status', {
        ...options,
        data: { noteIds: noteIds.map(String) },
        needAuth: true,
      })
    ),

  /* ================================ 计数服务 =============================== */

  noteCount: (noteId, options = {}) =>
    data(
      request('/count/note', {
        ...options,
        data: { noteId: String(noteId) },
        needAuth: true,
      })
    ),

  userCount: (userId, options = {}) =>
    data(
      request('/count/user', {
        ...options,
        data: { userId: String(userId) },
        needAuth: true,
      })
    ),

  /* ============================== 用户关系服务 ============================= */

  follow: (followUserId, options = {}) =>
    request('/relation/follow', {
      ...options,
      data: { followUserId: String(followUserId) },
      needAuth: true,
    }),

  unfollow: (unfollowUserId, options = {}) =>
    request('/relation/unfollow', {
      ...options,
      data: { unfollowUserId: String(unfollowUserId) },
      needAuth: true,
    }),

  followingList: (userId, pageNo = 1, options = {}) =>
    request('/relation/following/list', {
      ...options,
      data: { userId: String(userId), pageNo: Number(pageNo) || 1 },
      needAuth: true,
    }),

  fansList: (userId, pageNo = 1, options = {}) =>
    request('/relation/fans/list', {
      ...options,
      data: { userId: String(userId), pageNo: Number(pageNo) || 1 },
      needAuth: true,
    }),

  /* ================================ 搜索服务 =============================== */

  hotNotes: (pageNo = 1, options = {}) =>
    request('/search/note/hot', {
      ...options,
      data: { pageNo: Number(pageNo) || 1 },
      needAuth: false,
    }),

  searchNotes: (params = {}, options = {}) =>
    request('/search/note', {
      ...options,
      data: {
        keyword: params.keyword,
        pageNo: Number(params.pageNo) || 1,
        ...(params.type !== undefined && params.type !== null && params.type !== ''
          ? { type: Number(params.type) }
          : {}),
        ...(params.sort !== undefined && params.sort !== null && params.sort !== ''
          ? { sort: Number(params.sort) }
          : {}),
        ...(params.publishTimeRange !== undefined &&
        params.publishTimeRange !== null &&
        params.publishTimeRange !== ''
          ? { publishTimeRange: Number(params.publishTimeRange) }
          : {}),
      },
      needAuth: false,
    }),

  searchUsers: (params = {}, options = {}) =>
    request('/search/user', {
      ...options,
      data: {
        keyword: params.keyword,
        pageNo: Number(params.pageNo) || 1,
      },
      needAuth: false,
    }),

  /* ================================ OSS 服务 =============================== */

  uploadFile: (file, options = {}) => {
    const formData = new FormData();
    formData.append('file', file, file.name || 'upload');
    return data(
      request('/file/upload', {
        ...options,
        formData,
        needAuth: true,
        timeout: CONFIG.uploadTimeout,
      })
    );
  },
};

/**
 * 原样请求，供「接口联调」页面使用（不抛业务异常，返回状态码 / 耗时 / 原始文本）。
 */
export async function rawRequest(path, options = {}) {
  const startedAt = performance.now();
  try {
    const { res, text, json } = await doFetch(path, options);
    return {
      ok: res.ok && json?.success !== false,
      status: res.status,
      statusText: res.statusText || '',
      json,
      text,
      timeMs: Math.round(performance.now() - startedAt),
    };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      statusText: err?.code || 'NETWORK_ERROR',
      json: null,
      text: err?.message || String(err),
      timeMs: Math.round(performance.now() - startedAt),
      error: err,
    };
  }
}
