/** 接口联调：服务健康检查、接口清单、在线调试、已知接口缺口 */

import { rawRequest } from '../api.js';
import { CONFIG, setApiBase } from '../config.js';
import { icons } from '../icons.js';
import { toast } from '../ui.js';
import { escapeHtml } from '../utils.js';

const GROUPS = [
  {
    id: 'auth',
    name: '认证服务',
    port: '网关 8000 → lumenote-auth 8080',
    items: [
      {
        path: '/auth/verification/code/send',
        auth: false,
        desc: '发送短信验证码',
        req: { phone: '13800138000' },
        res: '{ "success": true, "data": null }',
        note: '验证码有效期 3 分钟；同一手机号在有效期内不能重复发送。开发环境可在 auth 服务日志中看到验证码。',
      },
      {
        path: '/auth/login',
        auth: false,
        desc: '登录 / 注册（验证码或密码）',
        req: { phone: '13800138000', code: '123456', type: 1 },
        res: '{ "success": true, "data": "<token>" }',
        note: 'type=1 为验证码登录（未注册手机号自动注册），此时传 code；type=2 为密码登录，此时传 password。',
      },
      {
        path: '/auth/logout',
        auth: true,
        desc: '退出登录',
        req: null,
        res: '{ "success": true }',
        note: '需要请求头 Authorization: Bearer <token>。',
      },
      {
        path: '/auth/password/update',
        auth: true,
        desc: '修改密码',
        req: { oldPassword: 'old-password', newPassword: 'new-password-123' },
        res: '{ "success": true }',
        note: '现有后端只传新密码，不校验原密码，请谨慎使用。',
      },
    ],
  },
  {
    id: 'user',
    name: '用户服务',
    port: '网关 8000 → lumenote-user 8082',
    items: [
      {
        path: '/user/update',
        auth: true,
        multipart: true,
        desc: '更新用户资料（multipart）',
        req: 'multipart/form-data 字段：\navatar（文件，可选）\nbackgroundImg（文件，可选）\nnickname（2-24 字符）\nlumenoteId（6-15 位字母/数字/下划线）\nsex（0 女 / 1 男）\nbirthday（yyyy-MM-dd）\nintroduction（1-100 字符）',
        res: '{ "success": true }',
        note: '后端直接按字段名绑定 UpdateUserInfoReqVO；留空的字段不会更新。',
      },
      {
        path: '/user/register',
        auth: true,
        desc: '注册用户（认证服务内部调用）',
        req: { phone: '13800138000' },
        res: '{ "success": true, "data": "用户 ID" }',
        note: '手机号已存在时直接返回已有用户 ID。前端不直接调用。',
      },
      {
        path: '/user/findByPhone',
        auth: true,
        desc: '根据手机号查询用户（只返回用户 ID）',
        req: { phone: '13800138000' },
        res: '{ "success": true, "data": { "id": "用户 ID" } }',
        note: '不再返回密码密文；前端用它把登录手机号换成用户 ID。',
      },
      {
        path: '/user/findById',
        auth: true,
        desc: '根据用户 ID 查询用户资料',
        req: { id: '用户 ID' },
        res: '{ "success": true, "data": { "id": "…", "nickname": "拾光笔记10000", "avatar": "https://…", "introduction": "…" } }',
        note: '只返回昵称、头像、简介，不含拾光笔记号、性别、生日与笔记数。',
      },
      {
        path: '/user/findByIds',
        auth: true,
        desc: '批量查询用户资料（1-10 个）',
        req: { ids: ['用户 ID 1', '用户 ID 2'] },
        res: '{ "success": true, "data": [ { "id": "…", "nickname": "…" } ] }',
        note: '集合大小限制 1-10。',
      },
      {
        path: '/user/password/update',
        auth: true,
        desc: '修改密码（需原密码，认证服务内部调用）',
        req: { oldPassword: 'old-password', newPassword: 'new-password-123' },
        res: '{ "success": true }',
        note: '用户服务内部完成原密码校验与 BCrypt 加密；前端请调用 /auth/password/update。',
      },
    ],
  },
  {
    id: 'note',
    name: '笔记服务',
    port: '网关 8000 → lumenote-note 8086',
    items: [
      {
        path: '/note/publish',
        auth: true,
        desc: '发布笔记',
        req: {
          type: 0,
          imgUris: ['https://example.com/1.jpg'],
          videoUri: null,
          title: '今天的咖啡',
          content: '手冲咖啡记录……',
          topicName: '美食',
        },
        res: '{ "success": true, "data": null }',
        note: 'type：0 图文（1-8 张图片）/ 1 视频（必须有 videoUri）。话题填 topicName（不存在会自动创建），也兼容 topicId。发布成功不返回笔记 ID。',
      },
      {
        path: '/note/detail',
        auth: true,
        desc: '查询笔记详情',
        req: { id: '笔记 ID' },
        res: '{ "success": true, "data": { "id": "…", "type": 0, "title": "…", "content": "…", "imgUris": [], "topicName": "…", "creatorId": "…", "creatorName": "…", "avatar": "…", "updateTime": "2024-05-01 12:30:00", "visible": 0 } }',
        note: '私密笔记只有作者本人可以查看；详情不返回点赞/收藏/评论数与置顶状态。',
      },
      {
        path: '/note/update',
        auth: true,
        desc: '修改笔记',
        req: {
          id: '笔记 ID',
          type: 0,
          imgUris: ['https://example.com/1.jpg'],
          title: '新的标题',
          content: '新的正文',
          topicName: '美食',
        },
        res: '{ "success": true }',
        note: '只有笔记作者本人可以修改；type 必填；话题同发布（topicName，不存在自动创建）。',
      },
      {
        path: '/note/delete',
        auth: true,
        desc: '删除笔记',
        req: { noteId: '笔记 ID' },
        res: '{ "success": true }',
        note: '注意字段名是 noteId，不是 id。',
      },
      {
        path: '/note/visible/onlyme',
        auth: true,
        desc: '设为仅自己可见',
        req: { id: '笔记 ID' },
        res: '{ "success": true }',
        note: '私密笔记会从发现页和搜索结果中移除，可用 /note/visible/public 恢复公开。',
      },
      {
        path: '/note/visible/public',
        auth: true,
        desc: '设为公开可见',
        req: { id: '笔记 ID' },
        res: '{ "success": true }',
        note: '仅作者本人可操作，恢复公开后由 Canal 重新同步到 ES 索引。',
      },
      {
        path: '/note/top',
        auth: true,
        desc: '置顶 / 取消置顶',
        req: { id: '笔记 ID', isTop: true },
        res: '{ "success": true }',
        note: 'isTop 为布尔值；详情接口不返回当前置顶状态。',
      },
      {
        path: '/note/like',
        auth: true,
        desc: '点赞笔记',
        req: { id: '笔记 ID' },
        res: '{ "success": true }',
        note: '重复点赞返回 NOTE-20010；收藏/点赞状态没有查询接口。',
      },
      {
        path: '/note/unlike',
        auth: true,
        desc: '取消点赞',
        req: { id: '笔记 ID' },
        res: '{ "success": true }',
        note: '未点赞时取消返回 NOTE-20011。',
      },
      {
        path: '/note/collect',
        auth: true,
        desc: '收藏笔记',
        req: { id: '笔记 ID' },
        res: '{ "success": true }',
        note: '重复收藏返回 NOTE-20012。',
      },
      {
        path: '/note/uncollect',
        auth: true,
        desc: '取消收藏',
        req: { id: '笔记 ID' },
        res: '{ "success": true }',
        note: '未收藏时取消返回 NOTE-20013。',
      },
    ],
  },
  {
    id: 'relation',
    name: '用户关系服务',
    port: '网关 8000 → lumenote-user-relation 8087',
    items: [
      {
        path: '/relation/follow',
        auth: true,
        desc: '关注用户',
        req: { followUserId: '被关注用户 ID' },
        res: '{ "success": true }',
        note: '不能关注自己；重复关注返回 RELATION-20004；关注数有上限。',
      },
      {
        path: '/relation/unfollow',
        auth: true,
        desc: '取关用户',
        req: { unfollowUserId: '被取关用户 ID' },
        res: '{ "success": true }',
        note: '未关注时取关返回 RELATION-20006。',
      },
      {
        path: '/relation/following/list',
        auth: true,
        desc: '查询关注列表（分页）',
        req: { userId: '用户 ID', pageNo: 1 },
        res: '{ "success": true, "data": [ { "userId": "…", "avatar": "…", "nickname": "…", "introduction": "…" } ], "pageNo": 1, "totalCount": 0, "pageSize": 10, "totalPage": 0 }',
        note: '每页固定 10 条。',
      },
      {
        path: '/relation/fans/list',
        auth: true,
        desc: '查询粉丝列表（分页）',
        req: { userId: '用户 ID', pageNo: 1 },
        res: '{ "success": true, "data": [ { "userId": "…", "avatar": "…", "nickname": "…", "fansTotal": 0, "noteTotal": 0 } ], "pageNo": 1, "totalCount": 0, "pageSize": 10, "totalPage": 0 }',
        note: '粉丝列表项会额外带粉丝总数与笔记总数（来自计数服务）。',
      },
    ],
  },
  {
    id: 'search',
    name: '搜索服务',
    port: '网关 /search（内部 8092）',
    items: [
      {
        path: '/search/note',
        auth: false,
        desc: '搜索笔记（Elasticsearch）',
        req: { keyword: '咖啡', pageNo: 1, type: null, sort: null, publishTimeRange: null },
        res: '{ "success": true, "data": [ { "noteId": "…", "cover": "…", "title": "…", "highlightTitle": "<strong>咖啡</strong>…", "avatar": "…", "nickname": "…", "updateTime": "2024-05-01 12:30:00", "likeTotal": "1.2万", "commentTotal": "0", "collectTotal": "23" } ], "pageNo": 1, "totalCount": 1, "pageSize": 10, "totalPage": 1 }',
        note: 'type：0 图文 / 1 视频；sort：0 最新 / 1 最多点赞 / 2 最多评论 / 3 最多收藏；publishTimeRange：0 一天内 / 1 一周内 / 2 半年内。全部为空表示综合排序。',
      },
      {
        path: '/search/user',
        auth: false,
        desc: '搜索用户（Elasticsearch）',
        req: { keyword: '拾光笔记', pageNo: 1 },
        res: '{ "success": true, "data": [ { "userId": "…", "nickname": "…", "highlightNickname": "<strong>拾光笔记</strong>…", "avatar": "…", "lumenoteId": "…", "noteTotal": 0, "fansTotal": "0" } ], "pageNo": 1, "totalCount": 1, "pageSize": 10, "totalPage": 1 }',
        note: '搜索与计数接口已接入网关，游客可访问；前端统一走同源代理。',
      },
    ],
  },
  {
    id: 'file',
    name: '文件存储服务',
    port: '网关 /file（内部 8081）',
    items: [
      {
        path: '/file/upload',
        auth: true,
        multipart: true,
        desc: '上传文件到阿里云 OSS',
        req: 'multipart/form-data，字段名：file',
        res: '{ "success": true, "data": "https://deepsea-lumenote.oss-cn-hangzhou.aliyuncs.com/xxx.jpg" }',
        note: '返回可直接访问的 URL；笔记图片、头像、背景图、视频都复用此接口。',
      },
    ],
  },
  {
    id: 'internal',
    name: '内部服务（网关未暴露）',
    port: 'KV 8084 · 分布式 ID 8085',
    items: [
      {
        path: '/kv/note/content/add',
        auth: true,
        internal: true,
        desc: '新增笔记正文（KV）',
        req: { uuid: '笔记内容 UUID', content: '正文' },
        res: '{ "success": true }',
        note: '由笔记服务通过 Feign 调用，前端不应直接使用。',
      },
      {
        path: '/kv/note/content/find',
        auth: true,
        internal: true,
        desc: '查询笔记正文（KV）',
        req: { uuid: '笔记内容 UUID' },
        res: '{ "success": true, "data": { "content": "正文" } }',
        note: '由笔记服务内部调用。',
      },
      {
        path: '/kv/note/content/delete',
        auth: true,
        internal: true,
        desc: '删除笔记正文（KV）',
        req: { uuid: '笔记内容 UUID' },
        res: '{ "success": true }',
        note: '由笔记服务内部调用。',
      },
      {
        path: '/id/snowflake/get/{key}',
        auth: true,
        internal: true,
        method: 'GET',
        desc: '雪花 ID（GET）',
        req: 'GET /id/snowflake/get/note',
        res: '"1812345678901234567"',
        note: '分布式 ID 生成服务，返回 19 位雪花 ID；它也是 JS 精度问题的来源。',
      },
      {
        path: '/id/segment/get/{key}',
        auth: true,
        internal: true,
        method: 'GET',
        desc: '号段 ID（GET）',
        req: 'GET /id/segment/get/lumenote_id',
        res: '"10001"',
        note: '号段模式，返回递增 ID。',
      },
    ],
  },
];

const GAPS = [
  {
    title: '没有 /user/me',
    body:
      '登录只返回 token。前端用登录手机号调用 /user/findByPhone 换 userId，再调用 /user/findById 拿昵称头像。建议增加 /user/me，或让登录接口返回 { token, userId }。',
  },
  {
    title: '没有评论接口',
    body:
      '数据模型与搜索索引里有 commentTotal，但没有评论增删查接口，详情页评论区目前是占位说明。',
  },
  {
    title: '没有关注流 / 推荐流',
    body:
      '发现页目前使用搜索服务的热门笔记接口；没有「关注的人发布」信息流，也没有频道/话题推荐流。',
  },
  {
    title: '发布接口不返回笔记 ID',
    body:
      '发布成功后无法直接跳转详情，只能提示用户到搜索页按标题查找；建议 /note/publish 返回新笔记 ID。',
  },
  {
    title: '详情不返回计数与置顶状态',
    body:
      'FindNoteDetailRspVO 不含 likeTotal / collectTotal / commentTotal / isTop。前端已改为单独调用 /count/note 取真实计数，置顶状态仍由本地记录。',
  },
  {
    title: 'Long 型 ID 未全局转字符串',
    body:
      '雪花 ID 是 19 位，Jackson 默认以数字返回，JS Number 会丢失精度。前端已把 16 位以上的 id/noteId/creatorId 等字段解析为字符串，并在请求时以字符串回传。建议后端统一配置 Long → String 序列化。',
  },
  {
    title: '验证码没有测试通道',
    body:
      '验证码写入 Redis 并调用阿里云短信，没有测试验证码接口。本地联调请在 debug 日志中查看，或手动从 Redis 读取 verification_code:{phone}。',
  },
  {
    title: '业务失败仍是 HTTP 200',
    body:
      '所有业务异常通过 { success: false, errorCode, message } 返回，HTTP 状态仍为 200；前端已统一按 success 判断。对接第三方 SDK / 监控时需要额外注意。',
  },
];

export default {
  async mount(root, ctx) {
    const testable = GROUPS.flatMap((group) =>
      group.items.filter((item) => !item.internal).map((item) => ({ ...item, groupId: group.id }))
    );
    const total = GROUPS.reduce((sum, group) => sum + group.items.length, 0);

    root.innerHTML = `<div class="api-page">
      <div class="page-head">
        <div>
          <h1 class="page-title">接口联调</h1>
          <p class="page-sub">共 ${total} 个接口 · 覆盖认证、用户、笔记、关系、搜索、文件与内部服务</p>
        </div>
        <div class="row">
          <button class="btn btn-outline btn-sm" type="button" id="refresh-health">${icons.refresh()} 重新检测服务</button>
          <a class="btn btn-ghost btn-sm" href="#/explore">回到发现</a>
        </div>
      </div>

      <div class="health-grid" id="health-grid">
        ${['gateway', 'search', 'oss']
          .map(
            (key) => `<div class="health-card" data-health="${key}">
              <span class="status-dot is-pending"></span>
              <div class="health-main">
                <strong>${key === 'gateway' ? '网关 gateway' : key === 'search' ? '搜索服务 search' : '文件服务 oss'}</strong>
                <span data-url>检测中…</span>
              </div>
              <span class="badge" data-state>检测中</span>
            </div>`
          )
          .join('')}
      </div>

      <div class="notice notice-info">
        ${icons.info()}
        <div>
          页面通过 <code>server.js</code> 同源代理访问后端：<code>/auth /user /note /relation /search /count /file → 8000</code>，
          网关统一做鉴权、CORS 与路由；<code>/minio</code> 单独代理到对象存储。
          若服务未启动，可先用 <code>node server.js</code> 启动前端，再按 README 启动后端。
        </div>
      </div>

      <div class="api-layout">
        <aside class="api-nav">
          <div class="api-nav-title">接口分组</div>
          ${GROUPS.map(
            (group) => `<button type="button" data-scroll="group-${group.id}">
              <span>${escapeHtml(group.name)}</span>
              <span class="count">${group.items.length}</span>
            </button>`
          ).join('')}
          <button type="button" data-scroll="api-gaps">
            <span>已知接口缺口</span>
            <span class="count">${GAPS.length}</span>
          </button>
          <div class="api-nav-title" style="margin-top:10px">开发配置</div>
          <div style="padding:0 8px 6px">
            <div class="field">
              <label class="field-label" for="api-base">API Base</label>
              <input class="input" id="api-base" type="text" placeholder="留空使用同源代理"
                value="${escapeHtml(CONFIG.apiBase)}" style="height:38px;font-size:12px">
              <div class="row" style="margin-top:6px">
                <button class="btn btn-soft btn-xs" type="button" id="save-base">保存</button>
                <button class="btn btn-ghost btn-xs" type="button" id="reset-base">重置</button>
              </div>
              <span class="field-hint">直连需要后端 CORS</span>
            </div>
          </div>
        </aside>

        <div class="api-main">
          <section class="tester" id="api-tester">
            <div class="tester-head">
              <h2>${icons.zap()} 在线调试</h2>
              <label class="checkbox" style="margin-left:auto">
                <input type="checkbox" id="tester-auth"> 携带 token
              </label>
            </div>

            <div class="tester-controls">
              <select class="select" id="tester-endpoint" aria-label="选择接口">
                ${testable
                  .map(
                    (item) =>
                      `<option value="${escapeHtml(item.path)}" data-auth="${item.auth ? '1' : '0'}">${escapeHtml(
                        item.path
                      )} · ${escapeHtml(item.desc)}</option>`
                  )
                  .join('')}
              </select>
              <button class="btn btn-outline" type="button" id="tester-fill">${icons.refresh()} 填入示例</button>
              <button class="btn btn-primary" type="button" id="tester-send">${icons.play()} 发送请求</button>
            </div>

            <div class="tester-grid">
              <div class="field">
                <span class="field-label">请求体（JSON）</span>
                <textarea class="code-input" id="tester-body" spellcheck="false"></textarea>
              </div>
              <div class="field">
                <span class="field-label">响应</span>
                <div class="response-panel">
                  <div class="response-meta" id="tester-meta">等待发送</div>
                  <pre class="code-output" id="tester-output"></pre>
                </div>
              </div>
            </div>
            <p class="field-hint">
              调试请求不会因为 401 自动退出登录；multipart 接口（资料更新、文件上传）请使用页面中的上传控件。
            </p>
          </section>

          ${GROUPS.map(
            (group) => `<section class="api-group" id="group-${group.id}">
              <div class="api-group-head">
                <h2>${escapeHtml(group.name)}</h2>
                <span class="badge badge-outline">${escapeHtml(group.port)}</span>
                <span class="badge badge-brand">${group.items.length} 个</span>
              </div>
              ${group.items.map(renderEndpoint).join('')}
            </section>`
          ).join('')}

          <section class="api-group" id="api-gaps">
            <div class="api-group-head">
              <h2>${icons.alert()} 已知接口缺口与对接建议</h2>
              <span class="badge badge-amber">${GAPS.length} 项</span>
            </div>
            <div style="padding:16px 18px">
              <div class="gap-list">
                ${GAPS.map(
                  (gap, index) => `<div class="gap-item">
                    <span class="gap-num">${index + 1}</span>
                    <div><strong>${escapeHtml(gap.title)}</strong><br>${escapeHtml(gap.body)}</div>
                  </div>`
                ).join('')}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>`;

    /* --------------------------------- 健康检查 ----------------------------- */

    const refreshHealth = async () => {
      root.querySelectorAll('.health-card').forEach((card) => {
        card.querySelector('.status-dot').className = 'status-dot is-pending';
        card.querySelector('[data-state]').textContent = '检测中';
      });

      try {
        const res = await fetch('/__health', { cache: 'no-store' });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        const targets = data?.targets || {};

        Object.entries(targets).forEach(([key, info]) => {
          const card = root.querySelector(`[data-health="${key}"]`);
          if (!card) return;
          const dot = card.querySelector('.status-dot');
          const urlEl = card.querySelector('[data-url]');
          const stateEl = card.querySelector('[data-state]');

          dot.className = `status-dot ${info.up ? 'is-up' : 'is-down'}`;
          if (urlEl) urlEl.textContent = info.url || '';
          if (stateEl) {
            stateEl.textContent = info.up
              ? `在线${info.status ? ` · ${info.status}` : ''}`
              : '离线';
            stateEl.className = `badge ${info.up ? 'badge-green' : 'badge-amber'}`;
          }
        });

        if (!Object.keys(targets).length) {
          throw new Error('健康检查接口未返回目标服务');
        }
      } catch (err) {
        root.querySelectorAll('.health-card').forEach((card) => {
          card.querySelector('.status-dot').className = 'status-dot is-down';
          const stateEl = card.querySelector('[data-state]');
          if (stateEl) {
            stateEl.textContent = '未知';
            stateEl.className = 'badge badge-amber';
          }
        });
        toast('无法获取服务状态，请确认通过 node server.js 访问页面', 'warning');
      }
    };

    root.querySelector('#refresh-health')?.addEventListener('click', refreshHealth);

    /* --------------------------------- 在线调试 ----------------------------- */

    const select = root.querySelector('#tester-endpoint');
    const authCheckbox = root.querySelector('#tester-auth');
    const bodyInput = root.querySelector('#tester-body');
    const sendBtn = root.querySelector('#tester-send');
    const fillBtn = root.querySelector('#tester-fill');
    const metaEl = root.querySelector('#tester-meta');
    const outputEl = root.querySelector('#tester-output');

    const endpointMap = new Map(testable.map((item) => [item.path, item]));

    const requestText = (item) => {
      if (item.req === null) return '';
      if (typeof item.req === 'string') return '';
      return JSON.stringify(item.req, null, 2);
    };

    const applyEndpoint = (path) => {
      const item = endpointMap.get(path);
      if (!item) return;

      authCheckbox.checked = Boolean(item.auth);
      bodyInput.value = requestText(item);
      bodyInput.disabled = Boolean(item.multipart);
      sendBtn.disabled = Boolean(item.multipart);
      outputEl.textContent = '';
      metaEl.textContent = item.multipart
        ? 'multipart 接口请在页面中使用上传控件'
        : '等待发送';
    };

    select?.addEventListener('change', () => applyEndpoint(select.value));

    fillBtn?.addEventListener('click', () => {
      const item = endpointMap.get(select.value);
      if (!item) return;
      bodyInput.value = requestText(item);
      authCheckbox.checked = Boolean(item.auth);
      toast('已填入示例请求体', 'success');
    });

    sendBtn?.addEventListener('click', async () => {
      const item = endpointMap.get(select.value);
      if (!item || item.multipart) return;

      let payload;
      const raw = bodyInput.value.trim();
      if (raw) {
        try {
          payload = JSON.parse(raw);
        } catch (err) {
          metaEl.innerHTML = '<span class="bad">JSON 解析失败</span>';
          outputEl.textContent = err.message;
          return;
        }
      }

      sendBtn.classList.add('is-loading');
      sendBtn.disabled = true;
      metaEl.textContent = '请求中…';
      outputEl.textContent = '';

      const res = await rawRequest(item.path, {
        data: payload,
        needAuth: authCheckbox.checked,
        timeout: 30000,
      });

      const size = new Blob([res.text || '']).size;
      metaEl.innerHTML = `
        <span class="${res.ok ? 'ok' : 'bad'}">${res.status || 'NETWORK'} ${
        escapeHtml(res.statusText || '')
      }</span>
        <span>${res.timeMs} ms</span>
        <span>${size} B</span>
        <span>${escapeHtml(item.path)}</span>`;

      if (res.json) {
        outputEl.textContent = JSON.stringify(res.json, null, 2);
      } else {
        outputEl.textContent = res.text || '(空响应)';
      }

      sendBtn.classList.remove('is-loading');
      sendBtn.disabled = false;
    });

    root.querySelectorAll('[data-test]').forEach((button) => {
      button.addEventListener('click', () => {
        const path = button.dataset.test;
        if (!path) return;
        select.value = path;
        applyEndpoint(path);
        document.getElementById('api-tester')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });

    /* ------------------------------- 折叠与滚动 ------------------------------ */

    root.querySelectorAll('.endpoint-head').forEach((head) => {
      head.addEventListener('click', () => {
        const endpoint = head.closest('.endpoint');
        const open = endpoint?.classList.toggle('is-open');
        head.setAttribute('aria-expanded', open ? 'true' : 'false');
      });
    });

    root.querySelectorAll('[data-scroll]').forEach((button) => {
      button.addEventListener('click', () => {
        document.getElementById(button.dataset.scroll)?.scrollIntoView({
          behavior: 'smooth',
          block: 'start',
        });
      });
    });

    /* --------------------------------- API Base ----------------------------- */

    const apiBaseInput = root.querySelector('#api-base');
    apiBaseInput.value = CONFIG.apiBase || '';

    root.querySelector('#save-base')?.addEventListener('click', () => {
      const value = apiBaseInput.value.trim();
      if (value) {
        try {
          const parsed = new URL(value);
          if (!/^https?:$/.test(parsed.protocol)) throw new Error('protocol');
        } catch (err) {
          toast('请输入以 http(s):// 开头的地址，或留空使用同源代理', 'warning');
          apiBaseInput.focus();
          return;
        }
      }

      setApiBase(value);
      apiBaseInput.value = CONFIG.apiBase;
      toast(
        CONFIG.apiBase ? `已切换到 ${CONFIG.apiBase}` : '已恢复同源代理',
        'success'
      );
    });

    root.querySelector('#reset-base')?.addEventListener('click', () => {
      setApiBase('');
      apiBaseInput.value = '';
      toast('已恢复同源代理', 'success');
    });

    /* ---------------------------------- 初始化 ------------------------------ */

    if (testable.length) {
      select.value = '/search/note';
      applyEndpoint(select.value);
    }

    refreshHealth();
  },
};

function renderEndpoint(item) {
  const requestText =
    item.req === null
      ? '无请求体'
      : typeof item.req === 'string'
      ? item.req
      : JSON.stringify(item.req, null, 2);

  return `<div class="endpoint" data-path="${escapeHtml(item.path)}">
    <button class="endpoint-head" type="button" aria-expanded="false">
      <span class="method">${escapeHtml(item.method || 'POST')}</span>
      <code class="endpoint-path">${escapeHtml(item.path)}</code>
      <span class="endpoint-desc">${escapeHtml(item.desc)}</span>
      <span class="badge ${
        item.internal ? 'badge-outline' : item.auth ? 'badge-amber' : 'badge-green'
      }">${item.internal ? '内部' : item.auth ? '需登录' : '公开'}</span>
    </button>
    <div class="endpoint-body">
      <div class="endpoint-body-grid">
        <div><h4>请求体</h4><pre>${escapeHtml(requestText)}</pre></div>
        <div><h4>响应示例</h4><pre>${escapeHtml(item.res)}</pre></div>
      </div>
      ${item.note ? `<p class="field-hint" style="margin-top:10px">${escapeHtml(item.note)}</p>` : ''}
      <div class="endpoint-foot">
        ${
          item.internal
            ? '<span class="badge badge-outline">仅供服务内部调用</span>'
            : `<button class="btn btn-outline btn-xs" type="button" data-test="${escapeHtml(
                item.path
              )}">在调试面板中打开</button>`
        }
      </div>
    </div>
  </div>`;
}
