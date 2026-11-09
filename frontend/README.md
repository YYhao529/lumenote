# 拾光笔记 Web 前端（电脑端）

基于本项目 Spring Cloud Alibaba 微服务接口实现的 **面向 PC 浏览器的 HTML + CSS + 原生 JavaScript 前端**，无构建工具、无 npm 依赖。

所有页面与交互都通过网关调用后端接口；配套的后端修复与启动说明见根目录 `README.md`。

---

## 快速开始

前置：Node.js >= 18（仅用于本地静态服务与开发代理）。

```bash
cd frontend
node server.js
# 打开 http://127.0.0.1:5180
```

也可以：

```bash
cd frontend
npm start              # 等价于 node server.js
node server.js 5190    # 指定端口
```

启动后页面顶部与「接口」页会显示各服务健康状态；如果后端没有启动，页面会给出明确的错误提示而不是白屏。

> 不要直接双击 `index.html` 以 `file://` 打开：浏览器的同源策略会阻止接口请求，页面会提示改用 `server.js`。

---

## 浏览器与屏幕适配

这是一个**电脑端 Web 页面**，不是移动端 App，因此没有底部 TabBar，也不会做成手机布局。

- 推荐分辨率：**1280×720 及以上**；内容最大宽度默认 1320px，2K 及以上屏幕自动扩展到 1420px。
- 顶部固定导航 + 搜索框；窗口宽度 ≤ 900px 时，导航链接折叠为右上角菜单按钮，仍然是网页的顶部导航形态。
- 笔记流使用响应式栅格：≥1600px 为 5 列，1100px（不含）到 1600px 为 4 列，≤1100px 为 3 列，窄窗口为 2 列，始终填满内容区宽度。
- 兼容 Chrome / Edge / Firefox / Safari 最近两个大版本，使用了原生 `<dialog>`、ES Module、CSS Grid、`aspect-ratio`。
- 桌面快捷键：`/` 聚焦顶部搜索框，`Esc` 关闭弹窗或折叠菜单。
- 在 1024px 宽度的浏览器中，详情页、创作页、接口页仍保持桌面双栏布局；更窄时才降级为单栏。

---

## 一、为什么需要 server.js

网关 `lumenote-gateway` 现在已经补齐 `/auth`、`/user`、`/note`、`/relation`、`/search`、`/count`、`/file` 路由，并在网关层配置了 CORS。`server.js` 仍然保留，原因有两个：

1. 托管静态文件（项目没有构建工具）；
2. 同源代理，开发时前端只访问 `127.0.0.1:5180`，不用关心跨域，也方便内网穿透。

`server.js` 做的事：

- 托管静态文件；
- 同源代理（默认只监听 `127.0.0.1`）：

| 前端路径 | 转发到 | 说明 |
| --- | --- | --- |
| `/auth/**` | `http://127.0.0.1:8000` | 网关 → auth 服务 |
| `/user/**` | `http://127.0.0.1:8000` | 网关 → user 服务 |
| `/note/**` | `http://127.0.0.1:8000` | 网关 → note 服务 |
| `/relation/**` | `http://127.0.0.1:8000` | 网关 → relation 服务 |
| `/search/**` | `http://127.0.0.1:8000` | 网关 → search 服务 |
| `/count/**` | `http://127.0.0.1:8000` | 网关 → count 服务 |
| `/file/**` | `http://127.0.0.1:8000` | 网关 → oss 服务 |
| `/minio/**` | `http://127.0.0.1:9000` | MinIO 对象直读（历史数据的绝对地址） |

可通过环境变量覆盖：

```bash
# macOS / Linux / Git Bash
GATEWAY_URL=http://192.168.1.10:8000 node server.js

# Windows PowerShell
$env:GATEWAY_URL="http://192.168.1.10:8000"; node server.js
```

生产部署时前端静态文件交给 Nginx，接口统一走网关，不需要 `server.js`。

---

## 二、页面与接口覆盖

| 路由 | 页面 | 主要接口 |
| --- | --- | --- |
| `#/explore` | 发现 | 搜索入口、最近浏览（本地）、按链接直达 |
| `#/search` | 搜索 | `/search/note`、`/search/user`、关注 |
| `#/note/:id` | 笔记详情 | `/note/detail`、`/note/like`、`/note/unlike`、`/note/collect`、`/note/uncollect`、`/note/top`、`/note/visible/onlyme`、`/note/delete`、`/relation/follow` |
| `#/publish` | 发布笔记 | `/note/publish`、`/file/upload` |
| `#/note/:id/edit` | 编辑笔记 | `/note/detail`、`/note/update` |
| `#/user/:id` | 他人主页 | `/user/findById`、`/relation/following/list`、`/relation/fans/list` |
| `#/me` | 我的主页 | `/user/findByPhone`、`/user/findById`、`/user/update`、`/auth/password/update`、`/auth/logout`、关系列表 |
| `#/login` | 登录 / 注册 | `/auth/verification/code/send`、`/auth/login` |
| `#/api` | 接口联调（开发工具） | 服务健康检查、接口清单、在线调试 |

覆盖情况：

- 认证服务：4/4
- 用户服务：对外 6/6（`/user/register`、`/user/password/update` 为服务内部调用，页面不直接使用）
- 笔记服务：10/10
- 用户关系服务：4/4
- 搜索服务：2/2
- OSS 服务：1/1
- KV / 分布式 ID：属于内部 RPC，只在「接口」页文档中列出，不直接调用

顶部固定导航（窄窗口自动折叠为菜单）、登录态、路由守卫、桌面优先的响应式栅格、图片灯箱、分页、空状态、错误重试均已实现。

面向用户的页面只展示产品文案与必要的表单提示，不在页面上展示后端实现、接口路径或接口缺口；这些开发信息统一放在本 README 和 `#/api`（不进入主导航，需要时直接访问）。

---

## 三、关键实现说明

### 1. 登录态

- 后端登录接口 `/auth/login` 只返回 **token 字符串**，没有 `/user/me`。
- 前端把 token 存在 `localStorage`，请求头使用 `Authorization: Bearer <token>`（与网关 Sa-Token 的 `token-name/token-prefix` 配置一致）。
- 为了拿到当前用户资料，前端用登录手机号调用 `/user/findByPhone` 得到 `userId`，再调用 `/user/findById` 得到昵称、头像、简介。
- 因此手机号会保存在本地；如果本地被清空，`#/me` 会要求重新填写登录手机号（或重新登录）。

### 2. 雪花 ID 精度

项目使用 19 位雪花 ID，Jackson 默认把 `Long` 序列化成 JSON number，**JavaScript 的 Number 只有 53 位精度，会丢 ID 的低位**。

前端在 `js/api.js` 中对 `id / noteId / creatorId / userId / followUserId / unfollowUserId / topicId` 这些字段做了处理：16 位以上的数字先加引号再 `JSON.parse`，全程以字符串保存与回传。Spring 的 `Long` 反序列化可以接受数字字符串，所以请求不受影响。

> 建议后端统一配置 `Long -> String` 的 Jackson 序列化，这比前端打补丁更稳妥。

### 3. 统一响应

后端业务失败时 HTTP 仍返回 200，响应体为 `{ success: false, errorCode, message }`。`js/api.js` 会统一判断 `success` 并抛出 `ApiError`；401 会触发全局事件，清理登录态并跳转登录页。

### 4. 本地记录的状态

后端没有「点赞状态 / 收藏状态 / 关注状态 / 置顶状态」查询接口，前端在 `localStorage` 中按用户维度记录这些状态，用于刷新后保留按钮样式：

- `lumenote:<userId>:interactions`：点赞、收藏
- `lumenote:<userId>:following`：关注的用户
- `lumenote:<userId>:topped`：置顶的笔记
- `lumenote:recent-notes`：最近浏览
- `lumenote:recent-searches`：最近搜索

这些只是体验补偿，真实状态仍以服务端为准。换设备、换浏览器或清理缓存后会失真。

### 5. 图片与视频上传

统一走 `POST /file/upload`（multipart，字段名 `file`），返回可直接访问的 OSS URL；笔记、头像、背景图、视频都复用该接口。

如果 OSS 服务未启动，也可以在发布页直接粘贴图片外链。

---

## 四、接口现状与剩余缺口

以下问题已经修复（前端代码无需再兜底）：

| 已修复 | 说明 |
| --- | --- |
| 网关路由与 CORS | `/search`、`/count`、`/file` 已接入网关，网关统一 CORS |
| 用户笔记列表 | `/note/list/publish`、`/note/list/collect`、`/note/list/like` |
| 可见性恢复 | `/note/visible/public` |
| 点赞状态查询 | `/note/liked/status` |
| 笔记计数 | `/count/note`，详情页已使用真实点赞/收藏数 |
| 改密安全 | 修改密码必须校验原密码，成功后注销所有 token |
| 手机号查询泄露密码 | `/user/findByPhone` 只返回用户 ID，密码校验移到用户服务内部 |

仍然存在的产品/接口缺口：

| # | 缺口 | 前端现状 | 建议 |
| --- | --- | --- | --- |
| 1 | 没有 `/user/me` | 用 `findByPhone → findById` 组合 | 登录返回 `{token,userId}` 或增加 `/user/me` |
| 2 | 没有评论接口 | 详情页评论区为占位 | 补评论增删查 |
| 3 | 没有关注流 / 推荐流 | 发现页用热门笔记 + 搜索 | 增加 feed 接口 |
| 4 | 发布接口不返回笔记 ID | 发布成功引导去搜索 | `/note/publish` 返回新笔记 ID |
| 5 | 详情不返回计数字段与 `isTop` | 数字走 `/count/note`，置顶状态本地记录 | 扩展 `FindNoteDetailRspVO` |
| 6 | Long 型 ID 未全局转字符串 | 前端对 16 位以上 ID 打补丁 | 后端统一 `Long -> String` |
| 7 | 验证码没有测试通道 | 开发时看 auth 日志（debug） | 开发环境返回固定验证码 |
| 8 | token 存在 `localStorage` | 有 XSS 风险 | 改 HttpOnly Cookie 或收紧 CSP |

安全提示：`/user/password/verify`、`/user/password/verifyById` 是服务内部接口，虽然不再返回密码密文，但生产环境仍建议只允许内网/服务间访问，并在网关层做限流。

---

## 五、目录结构

```
frontend/
├── index.html              # 应用外壳（顶部导航、折叠菜单、页脚、挂载点）
├── server.js               # 静态服务 + 同源代理 + /__health
├── package.json
├── README.md
├── css/
│   ├── base.css            # 设计变量、重置、布局、按钮、表单、导航
│   ├── components.css      # 头像、笔记卡片、弹窗、Toast、骨架屏、分页
│   └── pages.css           # 各页面专属样式与响应式
└── js/
    ├── app.js              # 应用入口：登录态、导航、启动
    ├── router.js           # hash 路由 + 登录守卫
    ├── api.js              # 接口客户端（统一响应、雪花 ID、401）
    ├── store.js            # token / 手机号 / 当前用户
    ├── session.js          # 登录、退出、resolveMe
    ├── local.js            # 本地状态：最近浏览、点赞、关注、置顶
    ├── config.js           # 配置与 API Base
    ├── constants.js        # 热词、筛选选项
    ├── utils.js            # 转义、日期、格式化等
    ├── ui.js               # UI 组件
    ├── icons.js            # 线性 SVG 图标
    └── views/
        ├── explore.js
        ├── search.js
        ├── note-detail.js
        ├── note-editor.js
        ├── login.js
        ├── profile.js
        ├── user.js
        ├── relations-panel.js
        └── api-docs.js
```

---

## 六、本地联调建议顺序

1. 启动 Nacos、MySQL、Redis、RabbitMQ、（搜索需要的）Elasticsearch / Canal。
2. 启动 `lumenote-gateway`（8000）以及需要的微服务：
   - 认证：8080
   - 用户：8082
   - 笔记：8086
   - 关系：8087
   - 计数：8090
   - 搜索：8092
   - OSS：8081
3. `cd frontend && node server.js`
4. 打开 `http://127.0.0.1:5180`，先看「接口」页的三个健康状态。
5. 登录：
   - 验证码登录：点「获取验证码」后，到 `lumenote-auth` 的 debug 日志里搜 `已生成验证码`；
   - 密码登录：需要该手机号已设置过密码。
6. 发布笔记需要 OSS 可用（或直接粘贴图片外链）。

### 常见问题

| 现象 | 排查 |
| --- | --- |
| 页面提示“网络异常” | 后端/网关未启动，或端口不一致 |
| 搜索 502 | 网关到搜索服务不通，检查 8092 与 Elasticsearch |
| 上传失败 | 网关到 OSS 不通，或 OSS AK / bucket 配置不对 |
| 401 | token 过期或未登录；重新登录 |
| 修改密码后立刻 401 | 后端会注销所有 token，重新登录即可 |
| 图片 404 | OSS bucket 权限、URL 过期或图片地址不可访问 |
| 笔记详情 401 | 详情接口在网关被 Sa-Token 拦截，必须登录后访问 |
| 登录后“我的主页”提示需要绑定手机号 | 本地手机号丢失，重新登录或填写登录手机号 |

---

## 七、说明

- 本项目是纯前端静态项目，**没有引入任何框架与构建工具**，方便直接阅读、修改、部署。
- 所有对后端的调用都集中在 `js/api.js`，后端接口调整时只需改这一处。
- `#/api` 接口联调页内置接口清单、在线调试和缺口说明，适合作为联调时的对照文档；它不进入产品主导航，直接访问该地址即可。
- 浏览器建议：Chrome / Edge / Firefox 最新版（使用了原生 `<dialog>`、`AbortController`、ES Module）。
