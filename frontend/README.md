# 时光笔记 Web 前端（电脑端）

基于本项目 Spring Cloud Alibaba 微服务接口实现的 **面向 PC 浏览器的 HTML + CSS + 原生 JavaScript 前端**，无构建工具、无 npm 依赖。

所有页面与交互都直接调用现有后端接口，**没有修改任何后端代码**。

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

当前网关 `lumenote-gateway` 有两个前端无法绕开的问题：

1. **没有配置 CORS**。浏览器从 `http://127.0.0.1:5180` 直连 `http://127.0.0.1:8000` 会被跨域拦截。
2. **网关只路由了 `/auth`、`/user`、`/note`、`/relation`**，没有 `/search` 与 `/file`，而搜索服务（8092）和 OSS 服务（8081）又是发布笔记、上传头像所必需的。

因此 `server.js` 做了两件事：

- 托管静态文件；
- 同源代理（默认只监听 `127.0.0.1`）：

| 前端路径 | 转发到 | 说明 |
| --- | --- | --- |
| `/auth/**` | `http://127.0.0.1:8000` | 网关 → auth 服务 |
| `/user/**` | `http://127.0.0.1:8000` | 网关 → user 服务 |
| `/note/**` | `http://127.0.0.1:8000` | 网关 → note 服务 |
| `/relation/**` | `http://127.0.0.1:8000` | 网关 → relation 服务 |
| `/search/**` | `http://127.0.0.1:8092` | 网关没有该路由，直连搜索服务 |
| `/file/**` | `http://127.0.0.1:8081` | 网关没有该路由，直连 OSS 服务 |

可通过环境变量覆盖：

```bash
# macOS / Linux / Git Bash
GATEWAY_URL=http://192.168.1.10:8000 SEARCH_URL=http://192.168.1.10:8092 OSS_URL=http://192.168.1.10:8081 node server.js

# Windows PowerShell
$env:GATEWAY_URL="http://192.168.1.10:8000"; node server.js
```

生产部署时推荐在网关补齐路由与 CORS，然后把前端静态文件交给 Nginx，不需要 `server.js`。

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

- `xhs:<userId>:interactions`：点赞、收藏
- `xhs:<userId>:following`：关注的用户
- `xhs:<userId>:topped`：置顶的笔记
- `xhs:recent-notes`：最近浏览
- `xhs:recent-searches`：最近搜索

这些只是体验补偿，真实状态仍以服务端为准。换设备、换浏览器或清理缓存后会失真。

### 5. 图片与视频上传

统一走 `POST /file/upload`（multipart，字段名 `file`），返回可直接访问的 OSS URL；笔记、头像、背景图、视频都复用该接口。

如果 OSS 服务未启动，也可以在发布页直接粘贴图片外链。

---

## 四、已知的后端接口缺口

以下是前端开发过程中发现的、**需要后端确认或补充**的问题。当前前端均已用兜底方案处理，没有修改后端代码。

| # | 问题 | 前端处理 | 建议 |
| --- | --- | --- | --- |
| 1 | 网关缺少 `/search/**`、`/file/**` 路由 | 开发代理直连 8092 / 8081 | 网关补路由并统一鉴权、限流 |
| 2 | 网关未配置 CORS | 同源代理规避 | 网关统一 CORS 或前后端同域 |
| 3 | 没有推荐流 / 笔记列表接口 | 发现页以搜索 + 最近浏览为主 | 增加 feed / 我的笔记 / 用户笔记接口 |
| 4 | 登录只返回 token，没有当前用户接口 | `findByPhone → findById` 组合 | 增加 `/user/me`，或登录返回 `{token,userId}` |
| 5 | 没有点赞 / 收藏 / 关注状态查询 | 本地 localStorage 记录 | 增加状态查询接口 |
| 6 | 没有评论接口 | 详情页评论区为占位 | 补评论增删查 |
| 7 | 发布接口不返回笔记 ID | 发布成功弹窗引导去搜索 | `/note/publish` 返回新笔记 ID |
| 8 | 详情不返回 `likeTotal` / `collectTotal` / `commentTotal` / `isTop` | 数字复用搜索结果快照；置顶状态本地记录 | 扩展 `FindNoteDetailRspVO` |
| 9 | 只有「设为仅自己可见」，没有改回公开 | 操作前弹窗明确警告 | 增加可见性切换接口 |
| 10 | Long 型 ID 的 JS 精度问题 | 16 位以上 ID 转字符串 | 后端 `Long -> String` |
| 11 | 验证码没有测试通道 | 页面不展示内部提示；联调时查看 auth 服务日志 | 开发环境返回测试验证码或固定验证码 |
| 12 | 修改密码不校验原密码 | 页面正常调用 | 增加旧密码校验，修改后使旧 token 失效 |

另外有 **安全相关** 的问题建议优先处理：

- `/user/findByPhone` 会返回用户的 **密码密文**，任何已登录用户都能按手机号查询。建议只返回 `id`（或移除该字段的对外暴露）。
- `/user/findByPhone` 被前端用于换取当前用户 ID，属于“借用内部接口”，长期应替换为 `/user/me`。
- token 存在 `localStorage`，存在 XSS 风险；如果需要更高安全性，建议改成 HttpOnly Cookie + 网关统一鉴权。
- `/file/upload` 直连 8081 时绕过了网关鉴权；生产环境必须收口。

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
   - 搜索：8092
   - OSS：8081
3. `cd frontend && node server.js`
4. 打开 `http://127.0.0.1:5180`，先看「接口」页的三个健康状态。
5. 登录：
   - 验证码登录：点「获取验证码」后，到 `lumenote-auth` 日志里搜 `已生成验证码`；
   - 密码登录：需要该手机号已设置过密码。
6. 发布笔记需要 OSS 可用（或直接粘贴图片外链）。

### 常见问题

| 现象 | 排查 |
| --- | --- |
| 页面提示“网络异常” | 后端/网关未启动，或端口不一致 |
| 搜索 502 | 搜索服务 8092 未启动，或 Elasticsearch 未就绪 |
| 上传失败 | OSS 服务 8081 未启动，或阿里云 AK / bucket 配置不对 |
| 401 | token 过期或未登录；重新登录 |
| 图片 404 | OSS bucket 权限、URL 过期或图片地址不可访问 |
| 笔记详情 401 | 详情接口在网关被 Sa-Token 拦截，必须登录后访问 |
| 登录后“我的主页”提示需要绑定手机号 | 本地手机号丢失，重新登录或填写登录手机号 |

---

## 七、说明

- 本项目是纯前端静态项目，**没有引入任何框架与构建工具**，方便直接阅读、修改、部署。
- 所有对后端的调用都集中在 `js/api.js`，后端接口调整时只需改这一处。
- `#/api` 接口联调页内置接口清单、在线调试和缺口说明，适合作为联调时的对照文档；它不进入产品主导航，直接访问该地址即可。
- 浏览器建议：Chrome / Edge / Firefox 最新版（使用了原生 `<dialog>`、`AbortController`、ES Module）。
