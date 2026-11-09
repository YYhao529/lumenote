/**
 * 拾光笔记 · 接口功能测试（P0 主流程）· 修正版
 *
 * 运行：node .test/api-test.mjs
 * 修正点：
 *  1. /user/findById 的参数是 id（不是 userId）
 *  2. 发布笔记 type=0 必须带 imgUris
 *  3. 更新资料走 /user/update（multipart）
 *  4. 翻页类接口返回 totalCount；列表直接是 data 数组
 *  5. /id/**、/kv/** 未接入网关（内部接口），直连服务端口测试
 *  6. 19 位 Long ID 从原始文本用正则提取为字符串，避免 JS 精度丢失
 */

const BASE = 'http://127.0.0.1:8000';
const IDG = 'http://127.0.0.1:8085';
const KV = 'http://127.0.0.1:8084';

const results = [];
let sectionName = '';

const section = (n) => { sectionName = n; console.log(`\n===== ${n} =====\n`); };
const record = (id, name, ok, detail = '') => {
  results.push({ id, section: sectionName, name, ok, detail });
  console.log(`  [${ok ? 'PASS' : 'FAIL'}] ${id.padEnd(9)} ${name}${detail ? '  → ' + detail : ''}`);
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 从原始响应里提取 19 位 ID（避免精度丢失） */
function extractBigIds(text) {
  return [...text.matchAll(/(\d{16,19})/g)].map((m) => m[1]);
}

/**
 * 按标题在原始 JSON 文本里定位 noteId。
 * 兼容两种返回形态：数字（老版本，需防精度丢失）与字符串（修复后）。
 */
function findNoteIdByRawText(text, title) {
  const idx = text.indexOf(`"title":"${title}"`);
  if (idx < 0) return null;
  const before = text.slice(0, idx);
  const matches = [...before.matchAll(/"noteId":\s*"?(\d{16,19})"?/g)];
  return matches.length ? matches[matches.length - 1][1] : null;
}

async function api(path, { method = 'POST', body, token, form, base = BASE } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) payload = form;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
    payload = JSON.stringify(body);
  }
  try {
    const res = await fetch(base + path, { method, headers, body: payload });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text, (k, v) => (typeof v === 'number' && Number.isInteger(v) && Math.abs(v) > 1e15 ? String(v) : v)); } catch { /* SSE/非 JSON */ }
    return { status: res.status, json, text };
  } catch (e) {
    return { status: 0, json: null, text: String(e) };
  }
}

async function main() {
  section('1. 网关 / 鉴权');
  let r = await api('/user/findById', { body: { id: 3 } });
  record('GW-02', '未登录访问受保护接口 401', r.status === 401, `HTTP ${r.status}`);

  r = await api('/search/note/hot', { body: { pageNo: 1, pageSize: 3 } });
  record('GW-04', '公开接口免鉴权', r.status === 200 && r.json?.success === true, `HTTP ${r.status}`);

  r = await api('/not-exist/xxx', { body: {} });
  record('GW-01', '未知路由不返回 5xx', [401, 404].includes(r.status), `HTTP ${r.status}`);

  section('2. 认证 AUTH');
  r = await api('/auth/login', { body: { phone: '18607664441', code: '000000', type: 1 } });
  record('AUTH-03', '错误验证码被拒', r.json?.success !== true, `errorCode=${r.json?.errorCode}`);

  r = await api('/auth/login', { body: { phone: '18607664441', code: '123456', type: 1 } });
  const tokenOld = r.json?.data;
  record('AUTH-02', '验证码登录成功（老用户）', r.json?.success === true && !!tokenOld, `token ${tokenOld?.length ?? 0} 字符`);

  r = await api('/auth/login', { body: { phone: '13900000088', code: '123456', type: 1 } });
  const tokenNew = r.json?.data;
  record('AUTH-07', '新手机号登录（自动注册）', r.json?.success === true && !!tokenNew, `token ${tokenNew?.length ?? 0} 字符`);

  section('3. 用户 USER');
  r = await api('/user/findById', { body: { id: 3 }, token: tokenOld });
  record('USER-04', '查询用户信息', r.json?.success === true && !!r.json?.data?.nickname,
    `nickname=${r.json?.data?.nickname}，返回字段: ${Object.keys(r.json?.data ?? {}).join(',')}`);

  // 昵称规则：2~24 字符；拾光笔记号规则：6~15 位（ID 规则在 USER-09 单独验证）
  let f = new FormData(); f.append('nickname', 'a');
  const r1 = await api('/user/update', { form: f, token: tokenOld });
  f = new FormData(); f.append('nickname', 'ab');
  const r2 = await api('/user/update', { form: f, token: tokenOld });
  record('USER-08', '昵称边界：1 字拒绝 / 2 字通过', r1.json?.success !== true && r2.json?.success === true,
    `1字=${r1.json?.success !== true ? '拒绝' : '通过'} 2字=${r2.json?.success === true ? '通过' : '拒绝(' + r2.json?.errorCode + ')'}`);

  f = new FormData(); f.append('lumenoteId', 'abcde');
  const id5 = await api('/user/update', { form: f, token: tokenOld });
  f = new FormData(); f.append('lumenoteId', 'abcdef');
  const id6 = await api('/user/update', { form: f, token: tokenOld });
  record('USER-09', '拾光笔记号边界：5 位拒绝 / 6 位通过', id5.json?.success !== true && id6.json?.success === true,
    `5位=${id5.json?.success !== true ? '拒绝' : '通过'} 6位=${id6.json?.success === true ? '通过' : '拒绝(' + id6.json?.errorCode + ')'}`);

  section('4. 笔记 NOTE');
  const title = `API Test Note ${Date.now().toString().slice(-6)}`;
  r = await api('/note/publish', {
    body: { type: 0, title, content: 'automated test content', topicName: 'test', imgUris: ['https://example.com/a.png'] },
    token: tokenOld,
  });
  const publishOk = r.json?.success === true;
  // 修复后发布接口直接返回 noteId；兼容老版本（从列表反查）
  let noteId = r.json?.data != null ? String(r.json.data) : null;
  const returnedDirectly = !!noteId;
  if (publishOk && !noteId) {
    const lb = await api('/note/list/publish', { body: { userId: 3, pageNo: 1, pageSize: 10 }, token: tokenOld });
    noteId = findNoteIdByRawText(lb.text, title);
  }
  record('NOTE-01', '发布图文笔记并拿到 ID', publishOk && !!noteId,
    returnedDirectly ? `接口直接返回 noteId=${noteId}` : `从列表反查 noteId=${noteId}`);

  r = await api('/note/detail', { body: { id: noteId }, token: tokenOld });
  record('NOTE-05', '详情标题一致', r.json?.success === true && r.json?.data?.title === title, `title=${r.json?.data?.title}`);

  const like1 = await api('/note/like', { body: { id: noteId }, token: tokenOld });
  const like2 = await api('/note/like', { body: { id: noteId }, token: tokenOld });
  record('NOTE-11/12', '点赞成功 + 重复点赞被拒', like1.json?.success === true && like2.json?.success !== true,
    `第二次 errorCode=${like2.json?.errorCode}`);

  r = await api('/note/collect', { body: { id: noteId }, token: tokenOld });
  record('NOTE-13', '收藏笔记', r.json?.success === true, `errorCode=${r.json?.errorCode}`);

  r = await api('/note/list/publish', { body: { userId: 3, pageNo: 1, pageSize: 5 }, token: tokenOld });
  record('NOTE-16', '发布列表分页', r.json?.success === true && Array.isArray(r.json?.data), `返回 ${r.json?.data?.length ?? 0} 条`);

  r = await api('/note/delete', { body: { noteId }, token: tokenNew });
  record('NOTE-08', '越权删除他人笔记被拒', r.json?.success !== true, `errorCode=${r.json?.errorCode}`);

  section('5. 计数 COUNT（MQ 最终一致）');
  await sleep(4000);
  r = await api('/count/note', { body: { noteId }, token: tokenOld });
  record('CNT-04', '点赞后计数 +1', (r.json?.data?.likeTotal ?? 0) >= 1, `likeTotal=${r.json?.data?.likeTotal}`);

  section('6. 关注关系 REL');
  const f1 = await api('/relation/follow', { body: { followUserId: 3 }, token: tokenNew });
  const f2 = await api('/relation/follow', { body: { followUserId: 3 }, token: tokenNew });
  record('REL-01/02', '关注成功 + 重复关注不报 5xx', f1.json?.success === true && f2.status === 200,
    `第二次 errorCode=${f2.json?.errorCode ?? '幂等'}`);

  r = await api('/relation/following/list', { body: { userId: 2100, pageNo: 1, pageSize: 10 }, token: tokenNew });
  record('REL-06', '关注列表可查询', r.json?.success === true && Array.isArray(r.json?.data), `totalCount=${r.json?.totalCount}`);

  r = await api('/relation/unfollow', { body: { unfollowUserId: 3 }, token: tokenNew });
  record('REL-04', '取消关注', r.json?.success === true, `errorCode=${r.json?.errorCode}`);

  section('7. 键值存储 KV（直连 8084，未接网关）');
  const uuid = crypto.randomUUID();
  r = await api('/kv/note/content/add', { body: { uuid, content: 'cassandra content test' }, base: KV });
  record('KV-01', '写入正文到 Cassandra', r.json?.success === true, `errorCode=${r.json?.errorCode}`);

  r = await api('/kv/note/content/find', { body: { uuid }, base: KV });
  record('KV-03', '读回正文一致', r.json?.data?.content === 'cassandra content test', `content=${r.json?.data?.content}`);

  section('8. 对象存储 OSS（MinIO）');
  // 用真实 1x1 PNG（扩展名 + Content-Type + 文件头魔数都要能通过）
  const pngBytes = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64'
  );
  const txt = new FormData();
  txt.append('file', new Blob([pngBytes], { type: 'image/png' }), 'oss-test.png');
  r = await api('/file/upload', { form: txt, token: tokenOld });
  const url = r.json?.data;
  record('OSS-01', '上传 PNG 返回 URL', r.json?.success === true && !!url, url);

  if (url) {
    const g = await fetch(url);
    record('OSS-02', '上传后 URL 可匿名访问', g.status === 200, `HTTP ${g.status}`);
  }

  const exe = new FormData();
  exe.append('file', new Blob(['MZ'], { type: 'application/octet-stream' }), 'evil.exe');
  r = await api('/file/upload', { form: exe, token: tokenOld });
  record('OSS-04', '非图片类型应被拒绝（安全）', r.json?.success !== true, r.json?.success ? '缺陷：任意文件都能上传' : `已拒绝 ${r.json?.errorCode}`);

  section('9. 搜索 SEARCH');
  // Canal → ES 是异步同步，实测延迟可达 10~20 秒，这里轮询最多 25 秒
  let totalCount = 0;
  for (let i = 0; i < 25; i += 1) {
    r = await api('/search/note', { body: { keyword: 'API Test', pageNo: 1, pageSize: 10 } });
    totalCount = r.json?.totalCount ?? r.json?.data?.length ?? 0;
    if (totalCount >= 1) break;
    await sleep(1000);
  }
  record('SRH-01/06', '搜索命中刚发布的笔记（Canal 同步）', totalCount >= 1, `totalCount=${totalCount}`);

  r = await api('/search/note/rebuild', { body: {}, token: tokenOld });
  record('SRH-09', '重建笔记索引', r.json?.success === true, `errorCode=${r.json?.errorCode}`);

  section('10. 分布式 ID（直连 8085，未接网关）');
  const id1 = (await api('/id/segment/get/leaf-segment-lumenote-id', { method: 'GET', base: IDG })).text;
  const id2 = (await api('/id/segment/get/leaf-segment-lumenote-id', { method: 'GET', base: IDG })).text;
  record('IDG-01', '号段取号递增', Number(id2) > Number(id1), `${id1} → ${id2}`);

  const snow = (await api('/id/snowflake/get/leaf-snowflake', { method: 'GET', base: IDG })).text;
  record('IDG-05', '雪花取号非空且为 19 位', /^\d{18,19}$/.test(snow.trim()), `id=${snow.trim()}`);

  section('11. 智能助手 AIGC');
  r = await api('/aigc/chat', { body: { sessionId: 'api-test', message: 'say hello in 3 words' }, token: tokenOld });
  record('AIGC-01', '同步对话', r.json?.success === true && !!r.json?.data?.reply, `reply=${(r.json?.data?.reply ?? '').slice(0, 40)}`);

  const sse = await api('/aigc/chat/stream', { body: { sessionId: 'api-test', message: 'count 1 to 3' }, token: tokenOld });
  record('AIGC-02', '流式对话返回 SSE', (sse.text.match(/data:/g) ?? []).length > 0, `事件数 ${(sse.text.match(/data:/g) ?? []).length}`);

  const pass = results.filter((x) => x.ok).length;
  const fail = results.length - pass;
  console.log(`\n===== 汇总 =====\n共 ${results.length} 条：PASS ${pass} / FAIL ${fail}\n`);
  for (const x of results.filter((x) => !x.ok)) console.log(`  FAIL ${x.id} ${x.name} — ${x.detail}`);

  // 还原测试期间被改动的数据
  const restore = new FormData();
  restore.append('nickname', '深海');
  const rr = await api('/user/update', { form: restore, token: tokenOld });
  console.log(`\n数据还原：昵称恢复为「深海」→ ${rr.json?.success ? '成功' : '失败'}`);

  (await import('node:fs')).writeFileSync(new URL('./api-test-result.json', import.meta.url), JSON.stringify(results, null, 2));
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(2); });
