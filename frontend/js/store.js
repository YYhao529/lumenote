/**
 * 登录态存储：token / 手机号 / 当前用户资料。
 * 只负责读写，不发起网络请求（避免循环依赖）。
 */

const TOKEN_KEY = 'lumenote:token';
const PHONE_KEY = 'lumenote:phone';
const ME_KEY = 'lumenote:me';

function readJSON(key) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    return null;
  }
}

function write(key, value) {
  try {
    if (value === null || value === undefined || value === '') localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch (err) {
    /* 隐私模式下忽略 */
  }
}

function readMe() {
  const me = readJSON(ME_KEY);
  if (!me || typeof me !== 'object') return null;
  return {
    id: me.id === null || me.id === undefined ? '' : String(me.id),
    nickname: me.nickname || '',
    avatar: me.avatar || '',
    introduction: me.introduction || '',
    backgroundImg: me.backgroundImg || '',
  };
}

const listeners = new Set();

export const auth = {
  token: (() => {
    try {
      return localStorage.getItem(TOKEN_KEY) || '';
    } catch (err) {
      return '';
    }
  })(),
  phone: (() => {
    try {
      return localStorage.getItem(PHONE_KEY) || '';
    } catch (err) {
      return '';
    }
  })(),
  me: readMe(),

  get isLoggedIn() {
    return Boolean(auth.token);
  },

  get userId() {
    return auth.me?.id || '';
  },

  setSession({ token, phone }) {
    if (token) {
      auth.token = String(token);
      write(TOKEN_KEY, auth.token);
    }
    if (phone) {
      auth.phone = String(phone);
      write(PHONE_KEY, auth.phone);
    }
    emit();
  },

  setPhone(phone) {
    auth.phone = phone ? String(phone) : '';
    write(PHONE_KEY, auth.phone);
    emit();
  },

  setMe(me) {
    auth.me = me
      ? {
          id: me.id === null || me.id === undefined ? '' : String(me.id),
          nickname: me.nickname || '',
          avatar: me.avatar || '',
          introduction: me.introduction || '',
          backgroundImg: me.backgroundImg || '',
        }
      : null;
    write(ME_KEY, auth.me ? JSON.stringify(auth.me) : '');
    emit();
  },

  updateMe(patch) {
    if (!auth.me) return;
    auth.setMe({ ...auth.me, ...patch });
  },

  clearSession() {
    auth.token = '';
    auth.phone = '';
    auth.me = null;
    write(TOKEN_KEY, '');
    write(PHONE_KEY, '');
    write(ME_KEY, '');
    emit();
  },
};

export function onAuthChange(handler) {
  listeners.add(handler);
  return () => listeners.delete(handler);
}

function emit() {
  listeners.forEach((handler) => {
    try {
      handler(auth);
    } catch (err) {
      console.error('[auth] listener error', err);
    }
  });
}
