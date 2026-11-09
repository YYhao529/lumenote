/**
 * 会话动作：登录、退出、拉取当前用户资料。
 *
 * 后端限制：/auth/login 只返回 token，没有 /user/me 接口，因此这里用登录手机号
 * 反查用户：/user/findByPhone -> /user/findById。手机号在登录成功后保存在本地。
 */

import { api } from './api.js';
import { auth } from './store.js';

export async function resolveMe(options = {}) {
  if (!auth.token) return null;
  if (!auth.phone) return auth.me;

  const found = await api.findByPhone(auth.phone, options);
  if (!found || found.id === undefined || found.id === null || found.id === '') {
    return null;
  }

  const profile = await api.findById(found.id, options);
  const me = {
    id: String(found.id),
    nickname: profile?.nickname || '拾光笔记用户',
    // 拾光笔记号（个人主页展示；老接口没有这个字段时兜底为空）
    lumenoteId: profile?.lumenoteId || '',
    avatar: profile?.avatar || '',
    introduction: profile?.introduction || '',
    backgroundImg: profile?.backgroundImg || '',
  };
  auth.setMe(me);
  return me;
}

export async function login(payload, options = {}) {
  const token = await api.login(payload, options);
  if (!token) throw new Error('接口未返回 token，请检查认证服务日志');

  auth.setSession({ token: String(token), phone: payload.phone });

  try {
    await resolveMe(options);
  } catch (err) {
    // 登录本身已成功，资料拉取失败不阻断流程（例如用户服务暂时不可用）
    console.warn('[session] 获取当前用户资料失败：', err);
  }

  return token;
}

export async function loginWithCode(phone, code, options = {}) {
  return login({ phone, code, type: 1 }, options);
}

export async function loginWithPassword(phone, password, options = {}) {
  return login({ phone, password, type: 2 }, options);
}

export async function logout() {
  try {
    await api.logout({ timeout: 6000 });
  } catch (err) {
    // token 可能已过期，忽略服务端错误，本地必须清理
  }
  auth.clearSession();
}
