/** 登录页：验证码登录 / 密码登录 */

import { auth } from '../store.js';
import { loginWithCode, loginWithPassword } from '../session.js';
import { api } from '../api.js';
import { icons } from '../icons.js';
import { navigate } from '../router.js';
import { setLoading, toast } from '../ui.js';
import { escapeHtml } from '../utils.js';

const PHONE_RE = /^\d{11}$/;

export default {
  async mount(root, ctx) {
    const redirectRaw = ctx.query.redirect || '';
    const redirect =
      redirectRaw.startsWith('/') && !redirectRaw.startsWith('//') ? redirectRaw : '/me';

    if (auth.isLoggedIn) {
      navigate(redirect, {}, { replace: true });
      return;
    }

    root.innerHTML = `<div class="auth-page">
      <div class="auth-card">
        <aside class="auth-aside">
          <a class="brand" href="#/explore" aria-label="返回发现页">
            <span class="brand-mark" aria-hidden="true">${icons.brandMark()}</span>
            <span class="brand-name">拾光笔记</span>
          </a>
          <div class="auth-aside-body">
            <h1>记录生活，<br>也<em>发现同好</em>。</h1>
            <p class="auth-aside-sub">分享你的兴趣瞬间</p>
          </div>
          <div class="auth-aside-foot">© 2026 拾光笔记</div>
        </aside>

        <div class="auth-form">
          <div class="auth-form-head">
            <h2>欢迎来到拾光笔记</h2>
            <p>${auth.phone ? `上次登录手机号：${escapeHtml(auth.phone)}` : '使用手机号登录或注册'}</p>
          </div>

          <div class="auth-tabs" role="tablist" aria-label="登录方式">
            <button class="auth-tab is-active" type="button" data-mode="code" role="tab" aria-selected="true">验证码登录</button>
            <button class="auth-tab" type="button" data-mode="password" role="tab" aria-selected="false">密码登录</button>
          </div>

          <form id="login-form" novalidate>
            <div class="stack">
              <div class="field">
                <label class="field-label" for="login-phone">手机号</label>
                <input class="input" id="login-phone" name="phone" type="tel" inputmode="numeric"
                  maxlength="11" autocomplete="tel" placeholder="请输入 11 位手机号"
                  value="${escapeHtml(auth.phone || '')}">
              </div>

              <div class="field" data-panel="code">
                <label class="field-label" for="login-code">
                  验证码
                  <span class="optional">6 位数字</span>
                </label>
                <div class="code-row">
                  <input class="input" id="login-code" name="code" type="text" inputmode="numeric"
                    maxlength="6" autocomplete="one-time-code" placeholder="请输入验证码">
                  <button class="btn btn-outline" id="send-code" type="button">获取验证码</button>
                </div>
                <p class="field-hint">验证码 3 分钟内有效。</p>
              </div>

              <div class="field" data-panel="password" hidden>
                <label class="field-label" for="login-password">密码</label>
                <div class="input-wrap">
                  <input class="input" id="login-password" name="password" type="password"
                    autocomplete="current-password" placeholder="请输入登录密码">
                  <button class="input-action" id="toggle-password" type="button" aria-label="显示密码">${icons.eye()}</button>
                </div>
              </div>

              <p class="auth-notice" id="login-error" hidden></p>

              <button class="btn btn-primary btn-block auth-submit" id="login-submit" type="submit">登录 / 注册</button>
            </div>
          </form>

          <div class="auth-foot">
            <a href="#/explore">先逛逛</a>
          </div>
        </div>
      </div>
    </div>`;

    const form = root.querySelector('#login-form');
    const phoneInput = root.querySelector('#login-phone');
    const codeInput = root.querySelector('#login-code');
    const passwordInput = root.querySelector('#login-password');
    const sendBtn = root.querySelector('#send-code');
    const submitBtn = root.querySelector('#login-submit');
    const errorBox = root.querySelector('#login-error');
    const codePanel = root.querySelector('[data-panel="code"]');
    const passwordPanel = root.querySelector('[data-panel="password"]');
    const tabs = [...root.querySelectorAll('.auth-tab')];

    let mode = 'code';
    let countdown = 0;
    let timer = null;

    const showError = (message) => {
      if (!errorBox) return;
      errorBox.textContent = message;
      errorBox.hidden = false;
    };

    const clearError = () => {
      if (!errorBox) return;
      errorBox.hidden = true;
      errorBox.textContent = '';
    };

    const stopCountdown = () => {
      if (timer) clearInterval(timer);
      timer = null;
    };

    const startCountdown = (seconds = 60) => {
      countdown = seconds;
      stopCountdown();
      sendBtn.disabled = true;
      sendBtn.textContent = `${countdown}s 后重发`;

      timer = setInterval(() => {
        countdown -= 1;
        if (countdown <= 0) {
          stopCountdown();
          countdown = 0;
          sendBtn.disabled = false;
          sendBtn.textContent = '获取验证码';
        } else {
          sendBtn.textContent = `${countdown}s 后重发`;
        }
      }, 1000);

      ctx.onCleanup(stopCountdown);
    };

    const setMode = (next) => {
      mode = next;
      tabs.forEach((tab) => {
        const active = tab.dataset.mode === next;
        tab.classList.toggle('is-active', active);
        tab.setAttribute('aria-selected', active ? 'true' : 'false');
      });
      codePanel.hidden = next !== 'code';
      passwordPanel.hidden = next !== 'password';
      submitBtn.textContent = next === 'code' ? '登录 / 注册' : '登录';
      clearError();
      (next === 'code' ? codeInput : passwordInput)?.focus();
    };

    tabs.forEach((tab) => {
      tab.addEventListener('click', () => setMode(tab.dataset.mode));
    });

    root.querySelector('#toggle-password')?.addEventListener('click', (event) => {
      const button = event.currentTarget;
      const isPassword = passwordInput.type === 'password';
      passwordInput.type = isPassword ? 'text' : 'password';
      button.innerHTML = isPassword ? icons.eyeOff() : icons.eye();
      button.setAttribute('aria-label', isPassword ? '隐藏密码' : '显示密码');
    });

    phoneInput?.addEventListener('input', () => {
      phoneInput.value = phoneInput.value.replace(/\D/g, '').slice(0, 11);
      clearError();
    });
    codeInput?.addEventListener('input', () => {
      codeInput.value = codeInput.value.replace(/\D/g, '').slice(0, 6);
      clearError();
    });

    const validatePhone = () => {
      const phone = phoneInput.value.trim();
      if (!PHONE_RE.test(phone)) {
        showError('请输入 11 位手机号');
        phoneInput.focus();
        return '';
      }
      return phone;
    };

    sendBtn?.addEventListener('click', async () => {
      const phone = validatePhone();
      if (!phone) return;

      setLoading(sendBtn, true);
      try {
        await api.sendCode(phone, { signal: ctx.signal });
        toast('验证码已发送，请查收短信', 'success');
        startCountdown(60);
      } catch (err) {
        if (err.name === 'AbortError') return;
        const message = err.status === 401 ? '发送验证码失败' : err.message || '发送失败';
        showError(message);
        toast(message, 'error');
      } finally {
        setLoading(sendBtn, false);
        if (countdown > 0) {
          sendBtn.disabled = true;
          sendBtn.textContent = `${countdown}s 后重发`;
        }
      }
    });

    form?.addEventListener('submit', async (event) => {
      event.preventDefault();
      clearError();

      const phone = validatePhone();
      if (!phone) return;

      if (mode === 'code') {
        const code = codeInput.value.trim();
        if (code.length !== 6) {
          showError('请输入 6 位验证码');
          codeInput.focus();
          return;
        }

        setLoading(submitBtn, true);
        try {
          await loginWithCode(phone, code, { signal: ctx.signal });
          toast('登录成功，欢迎回来', 'success');
          navigate(redirect, {}, { replace: true });
        } catch (err) {
          if (err.name === 'AbortError') return;
          const message = err.status === 401 ? '验证码错误或已过期' : err.message || '登录失败';
          showError(message);
          toast(message, 'error');
        } finally {
          setLoading(submitBtn, false);
        }
        return;
      }

      const password = passwordInput.value;
      if (!password) {
        showError('请输入密码');
        passwordInput.focus();
        return;
      }

      setLoading(submitBtn, true);
      try {
        await loginWithPassword(phone, password, { signal: ctx.signal });
        toast('登录成功，欢迎回来', 'success');
        navigate(redirect, {}, { replace: true });
      } catch (err) {
        if (err.name === 'AbortError') return;
        const message = err.status === 401 ? '手机号或密码错误' : err.message || '登录失败';
        showError(message);
        toast(message, 'error');
      } finally {
        setLoading(submitBtn, false);
      }
    });

    (auth.phone ? codeInput : phoneInput)?.focus();
  },
};
