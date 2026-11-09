# 拾光笔记 · 模块测试计划

| 项目 | 内容 |
| --- | --- |
| 被测系统 | 拾光笔记（Lumenote），Spring Cloud Alibaba 微服务 + 原生 JS 前端 |
| 文档版本 | v1.0（2026-10-31） |
| 测试环境 | VMware CentOS 7 虚拟机 `192.168.100.111` + 宿主机 Windows（Canal / 微服务进程 / 浏览器） |
| 模块数 | 后端 11 个服务 + 1 个前端 + 1 套基础设施 |
| 编写依据 | 各模块 Controller / Job Handler / Feign Api 的实际代码，非通用模板 |

---

## 1. 测试范围

### 1.1 范围内

| 层次 | 内容 |
| --- | --- |
| 后端服务 | gateway、auth、user、user-relation、note、count、kv、oss、search、data-align、distributed-id-generator |
| 前端 | 拾光笔记 Web（发现 / 搜索 / 详情 / 发布 / 个人主页 / 登录注册 / 接口文档页） |
| 基础设施 | MySQL、Redis、RabbitMQ、Nacos、Cassandra、MinIO、Elasticsearch、ZooKeeper、Canal |
| 集成点 | 网关路由、Feign 调用、Nacos 注册发现、MySQL binlog → Canal → ES、MQ 事件 → count 服务 |

### 1.2 范围外

- 压力测试的容量规划（只做基线压测，不出容量结论）
- 移动端原生 App、小程序
- 第三方服务真实计费（阿里云短信 / 阿里云 OSS 只用 Mock 或占位凭据）
- 生产环境部署与容灾演练

---

## 2. 测试环境

### 2.1 服务与端口

| 组件 | 地址 | 账号 / 说明 | 依赖方 |
| --- | --- | --- | --- |
| 网关 | `http://localhost:8000`（本地进程） | Sa-Token，`token-name: Authorization` | 全部前端请求 |
| MySQL | `192.168.100.111:3306` | root / 1234；库 `lumenote`（业务）、`leaf`（号段） | 全部业务服务 |
| Redis | `192.168.100.111:6379` | 无密码；db0 | auth / user / note / count |
| RabbitMQ | `192.168.100.111:5672` | deepsea / 1234，vhost `/lumenote` | note / count / user-relation / data-align |
| Nacos | `192.168.100.111:8848` | 免登录，namespace `lumenote` | 全部服务 |
| Cassandra | `192.168.100.111:9042` | keyspace `lumenote` | kv |
| MinIO | `192.168.100.111:9000` | deepsea / 12345678，桶 `deepsea-lumenote` | oss |
| Elasticsearch | `192.168.100.111:9200` | 7.3.0，无鉴权 | search |
| ZooKeeper | `192.168.100.111:2181` | 单节点 | id-generator（workerId） |
| Canal | 宿主机 `127.0.0.1:11111` | destination `example`，MySQL 账号 canal/canal | search（binlog 同步） |
| XXL-Job Admin | `http://192.168.100.111:7777/xxl-job-admin` | admin / 123456，`accessToken=default_token` | data-align |

### 2.2 模块依赖矩阵

`●` = 强依赖（缺失则用例不可执行）　`○` = 弱依赖（缺失可验证降级）

| 模块 | MySQL | Redis | MQ | Nacos | Cassandra | MinIO | ES | ZK | Canal | XXL-Job |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gateway | | | | ● | | | | | | |
| auth | ● | ● | | ● | | | | | | |
| user | ● | ● | | ● | | ○ | | | | |
| user-relation | ● | ● | ● | ● | | | | | | |
| note | ● | ● | ● | ● | | ● | | | | |
| count | ● | ● | ● | ● | | | | | | |
| kv | | ● | | ● | ● | | | | | |
| oss | | | | ● | | ● | | | | |
| search | ● | | | ● | | | ● | | ● | |
| data-align | ● | | ○ | ● | | | | | | ● |
| id-generator | ● | | | ● | | | | ● | | |

### 2.3 测试数据准备（执行前一次性完成）

| 数据 | 准备方式 | 说明 |
| --- | --- | --- |
| 业务库表 | `D:\JAVA_Code\lumenote1\docs\schema.sql` 已导入 | 15 张表，含 `t_role` 种子 |
| 历史数据 | 旧库 `xiaohashu` 已迁移 | 2 用户 / 8 笔记 / 10 话题 / 4 点赞 / 3 收藏 |
| 号段 | `leaf.leaf_alloc` 的 `leaf-segment-lumenote-id`、`leaf-segment-user-id` | 取号耗尽后自动续段 |
| 验证码 | 从 Redis `GET verification_code:{phone}` 读取 | 3 分钟有效，避免依赖真实短信 |
| MinIO 桶 | `deepsea-lumenote` 已创建 | 上传用例前置 |
| 测试账号 | 见 4.3 注册用例产出 | 建议固定 2 个主账号 + 1 个旁观账号 |
| ES 索引 | 由 `/search/note/rebuild`、`/search/user/rebuild` 初始化 | 索引名以代码 `NoteIndexDocBuilder` / `UserIndex` 映射为准，可用 `curl :9200/_cat/indices?v` 观察 |

---

## 3. 测试策略

### 3.1 分层

| 层 | 手段 | 覆盖目标 |
| --- | --- | --- |
| 单元 | JUnit 5（已有 `ParamUtilsTest` 为样板） | 工具类、枚举、校验规则、ID 生成器核心算法 |
| 接口 | 直连服务端口 + 经网关两种方式 | 每个 Controller 的正常/边界/异常 |
| 集成 | 真实中间件（本环境已齐全） | MQ 最终一致、binlog→ES、定时任务、Feign 链路 |
| 端到端 | 前端页面 + 网关 + 全链路 | 核心业务闭环（发布、点赞、搜索、关注） |
| 非功能 | 轻量压测 / 安全用例 / 兼容性 | 基线性能、越权、XSS、注入 |

### 3.2 优先级定义

| 优先级 | 含义 | 准入要求 |
| --- | --- | --- |
| **P0** | 主流程，挂了系统不可用 | 每次提测必回归，全绿方可发版 |
| **P1** | 重要分支 / 边界 | 提测必跑，允许已知缺陷挂起但需登记 |
| **P2** | 异常路径 / 体验 / 非功能 | 迭代内择机，发版前抽检 |

### 3.3 缺陷分级

| 级别 | 判定标准 | 示例 |
| --- | --- | --- |
| S1 阻塞 | 主流程中断、数据错乱、安全漏洞 | 登录必失败、点赞重复计数、越权改他人笔记 |
| S2 严重 | 功能不可用但有绕行路径 | 某列表分页失效、搜索不同步 |
| S3 一般 | 局部功能异常 / 校验缺失 | 昵称边界提示不友好 |
| S4 轻微 | 文案、样式、日志 | 提示语错别字、控制台告警 |

---

## 4. 模块测试计划

### 4.1 M1 网关 `lumenote-gateway`

**职责**：统一入口、路由转发、跨域、鉴权（Sa-Token Reactor）、大文件缓冲。
**依赖**：Nacos。
**路由**：`/auth/**` → auth，`/user/**` → user，`/note/**` → note，`/relation/**` → user-relation，`/search/**` → search，`/count/**` → count，`/file/**` → oss。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| GW-01 | 七条路由逐个转发 | 全部服务注册成功 | 分别返回 200，响应体来自对应服务 | P0 |
| GW-02 | 未携带 token 访问受保护接口（如 `/user/findById`） | 未登录 | 401，且不触达下游 | P0 |
| GW-03 | 携带有效 token 访问受保护接口 | 已登录 | 200 | P0 |
| GW-04 | 公开接口免鉴权（如 `/search/note/hot`） | 无 token | 200 | P0 |
| GW-05 | 跨域预检 `OPTIONS /user/findById` | `Origin: http://127.0.0.1:5180` | 返回 `Access-Control-Allow-Origin` 与允许方法 | P1 |
| GW-06 | 下游实例全部下线 | 停掉 note 服务 | 5xx/503，网关不挂死，其他路由不受影响 | P1 |
| GW-07 | 上传接近上限的文件（~200MB） | 已登录 | 不因缓冲溢出报错（`max-in-memory-size: 210MB`） | P2 |
| GW-08 | 伪造/篡改 token | 任意请求 | 401，不泄露堆栈 | P1 |
| GW-09 | 不存在的路由 `/xxx/**` | — | 404，非 500 | P2 |

### 4.2 M2 认证 `lumenote-auth`

**职责**：验证码登录 / 密码登录、登出、改密、发送验证码。
**依赖**：MySQL、Redis、Nacos、短信（可 Mock）。
**接口**：`POST /auth/login`、`/logout`、`/password/update`、`/verification/code/send`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| AUTH-01 | 发送验证码 | 合法手机号 | 200；Redis 写入 `verification_code:{phone}`，6 位，TTL≈180s | P0 |
| AUTH-02 | 验证码登录成功 | 已发送验证码 | 200，返回 token；前端写入本地存储 | P0 |
| AUTH-03 | 验证码错误 | 输入错误验证码 | 业务错误码（非 500），提示"验证码错误" | P0 |
| AUTH-04 | 验证码过期 | 等待 >3 分钟 | 提示过期，需重新获取 | P0 |
| AUTH-05 | 同一验证码重复使用 | 登录成功后再次使用 | 第二次失败（一次性） | P1 |
| AUTH-06 | 手机号格式非法（10 位 / 含字母） | — | 参数校验失败，明确提示 | P1 |
| AUTH-07 | 未注册手机号登录 | 该号码不在 `t_user` | 自动注册或提示注册（与产品约定一致） | P0 |
| AUTH-08 | 密码登录成功 / 密码错误 | 已设置密码 | 成功 200；错误返回业务错误码，不区分"用户不存在" | P0 |
| AUTH-09 | 登出后 token 失效 | 已登录 → 登出 | 再访问受保护接口 401 | P0 |
| AUTH-10 | 修改密码后旧 token 失效 | 已登录 → 改密 | 旧 token 401，新密码可登录 | P1 |
| AUTH-11 | 高频发送验证码 | 连续请求 >3 次 | 触发限流（若配置 `rate-limit`），不产生短信轰炸 | P2 |

### 4.3 M3 用户 `lumenote-user`

**职责**：注册、资料更新、用户查询、密码校验、用户号生成。
**依赖**：MySQL、Redis（角色权限缓存）、Nacos、id-generator（Feign）、oss（Feign，头像）。
**接口**：`POST /user/register`、`/findByPhone`、`/findById`、`/findByIds`、`/password/verify`、`/password/verifyById`、`/password/update`、`/user`（更新资料）、`/user/batch`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| USER-01 | 注册成功 | 合法手机号 + 验证码 | 200；`t_user` 新增记录；`lumenote_id` 唯一且递增；昵称默认 `拾光笔记{id}` | P0 |
| USER-02 | 重复手机号注册 | 已存在号码 | 业务错误码，不产生脏数据 | P0 |
| USER-03 | 用户号格式 | 注册后 | `lumenote_id` 满足 6–15 位（字母/数字/下划线）校验规则 | P0 |
| USER-04 | 查询当前用户 `/findById` | 已登录 | 200，字段齐全（含 `lumenoteId`、`nickname`、`avatar`） | P0 |
| USER-05 | 批量查询 `/findByIds` | 传入 3 个 id（含 1 个不存在） | 返回存在的 2 条，不报错 | P1 |
| USER-06 | 按手机号查询 | 已注册 | 200；敏感字段（密码）不下发 | P1 |
| USER-07 | 更新资料（昵称/简介/性别/生日） | 已登录 | 200；再次查询已生效；缓存（`user:info:`）同步 | P0 |
| USER-08 | 昵称边界：5 位 / 6 位 / 15 位 / 16 位 | — | 5/16 位拒绝，6/15 位通过（`ParamUtils` 规则） | P1 |
| USER-09 | 非法字符昵称（emoji / 空格 / `<>`） | — | 按规则拒绝，XSS 字符不落库 | P1 |
| USER-10 | 密码校验 `/password/verify` | — | 正确返回 true，错误返回 false，均非 5xx | P0 |
| USER-11 | 上传头像后资料展示 | oss 可用 | 返回可访问 URL；矿无效图片时给出错误提示 | P1 |
| USER-12 | 角色权限缓存 | 启动完成 | 启动 runner 将 `t_role`/`t_permission` 写入 Redis `role:permission:{roleKey}` | P1 |
| USER-13 | id-generator 不可用 | 停 id-generator | 注册失败并明确报错（不产生 `lumenote_id` 为空的用户） | P1 |
| USER-14 | 越权查询他人隐私字段 | 用户 A 请求用户 B | 仅返回公开字段 | P1 |

### 4.4 M4 关注关系 `lumenote-user-relation`

**职责**：关注 / 取关、关注列表、粉丝列表；通过 MQ 通知 count 服务。
**依赖**：MySQL、Redis、RabbitMQ、Nacos。
**接口**：`POST /relation/follow`、`/unfollow`、`/following/list`、`/fans/list`。
**MQ**：`follow.unfollow.direct`、`followings.fans.direct`、`followings.fans.2db.direct`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| REL-01 | 关注他人成功 | A、B 两个账号 | 200；`t_following`/`t_fans` 各新增一条 | P0 |
| REL-02 | 重复关注 | 已关注再关注 | 幂等：不重复插入、不重复计数 | P0 |
| REL-03 | 关注自己 | — | 拒绝并提示 | P1 |
| REL-04 | 取关 | 已关注 | 200；两表记录删除；计数减一（异步最终一致） | P0 |
| REL-05 | 取消未关注的用户 | — | 幂等，不报 5xx | P1 |
| REL-06 | 关注列表分页 | 关注 25 人 | 第 1 页 10 条、第 3 页 5 条；total 正确 | P0 |
| REL-07 | 粉丝列表分页 | 同上 | 同上，且包含正确用户信息 | P0 |
| REL-08 | 被拉黑/被封禁用户关注 | 构造 status≠0 用户 | 按产品规则拒绝 | P2 |
| REL-09 | MQ 发送失败 | 停 RabbitMQ | 业务不崩，计数延迟，恢复后补偿 | P1 |
| REL-10 | 关注后计数最终一致 | 关注 5 人 | 5 秒内 `count/user` 关注数 +5 | P0 |

### 4.5 M5 笔记 `lumenote-note`

**职责**：发布 / 编辑 / 删除、可见性、置顶、点赞、收藏、列表、正文读写。
**依赖**：MySQL、Redis、RabbitMQ、MinIO、Nacos、id-generator。
**接口**：`/note/publish`、`/detail`、`/update`、`/delete`、`/visible/onlyme`、`/visible/public`、`/top`、`/like`、`/unlike`、`/collect`、`/uncollect`、`/list/publish`、`/list/collect`、`/list/like`、`/liked/status`；正文：`/note/content/add|find|delete`。
**MQ**：`publish.note.direct`、`like.note(.2db).direct`、`collect.note(.2db).direct`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| NOTE-01 | 发布图文笔记 | 已登录 | 200；`t_note` 新增；正文（`content_uuid`）落库；返回 noteId | P0 |
| NOTE-02 | 发布视频笔记 | 提供 `video_uri` | 封面/视频字段正确 | P1 |
| NOTE-03 | 发布无标题 / 空内容 | — | 校验失败并提示，不落脏数据 | P0 |
| NOTE-04 | 标题超长（>200 字） | — | 截断或拒绝，规则一致 | P1 |
| NOTE-05 | 详情查询 | 刚发布 | 200；作者、话题、点赞/收藏数、可见性正确 | P0 |
| NOTE-06 | 查看他人私密笔记 | 笔记 `visible=仅自己` | 无权限（404 或 403），不泄露内容 | P0 |
| NOTE-07 | 编辑笔记（标题/正文/图片） | 作者本人 | 200；详情与搜索索引同步更新 | P0 |
| NOTE-08 | 非作者编辑 / 删除 | 用户 B 操作 A 的笔记 | 拒绝（403），数据未变 | P0（安全） |
| NOTE-09 | 切换可见性 公开 ↔ 仅自己 | 作者本人 | 200；他人视角可见性立即生效 | P1 |
| NOTE-10 | 置顶笔记 | 作者本人 | 200；个人主页置顶列表第一条 | P1 |
| NOTE-11 | 点赞 / 取消点赞 | 已登录 | 200；`t_note_like` 增删；`liked/status` 同步；计数异步 +1/-1 | P0 |
| NOTE-12 | 重复点赞 | 连续点赞两次 | 幂等，计数只 +1 | P0 |
| NOTE-13 | 收藏 / 取消收藏 | 已登录 | 同上；`list/collect` 可见/移除 | P0 |
| NOTE-14 | 点赞自己的笔记 | — | 按产品规则（允许/拒绝）保持一致 | P2 |
| NOTE-15 | 删除笔记 | 作者本人 | 200；列表与详情均不可见；正文与索引同步清理 | P0 |
| NOTE-16 | 三个列表分页 | 发布 25 条 | 发布/收藏/点赞列表分页与总数正确 | P0 |
| NOTE-17 | 正文读写 | 发布后 | `content/find` 返回原正文；`content/delete` 后不可读 | P1 |
| NOTE-18 | 图片上传失败时发布 | 停 MinIO | 发布失败并提示，不产生半成品笔记 | P1 |
| NOTE-19 | XSS 注入标题/正文 | 提交 `<script>alert(1)</script>` | 存储转义或过滤，前端渲染不执行 | P0（安全） |

### 4.6 M6 计数 `lumenote-count`

**职责**：笔记/用户维度计数（点赞、收藏、关注、粉丝、作品数），MQ 驱动 + Redis 缓存 + MySQL 落库。
**依赖**：MySQL、Redis、RabbitMQ、Nacos。
**接口**：`POST /count/note`、`/count/user`、`/count/user/batch`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| CNT-01 | 单条笔记计数查询 | 有笔记数据 | 点赞/收藏数与 `t_note_count` 一致 | P0 |
| CNT-02 | 用户计数查询 | 有用户数据 | 关注/粉丝/作品数正确 | P0 |
| CNT-03 | 批量用户计数 | 传 3 个 userId | 返回 3 组，missing 用户返回 0 或空 | P1 |
| CNT-04 | 点赞事件驱动 +1 | 消费 `like.note.direct` | 5 秒内计数 +1，Redis 与 MySQL 一致 | P0 |
| CNT-05 | 取消点赞 -1 | 消费 `like.note.direct`（取消） | 5 秒内 -1 | P0 |
| CNT-06 | 并发点赞同一笔记（50 并发） | 多用户同时点 | 最终计数 = 实际点赞数，无重复累加 | P0 |
| CNT-07 | 消息重复投递 | 人为重放同一消息 | 幂等，不重复累加 | P1 |
| CNT-08 | 死信队列 | 消息消费异常 | 进入 `error.direct`，主流程不受阻塞 | P1 |
| CNT-09 | Redis 挂掉 | 停 Redis | 计数降级读 MySQL，不返回 5xx | P2 |
| CNT-10 | 计数与源表对账 | 造 100 条点赞 | `data-align` 对齐后差异为 0 | P1 |

### 4.7 M7 KV `lumenote-kv`

**职责**：Redis 缓存读写、Cassandra 读写、雪花 ID 解码。
**依赖**：Redis、Cassandra、Nacos。
**接口**：`/kv/cache`、`/kv/db`、`/kv/decodeSnowflakeId`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| KV-01 | 写缓存 → 读缓存 | — | 值一致，命中后 TTL 按约定递减 | P0 |
| KV-02 | 缓存未命中 | 查询不存在的 key | 返回空（非 500） | P0 |
| KV-03 | 写入 Cassandra | Cassandra 正常 | 可读回，`SELECT` 与写入一致 | P0 |
| KV-04 | keyspace 不存在 | 删除 keyspace 后启动 | 启动失败并给出明确错误（不作为静默降级） | P1 |
| KV-05 | 雪花 ID 解码 | 传入合法 ID | 返回时间戳/workerId/序列号，结果与生成规则一致 | P1 |
| KV-06 | 非法 ID 解码 | 传非数字 | 参数校验失败，不抛 500 | P2 |
| KV-07 | Cassandra 宕机 | 停 Cassandra | 服务存活，调用返回友好错误 | P2 |

### 4.8 M8 对象存储 `lumenote-oss`

**职责**：文件上传（MinIO），返回可访问 URL。
**依赖**：MinIO、Nacos。
**接口**：`POST /file/upload`（multipart）、`/file/test`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| OSS-01 | 上传 JPG | 已登录，桶存在 | 200；返回 URL 可直接访问；对象落在 `deepsea-lumenote/` | P0 |
| OSS-02 | 上传 PNG / WEBP | — | 同上 | P1 |
| OSS-03 | 上传超大文件（> 业务上限） | — | 明确拒绝，提示大小限制 | P0 |
| OSS-04 | 上传非图片类型（.exe / .sh） | — | 按白名单拒绝 | P1（安全） |
| OSS-05 | 空文件 | — | 报"文件大小为空"（`FILE_EMPTY`） | P1 |
| OSS-06 | 文件名含中文/空格/../ | — | 生成安全的 `objectName`，不发生路径穿越 | P1（安全） |
| OSS-07 | 桶不存在 | 删除 `deepsea-lumenote` | 返回明确错误，不吞异常 | P1 |
| OSS-08 | 并发上传 20 个 | — | 全部成功，objectName 不冲突 | P2 |
| OSS-09 | MinIO 宕机 | 停 MinIO | 上传失败并提示，服务不崩 | P1 |

### 4.9 M9 搜索 `lumenote-search`

**职责**：笔记/用户检索（ES）、热点笔记、索引重建、Canal 增量同步。
**依赖**：MySQL（binlog）、Canal、ES 7.3.0、Nacos。
**接口**：`POST /search/note`、`/note/hot`、`/note/rebuild`、`/search/user`、`/user/rebuild`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| SRH-01 | 关键词搜笔记 | 索引已建 | 命中相关笔记，返回高亮字段与分页信息 | P0 |
| SRH-02 | 空关键词 / 特殊字符（`*`、`"`、`+`） | — | 不报 500；空关键词按默认排序或提示 | P1 |
| SRH-03 | 分页与总数 | 20 条数据 | total、totalPage 与每页条数一致 | P0 |
| SRH-04 | 无结果 | 搜索不存在词 | 200 + 空列表（非错误） | P1 |
| SRH-05 | 搜索用户（按昵称/号） | 索引已建 | 返回用户卡片信息，隐私字段不外泄 | P0 |
| SRH-06 | 新建笔记 5 秒内可搜到 | Canal 正常 | 通过 binlog 增量同步，无需手动重建 | P0（集成） |
| SRH-07 | 更新笔记后搜索结果更新 | 改标题 | 5 秒内搜索命中新标题 | P0（集成） |
| SRH-08 | 删除笔记后索引移除 | 删除笔记 | 5 秒内搜索不到 | P0（集成） |
| SRH-09 | 手动重建索引 | 调用 `/note/rebuild` | 全量重建成功，条数与库一致 | P1 |
| SRH-10 | Canal 停机 | 停 canal | 搜索仍可查旧数据；恢复后位点续传不丢变更 | P1 |
| SRH-11 | ES 宕机 | 停 ES | 搜索接口降级/报错可读，笔记主流程不受影响 | P2 |
| SRH-12 | 中文分词与拼音/别名 | — | 中文关键词命中率符合预期，无乱码 | P1 |

### 4.10 M10 数据对齐 `lumenote-data-align`（XXL-Job）

**职责**：无 HTTP 接口，8 个 Job handler 做计数对账与临时表维护。
**依赖**：MySQL、XXL-Job Admin、RabbitMQ（弱依赖）。
**Handler**：`createTableJobHandler`、`deleteTableJobHandler`、`followingCountShardingJobHandler`、`fansCountShardingJobHandler`、`notePublishCountShardingJobHandler`、`noteLikeCountShardingJobHandler`、`noteCollectCountShardingJobHandler`、`userCollectCountShardingJobHandler`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| ALN-01 | 执行器注册 | Admin 正常 | Admin 控制台"执行器管理"能看到 `xxl-job-executor-lumenote` 且有心跳 | P0 |
| ALN-02 | 创建日增量表 | 触发 createTable | 生成 `t_data_align_*_YYYYMMDD_0` 表，字段正确 | P0 |
| ALN-03 | 重复创建（同日） | 再触发一次 | 幂等，不报"表已存在"导致任务失败 | P1 |
| ALN-04 | 删除历史表 | 触发 deleteTable | 按保留天数清理，不影响当天表 | P1 |
| ALN-05 | 分片对齐：关注数 | 造 10 条不一致数据 | 任务执行后 `count` 与源表一致 | P0 |
| ALN-06 | 分片对齐：粉丝数/作品数/点赞数/收藏数 | 同上 | 同上，5 个 handler 各验一次 | P0 |
| ALN-07 | 分片广播与路由 | 多分片参数 | 每个分片只处理自己的数据，无重复计算 | P1 |
| ALN-08 | 任务超时/失败 | 人为制造异常 | 失败日志可在 Admin 查看，可重试 | P1 |
| ALN-09 | Admin 停机 | 停 7777 | 执行器启动不受影响（仅注册失败告警），业务服务正常 | P2 |
| ALN-10 | 大表分片性能 | 100 万行临时表 | 单分片执行时间在可接受范围，无全表锁 | P2 |

### 4.11 M11 分布式 ID `lumenote-distributed-id-generator`

**职责**：Leaf 号段模式 + 雪花模式发号，ZK 分配 workerId。
**依赖**：MySQL（`leaf_alloc`）、ZooKeeper、Nacos。
**接口**：`GET /id/segment/get/{key}`、`GET /id/snowflake/get/{key}`。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| IDG-01 | 号段取号 | key=`leaf-segment-lumenote-id` | 返回递增 long；`leaf_alloc.max_id` 按 step 前进 | P0 |
| IDG-02 | 并发取号（200 并发） | — | 无重复 ID，数量与请求数一致 | P0 |
| IDG-03 | 号段耗尽自动续段 | 连续取到 step 边界 | 平滑切换下一段，无重复、无明显抖动 | P0 |
| IDG-04 | 不存在的 key | key 未配置 | 明确错误（`NoKeyException`），不返回 0 | P1 |
| IDG-05 | 雪花取号 | ZK 正常 | 返回唯一 ID；日志显示 `USE ZK WORKERID-x` | P0 |
| IDG-06 | 多实例 workerId 不同 | 启动两个实例 | 两个实例 workerId 不同，ID 全局唯一 | P0 |
| IDG-07 | ZK 不可用时启动 | 停 ZK | 启动失败可读（或按设计降级到本地 workerID 文件），不产生重复 ID | P1 |
| IDG-08 | 时钟回拨 | 调整系统时间 | 触发保护，拒绝发号而非产生重复 | P2 |
| IDG-09 | 雪花 ID 解码 | 取号后调 `/kv/decodeSnowflakeId` | 解出的时间戳/workerId 与生成端一致 | P1 |
| IDG-10 | 号段 DB 不可用 | 停 MySQL | 当前段可继续发号，续段失败时明确报错 | P1 |

### 4.12 M12 前端「拾光笔记 Web」

**职责**：静态站点（`frontend/`），hash 路由 + 原生 JS 模块。
**路由**：`/explore`、`/search`、`/note/:id`、`/note/:id/edit`、`/publish`、`/user/:id`、`/me`、`/login`（另有 404）。

| 编号 | 用例 | 前置 | 预期 | 优先级 |
| --- | --- | --- | --- | --- |
| FE-01 | 未登录访问需登录路由 | 无 token | 跳转登录页，登录后回跳原页面 | P0 |
| FE-02 | 登录成功跳转 | 验证码登录 | 头部出现头像/昵称，`/me` 可访问 | P0 |
| FE-03 | token 过期处理 | token 失效后操作 | 提示重新登录，不白屏、不无限重试 | P0 |
| FE-04 | 发布笔记全流程 | 已登录 | 选图 → 预览 → 提交 → 跳详情；失败有提示 | P0 |
| FE-05 | 笔记详情 | 有数据 | 封面/正文/作者/计数正确；图片加载失败有占位 | P0 |
| FE-06 | 点赞/收藏交互 | 已登录 | 按钮态即时变化，失败回滚并提示 | P0 |
| FE-07 | 搜索页 | — | 关键词/分页/空态/错误态齐全 | P1 |
| FE-08 | 个人主页 | 有数据 | 作品/收藏/点赞 Tab 切换正确，置顶展示 | P1 |
| FE-09 | 空态与错误态 | 后端 502 / 无数据 | 显示友好占位（不是裸报错），可重试 | P1 |
| FE-10 | 表单校验 | 手机号/昵称/标题非法 | 前端即时提示，规则与后端一致 | P0 |
| FE-11 | 深色模式 | 切换主题 | 全站配色正确（重点检查文字对比度、卡片、输入框） | P1 |
| FE-12 | 响应式 | 1440 / 768 / 375 宽 | 无横向滚动，导航/卡片不重叠 | P1 |
| FE-13 | 键盘可达性 | 仅键盘 | Tab 顺序合理，焦点可见，弹窗可 Esc 关闭 | P2 |
| FE-14 | 图片懒加载与失败回退 | 弱网/坏链接 | 占位图显示，不破版 | P2 |
| FE-15 | 品牌与文案 | — | 全站为「拾光笔记」，无「小哈书/时光笔记」残留 | P1 |

---

## 5. 端到端场景（跨模块）

| 编号 | 场景 | 链路 | 关键断言 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-01 | 新用户注册 → 登录 → 发笔记 → 他人点赞收藏 → 计数与搜索可见 | gateway → auth → user → id-gen → note → oss → MQ → count → binlog → canal → ES | 每一步数据一致；5 秒内计数与搜索可见 | P0 |
| E2E-02 | 关注 / 取关 → 粉丝列表 → 粉丝数 | user-relation → MQ → count | 双向列表与计数一致 | P0 |
| E2E-03 | 编辑 / 删除笔记 → 列表、详情、搜索三处一致 | note → canal → ES | 删除后三处均不可见 | P0 |
| E2E-04 | 头像上传 → 个人主页 → 他人视角 | oss → user → MinIO 公网 URL | URL 可访问，他人可见新头像 | P1 |
| E2E-05 | 登出 → 受保护接口 | gateway → auth | 全部 401，前端回登录页 | P0 |
| E2E-06 | 数据对齐兜底 | data-align → count | 人为制造计数偏差，任务跑完差异归零 | P1 |
| E2E-07 | 基础设施重启恢复 | 重启虚拟机（systemd 自启） | 8 个中间件自动恢复，服务重连成功，业务可继续 | P1 |

---

## 6. 非功能测试

### 6.1 性能（基线，单机环境）

| 编号 | 场景 | 指标 |
| --- | --- | --- |
| PERF-01 | 笔记详情读（缓存命中 / 未命中） | P95 < 50ms / < 200ms |
| PERF-02 | 发布笔记 | P95 < 500ms |
| PERF-03 | 搜索笔记（ES） | P95 < 300ms，100 并发无错误 |
| PERF-04 | 取号 `/id/segment/get` | 1 万次无重复，QPS 与耗时记录 |
| PERF-05 | 计数最终一致延迟 | 95% 在 2 秒内完成 |

### 6.2 安全

| 编号 | 场景 | 断言 |
| --- | --- | --- |
| SEC-01 | 水平越权（改他人笔记/资料） | 403，数据不变 |
| SEC-02 | 垂直越权（普通用户访问管理类接口） | 拒绝 |
| SEC-03 | SQL 注入（搜索关键词、id 参数） | 参数化生效，无报错泄露、无数据泄露 |
| SEC-04 | XSS（笔记标题/正文/昵称） | 不执行脚本 |
| SEC-05 | 上传绕过（改后缀 / 双扩展名 / MIME 伪造） | 拒绝 |
| SEC-06 | 敏感信息泄露（日志/响应含密码、AK/SK） | 无明文 |
| SEC-07 | 重放/篡改 token | 401 |
| SEC-08 | 验证码暴力破解 | 有频率限制或失败上限 |

### 6.3 兼容与可用性

- 浏览器：Chrome / Edge / Firefox 最新版
- 视口：1440 / 1024 / 768 / 375
- 深色模式、Ctrl+F5 无缓存刷新后样式完整
- 慢网（3G 模拟）：首屏可用、图片有占位

---

## 7. 准入与准出

### 7.1 准入

1. 环境自检通过（`docker ps` 8 个容器 `Up`；`nacos` 11 个服务注册；`/search/note/rebuild` 可执行）。
2. 数据库表与基础数据就绪（15 张业务表 + `leaf_alloc` 两条号段 + MinIO 桶）。
3. 各服务可启动（`started` + `registered`）。
4. 本计划 P0 用例已评审。

### 7.2 准出

1. P0 用例 100% 通过；P1 通过率 ≥ 95% 且无 S1/S2 未修复。
2. 端到端 E2E-01/02/03/05 全通过。
3. 安全用例无 S1 问题。
4. 遗留缺陷均有责任人与计划版本，S3/S4 可带版发布。

### 7.3 回归范围

| 变更类型 | 必回归 |
| --- | --- |
| 认证/鉴权 | AUTH-01~10、GW-02/03/08、FE-01/02/03 |
| 笔记主流程 | NOTE-01/05/07/11/15、E2E-01/03 |
| MQ / 计数 | CNT-04~07、REL-02/10、ALN-05/06 |
| 搜索/同步 | SRH-01/06/07/08 |
| 前端样式与交互 | FE-09~15 |

---

## 附录 A：环境自检清单

```bash
# 1. 中间件容器（应 8 个 Up）
ssh root@192.168.100.111 'docker ps --format "{{.Names}} {{.Status}}"'

# 2. 健康探测
curl -s http://192.168.100.111:8848/nacos/v1/console/health/liveness   # Nacos
curl -s http://192.168.100.111:9200/_cluster/health                    # ES: green
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.100.111:9000/minio/health/live
docker exec redis redis-cli ping                                        # PONG

# 3. Nacos 服务列表（应 11 个）
curl -s "http://192.168.100.111:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20&namespaceId=lumenote"

# 4. Canal 订阅状态
ssh root@192.168.100.111 "docker exec mysql8 mysql -uroot -p1234 -e 'SHOW PROCESSLIST' | grep -i binlog"
```

## 附录 B：冒烟脚本思路

1. 直连（绕网关）验证每个服务存活：`POST /findById`、`POST /count/note` 等最小请求。
2. 经网关验证鉴权：无 token → 401，有 token → 200。
3. 造一条闭环数据：注册 → 发笔记 → 点赞 → 查计数 → 搜关键词，全部断言成功即判定冒烟通过。
4. 冒烟失败时按「网关 → 注册发现 → 服务日志 → 中间件」顺序定位，避免逐层盲查。
