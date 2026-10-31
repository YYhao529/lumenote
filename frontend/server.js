#!/usr/bin/env node
'use strict';

/**
 * 时光笔记 Web 开发服务器
 *
 * - 托管 frontend 目录下的静态文件（HTML / CSS / 原生 ES Module）
 * - 同源代理后端接口，规避网关没有配置 CORS 的问题
 * - 提供 /__health 供「接口联调」页面检测各服务是否在线
 *
 * 默认端口 5180，可通过 PORT 环境变量或第一个命令行参数覆盖：
 *   node server.js 5190
 *
 * 后端地址可通过环境变量覆盖（Windows PowerShell 同样支持）：
 *   GATEWAY_URL=http://127.0.0.1:8000
 *   SEARCH_URL=http://127.0.0.1:8092
 *   OSS_URL=http://127.0.0.1:8081
 *   MINIO_URL=http://127.0.0.1:9000
 */

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { URL } = require('node:url');

const ROOT = __dirname;
const PORT = Number(process.env.PORT || process.argv.find((arg) => /^\d+$/.test(arg)) || 5180);
const HOST = process.env.HOST || '127.0.0.1';

const GATEWAY_URL = (process.env.GATEWAY_URL || 'http://127.0.0.1:8000').replace(/\/+$/, '');
const SEARCH_URL = (process.env.SEARCH_URL || 'http://127.0.0.1:8092').replace(/\/+$/, '');
const OSS_URL = (process.env.OSS_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '');
const COUNT_URL = (process.env.COUNT_URL || 'http://127.0.0.1:8090').replace(/\/+$/, '');
// 本机 MinIO：历史数据里存的是 http://127.0.0.1:9000/xxx 绝对地址，
// 通过 /minio 同源代理转发，内网穿透后远程访客也能加载到图片/视频
const MINIO_URL = (process.env.MINIO_URL || 'http://127.0.0.1:9000').replace(/\/+$/, '');

// 注意顺序：更长的前缀放前面不影响，因为这里按前缀匹配
const ROUTES = [
  { prefix: '/auth', target: GATEWAY_URL, name: 'auth → gateway' },
  { prefix: '/user', target: GATEWAY_URL, name: 'user → gateway' },
  { prefix: '/note', target: GATEWAY_URL, name: 'note → gateway' },
  { prefix: '/relation', target: GATEWAY_URL, name: 'relation → gateway' },
  { prefix: '/search', target: SEARCH_URL, name: 'search → search-service' },
  { prefix: '/file', target: OSS_URL, name: 'file → oss-service' },
  { prefix: '/count', target: COUNT_URL, name: 'count → count-service' },
  // stripPrefix：把 /minio/a/b.mp4 转发成 http://127.0.0.1:9000/a/b.mp4
  { prefix: '/minio', target: MINIO_URL, name: 'minio → object-storage', stripPrefix: true },
];

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
};

const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
]);

const color = {
  red: (text) => `\x1b[31m${text}\x1b[0m`,
  green: (text) => `\x1b[32m${text}\x1b[0m`,
  yellow: (text) => `\x1b[33m${text}\x1b[0m`,
  cyan: (text) => `\x1b[36m${text}\x1b[0m`,
  dim: (text) => `\x1b[2m${text}\x1b[0m`,
  bold: (text) => `\x1b[1m${text}\x1b[0m`,
};

/* --------------------------------- 静态文件 -------------------------------- */

function securityHeaders() {
  return {
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'X-Frame-Options': 'SAMEORIGIN',
  };
}

function sendJson(res, statusCode, body, extraHeaders = {}) {
  const payload = JSON.stringify(body);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
    ...securityHeaders(),
    ...extraHeaders,
  });
  res.end(payload);
}

function serveFile(req, res, filePath) {
  fs.stat(filePath, (err, stat) => {
    if (err || !stat.isFile()) {
      if (path.basename(filePath) === 'index.html') {
        sendJson(res, 500, { success: false, message: 'index.html 缺失，无法提供前端页面' });
        return;
      }
      serveIndex(req, res);
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const headers = {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Content-Length': stat.size,
      // 开发服务器不做强缓存，避免改了 CSS / JS 后浏览器仍然用旧文件
      'Cache-Control': 'no-cache',
      ...securityHeaders(),
    };

    res.writeHead(200, headers);
    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    const stream = fs.createReadStream(filePath);
    stream.on('error', () => res.destroy());
    stream.pipe(res);
  });
}

function serveIndex(req, res) {
  serveFile(req, res, path.join(ROOT, 'index.html'));
}

function handleStatic(req, res, pathname) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    sendJson(res, 405, { success: false, message: 'Method Not Allowed' });
    return;
  }

  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch (err) {
    sendJson(res, 400, { success: false, message: 'Bad Request：路径编码不正确' });
    return;
  }

  if (decoded.includes('\0')) {
    sendJson(res, 400, { success: false, message: 'Bad Request' });
    return;
  }

  const relative = decoded.replace(/^\/+/, '');
  const filePath = path.resolve(ROOT, relative);

  if (filePath !== ROOT && !filePath.startsWith(`${ROOT}${path.sep}`)) {
    sendJson(res, 403, { success: false, message: 'Forbidden' });
    return;
  }

  if (!relative || decoded.endsWith('/')) {
    serveIndex(req, res);
    return;
  }

  fs.stat(filePath, (err, stat) => {
    if (!err && stat.isDirectory()) {
      serveIndex(req, res);
      return;
    }

    if (!err && stat.isFile()) {
      serveFile(req, res, filePath);
      return;
    }

    // 前端使用 hash 路由，正常情况下只有 / 会命中 index.html；
    // 对无扩展名的路径做一次 SPA 回退，便于直接访问 /#/note/xxx。
    if (!path.extname(filePath)) {
      serveIndex(req, res);
      return;
    }

    sendJson(res, 404, { success: false, message: 'Not Found' });
  });
}

/* ---------------------------------- 代理 ---------------------------------- */

function resolveRoute(pathname) {
  return ROUTES.find(
    (route) => pathname === route.prefix || pathname.startsWith(`${route.prefix}/`)
  );
}

function proxyRequest(req, res, target, pathname, search) {
  const startedAt = Date.now();
  let targetUrl;
  try {
    targetUrl = new URL(target);
  } catch (err) {
    sendJson(res, 500, { success: false, message: `代理目标配置错误：${target}` });
    return;
  }

  const headers = { ...req.headers, host: targetUrl.host };
  Object.keys(headers).forEach((key) => {
    if (HOP_BY_HOP.has(key.toLowerCase())) delete headers[key];
  });
  headers['x-forwarded-host'] = req.headers.host || '';
  headers['x-forwarded-proto'] = 'http';

  const proxyReq = http.request(
    {
      protocol: targetUrl.protocol,
      hostname: targetUrl.hostname,
      port: targetUrl.port || 80,
      method: req.method,
      path: `${pathname}${search || ''}`,
      headers,
      timeout: 60000,
    },
    (proxyRes) => {
      const responseHeaders = { ...proxyRes.headers };
      Object.keys(responseHeaders).forEach((key) => {
        if (HOP_BY_HOP.has(key.toLowerCase())) delete responseHeaders[key];
      });
      Object.assign(responseHeaders, securityHeaders());

      res.writeHead(proxyRes.statusCode || 502, responseHeaders);
      proxyRes.pipe(res);

      proxyRes.on('end', () => {
        console.log(
          `${color.dim('←')} ${req.method} ${pathname} ${color.dim(
            `${proxyRes.statusCode} · ${Date.now() - startedAt}ms`
          )}`
        );
      });
    }
  );

  proxyReq.on('timeout', () => {
    proxyReq.destroy(new Error('上游服务响应超时'));
  });

  proxyReq.on('error', (err) => {
    const message = `无法连接 ${target}：${err.message}`;
    console.error(`${color.red('✗')} ${req.method} ${pathname} ${color.red(message)}`);

    if (!res.headersSent) {
      sendJson(res, 502, {
        success: false,
        errorCode: 'PROXY_ERROR',
        message,
      });
    } else {
      res.destroy();
    }
  });

  req.on('aborted', () => proxyReq.destroy());
  req.pipe(proxyReq);
}

/* --------------------------------- 健康检查 -------------------------------- */

function probeService(urlString) {
  return new Promise((resolve) => {
    let finished = false;
    const finish = (value) => {
      if (finished) return;
      finished = true;
      resolve({ url: urlString, ...value });
    };

    let url;
    try {
      url = new URL(urlString);
    } catch (err) {
      finish({ up: false, error: 'URL 不合法' });
      return;
    }

    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port || 80,
        path: '/',
        method: 'GET',
        timeout: 1500,
        headers: { 'user-agent': 'lumenote-web-health-check' },
      },
      (res) => {
        res.resume();
        finish({ up: true, status: res.statusCode });
      }
    );

    req.on('timeout', () => {
      req.destroy();
      finish({ up: false, error: '连接超时' });
    });

    req.on('error', (err) => {
      finish({ up: false, error: err.code || err.message });
    });

    req.end();
  });
}

async function handleHealth(req, res) {
  const [gateway, search, oss, count, minio] = await Promise.all([
    probeService(GATEWAY_URL),
    probeService(SEARCH_URL),
    probeService(OSS_URL),
    probeService(COUNT_URL),
    probeService(MINIO_URL),
  ]);

  sendJson(res, 200, {
    ok: true,
    timestamp: new Date().toISOString(),
    targets: { gateway, search, oss, count, minio },
    routes: ROUTES.map((route) => ({
      prefix: route.prefix,
      target: route.target,
      name: route.name,
    })),
  });
}

/* ---------------------------------- 启动 ---------------------------------- */

const server = http.createServer((req, res) => {
  const startedAt = Date.now();

  let parsed;
  try {
    parsed = new URL(req.url, `http://${req.headers.host || `${HOST}:${PORT}`}`);
  } catch (err) {
    sendJson(res, 400, { success: false, message: 'Bad Request' });
    return;
  }

  const pathname = parsed.pathname;

  if (pathname === '/__health') {
    handleHealth(req, res).catch((err) => {
      sendJson(res, 500, { success: false, message: err.message });
    });
    return;
  }

  if (pathname === '/favicon.ico') {
    res.writeHead(204);
    res.end();
    return;
  }

  const route = resolveRoute(pathname);
  if (route) {
    // stripPrefix 的路由（如 /minio）需要把前缀去掉再转给目标服务
    const forwardPath = route.stripPrefix
      ? pathname.slice(route.prefix.length) || '/'
      : pathname;
    console.log(
      `${color.cyan('→')} ${req.method} ${pathname} ${color.dim(`proxy ${route.target}`)}`
    );
    proxyRequest(req, res, route.target, forwardPath, parsed.search);
    return;
  }

  res.on('finish', () => {
    if (pathname !== '/' && !pathname.endsWith('.html')) return;
    console.log(
      `${color.dim('·')} ${req.method} ${pathname} ${color.dim(`${Date.now() - startedAt}ms`)}`
    );
  });

  handleStatic(req, res, pathname);
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(color.red(`\n端口 ${PORT} 已被占用，请换一个端口：node server.js 5181\n`));
  } else {
    console.error(err);
  }
  process.exit(1);
});

server.listen(PORT, HOST, () => {
  const line = '─'.repeat(62);
  console.log(`\n${color.dim(line)}`);
  console.log(`${color.bold('  时光笔记 Web')}  ${color.dim('HTML + CSS + 原生 JavaScript')}`);
  console.log(`  本地地址   ${color.cyan(`http://${HOST}:${PORT}`)}`);
  console.log(`${color.dim(line)}`);
  console.log(`  ${color.bold('代理规则')}`);
  [
    { label: '网关', target: GATEWAY_URL, prefixes: '/auth /user /note /relation' },
    { label: '搜索服务', target: SEARCH_URL, prefixes: '/search' },
    { label: '文件服务', target: OSS_URL, prefixes: '/file' },
    { label: '计数服务', target: COUNT_URL, prefixes: '/count' },
    { label: '对象存储', target: MINIO_URL, prefixes: '/minio' },
  ].forEach(({ label, target, prefixes }) => {
    console.log(`  ${prefixes.padEnd(34)} → ${label} ${color.dim(target)}`);
  });
  console.log(`${color.dim(line)}`);
  console.log(`  ${color.bold('提示')} 先启动 Nacos / MySQL / Redis / ES 等依赖与各微服务`);
  console.log(`       网关 8000；搜索 8092；OSS 8081；计数 8090；MinIO 9000`);
  console.log(`       健康检查：${color.cyan(`http://${HOST}:${PORT}/__health`)}`);
  console.log(`       覆盖端口：PORT=5190 node server.js`);
  console.log(`${color.dim(`${line}\n`)}`);
});

function shutdown(signal) {
  console.log(`\n${color.dim(`收到 ${signal}，正在关闭开发服务器…`)}`);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1500).unref();
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
