/**
 * 全局配置
 *
 * 默认走同源相对路径，由 server.js 开发代理统一转发到网关 8000：
 *   /auth /user /note /relation /search /count /file -> 网关 8000
 *   /minio                                           -> 对象存储 9000
 *
 * 也可以在「接口联调」页面里临时填写直连地址（需要后端开启 CORS）。
 */

const API_BASE_KEY = 'lumenote:apiBase';

function readApiBase() {
  try {
    return localStorage.getItem(API_BASE_KEY) || '';
  } catch (err) {
    return '';
  }
}

export const CONFIG = {
  pageSize: 10,
  requestTimeout: 20000,
  uploadTimeout: 120000,
  apiBase: readApiBase(),
  storagePrefix: 'lumenote:',
};

export function setApiBase(value) {
  const next = String(value || '').trim().replace(/\/+$/, '');
  CONFIG.apiBase = next;
  try {
    if (next) localStorage.setItem(API_BASE_KEY, next);
    else localStorage.removeItem(API_BASE_KEY);
  } catch (err) {
    /* localStorage 不可用时仅内存生效 */
  }
}

export function isFileProtocol() {
  return window.location.protocol === 'file:';
}
