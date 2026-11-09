# 拾光笔记 · 测试报告

| 项目 | 内容 |
| --- | --- |
| 被测版本 | `0.0.1-SNAPSHOT`（Spring Boot 3.4.0 / Spring Cloud 2024.0.0 / SCA 2023.0.3.4） |
| 测试时间 | 2026-11-01 23:00 ~ 23:25 |
| 测试环境 | 虚拟机 `192.168.100.111`（CentOS 7 + Docker 9 容器）+ 宿主机 Windows（12 个服务进程） |
| 测试类型 | 环境检查 / 单元测试 / 接口功能测试 / 集成链路 |
| 执行人 | 测试（AI 辅助） |
| 结论 | 主流程**可用**，但存在 **1 个 P0 环境缺陷（点赞/收藏完全不可用）** 与 3 个 P1 缺陷 |

---

## 一、环境检查

| 组件 | 状态 |
| --- | --- |
| 9 个中间件容器 | 全部 Up（mysql8 / redis / rabbitmq / nacos / cassandra / minio / elasticsearch / zookeeper / kibana） |
| MySQL / Redis / Nacos / MinIO / Kibana | 健康（`mysqld is alive`、`PONG`、HTTP 200） |
| Elasticsearch | `yellow`（单节点副本未分配，**预期状态**，非缺陷） |
| xxl-job-admin | systemd `active` |
| 12 个服务启动 | 11 个一次启动成功；`kv` 失败一次（见 BUG-08），Cassandra 就绪后重启成功 |

---

## 二、单元测试

`mvn test`：**BUILD SUCCESS**，共 25 个用例，0 失败（含 Redis / Cassandra / RabbitMQ 真实中间件用例）。

| 模块 | 用例数 |
| --- | --- |
| framework-common | 9 |
| framework（context / operationlog / jackson / mq） | 4 |
| auth（Redis / 线程池） | 5 |
| kv（Cassandra） | 4 |
| note（Spring AMQP） | 1 |
| user-relation（MQ） | 2 |

> 说明：现有单元测试以「中间件连通性 + 工具类」为主，业务流程（发布、点赞、关注）无单测覆盖。

---

## 三、接口功能测试

脚本：`tests/api-test.mjs`（Node 24，直连网关 8000）。

**结果：29 条用例，PASS 22 / FAIL 7。**

| 分组 | 用例 | 结果 |
| --- | --- | --- |
| 网关 | GW-02 未登录 401 / GW-04 公开接口 / GW-01 未知路由 | 3/3 PASS |
| 认证 | AUTH-03 错误验证码 / AUTH-02 登录 / AUTH-07 自动注册 | 3/3 PASS |
| 用户 | USER-04 查询资料 / USER-08 昵称边界 | 1 PASS / **1 FAIL** |
| 笔记 | NOTE-01 发布 / 05 详情 / 16 分页 / 08 越权 | PASS |
| 笔记 | NOTE-11/12 点赞、NOTE-13 收藏 | **2 FAIL** |
| 计数 | CNT-04 点赞后计数 +1 | **FAIL**（依赖点赞成功） |
| 关注 | REL-01/02 关注、REL-06 列表、REL-04 取关 | 3/3 PASS |
| 键值 | KV-01/03 写入/读回 Cassandra | 2/2 PASS |
| 对象存储 | OSS-01 上传 / OSS-02 匿名校验 / OSS-04 类型校验 | 2 PASS / **1 FAIL** |
| 搜索 | SRH-01/06 发布后可搜 / SRH-09 重建索引 | 2/2 PASS（第二轮） |
| 分布式 ID | IDG-01 号段递增 / IDG-05 雪花 | 2/2 PASS |
| 智能助手 | AIGC-01 同步 / AIGC-02 流式 | 2/2 PASS |

> 第一轮出现的 14 条失败中，有 7 条是**测试脚本自身假设错误**（参数名 `id`/`userId` 混用、`/id/**` 与 `/kv/**` 未接网关、`imgUris` 必填、分页字段是 `totalCount`）。已修正脚本并复测，以下缺陷均为修正后仍失败的真实问题。

---

## 四、缺陷清单

### BUG-01 · P0 · 环境：Redis 缺少 RedisBloom 模块，点赞/收藏功能完全不可用

- **现象**：`POST /note/like`、`POST /note/collect` 均返回 `NOTE-10000`（系统错误）。
- **证据**（note 服务日志）：
  ```
  RedisSystemException: Error in execution
  Caused by: io.lettuce.core.RedisCommandExecutionException:
    ERR Unknown Redis command called from script
    script: ba9581c1..., on @user_script:7
  ```
  直接验证：`docker exec redis redis-cli MODULE LIST` → **空**；`BF.ADD test:bloom 1` → `ERR unknown command 'BF.ADD'`。
- **根因**：代码用 `BF.ADD / BF.RESERVE / BF.EXISTS`（RedisBloom）做「今日是否已点赞/收藏」的布隆过滤，而虚拟机部署的是官方 `redis:7.4-alpine`，**未加载 redisbloom 模块**。旧环境（Docker Desktop 的 redis 容器）配置里 `loadmodule .../redisbloom.so` 是有的，迁移到虚拟机时遗漏。
- **影响**：点赞、收藏、点赞列表、收藏列表、计数（CNT-04）全部不可用 —— 属于核心交互链路。
- **建议**：
  1. 改用 `redis/redis-stack-server` 镜像（自带 RedisBloom）；或
  2. 保留官方镜像，把 `redisbloom.so` 挂进容器并在 `redis.conf` 里 `loadmodule`；
  3. 若短期无法加模块，代码侧降级为 `SETNX`/`SADD + EXPIRE` 方案（需要改 `note` 与 `data-align` 的 4 处 Lua 脚本与 4 处 `BF.ADD` 调用）。

### BUG-02 · P1 · 接口：发布笔记不返回笔记 ID

- **现象**：`POST /note/publish` 返回 `{"success":true,"data":null}`，前端发布后拿不到 ID，无法跳转详情页，也无法继续点赞/收藏。
- **证据**：`diag2.mjs` 输出 `data:null`，而列表里能查到刚发布的笔记。
- **建议**：`data` 返回 `noteId`（Long，最好按 BUG-03 序列化成字符串）。

### BUG-03 · P1 · 接口契约：19 位 Long 以 JSON 数字返回，JS 调用方精度丢失

- **现象**：同一篇笔记，列表返回 `noteId: 2116913399349641200`，真实值末尾被抹成全 0（如 `...941234` → `...9641200`）；用它调详情/点赞会得到「笔记不存在」。
- **证据**：实测 list → detail 链路失败，改用原始文本正则取 ID 后成功。
- **根因**：后端用默认 Jackson 把 `Long` 序列化为数字；JS `JSON.parse` 先转 double，19 位雪崩。前端 `api.js` 用「解析前给大 ID 加引号」的正则 hack 兜底，但任何非 JS 客户端（Python/Go/curl + jq）都会踩坑。
- **建议**：后端全局配置 `Long → String`（`Jackson2ObjectMapperBuilderCustomizer` 注册 `ToStringSerializer`），从根上消除。

### BUG-04 · P1 · 安全：文件上传不校验类型

- **现象**：上传 `evil.exe`（`application/octet-stream`）**成功**并返回可访问 URL。
- **影响**：可上传任意可执行/脚本文件；若桶策略误开写权限或链接被分享，存在钓鱼/木马风险。
- **建议**：服务端按白名单校验扩展名 + `Content-Type` + 文件头 magic number（JPEG `FFD8FF`、PNG `89504E47`、WebP `RIFF....WEBP`），并限制大小。

### BUG-05 · P2 · 规范：笔记 ID 字段名三套叫法

| 接口 | 字段 |
| --- | --- |
| `/note/detail`、`/note/like`、`/note/collect` | `id` |
| `/note/delete` | `noteId` |
| `/note/liked/status` | `noteIds`（数组） |

- **影响**：联调/测试反复踩坑（本次测试第一轮就因此误判），前端需为每个接口记不同字段。
- **建议**：统一为 `noteId`（单）/`noteIds`（批量），并同步前端。

### BUG-06 · P2 · 校验：更新资料不校验昵称长度

- **现象**：`POST /user/update` 传 `nickname=abcde`（5 字）**成功**，与注册时的 6–15 字规则不一致。
- **证据**：USER-08 用例 5 字/6 字都返回成功。
- **建议**：`UpdateUserInfoReqVO.nickname` 加 `@Size(min=6,max=15)` 或复用 `ParamUtils` 校验，与注册保持一致。

### BUG-07 · P2 · 接口：用户详情不返回拾光笔记号

- **现象**：`/user/findById` 返回字段只有 `id, nickname, avatar, introduction, backgroundImg`，缺 `lumenoteId`。
- **影响**：个人主页要展示「拾光笔记号」，前端需另调接口或从注册返回值拿。
- **建议**：VO 补 `lumenoteId`。

### BUG-08 · P2 · 部署：kv 服务对 Cassandra 无重试，冷启动即失败

- **现象**：虚拟机开机后并发启动 12 个服务，`kv` 启动失败：
  ```
  query 'SELECT * FROM system_schema.types' timed out after PT2S
  ```
  Cassandra 就绪后重启 `kv`，9 秒启动成功。
- **建议**：`CassandraConfig` 增加连接重试/退避（或 `spring.cassandra.request.timeout` + 启动探活重试），避免依赖启动顺序。

---

## 五、观察项（非缺陷，供决策）

1. **Canal → ES 同步延迟不稳定**：第一轮发布后等待 6 秒搜索未命中，第二轮命中；建议把「5 秒可见」的验收标准放宽到 15 秒，或在 Canal 侧调小 `canal.instance.memory.batch.size`/`poll` 间隔。
2. **`/id/**`、`/kv/**` 未接入网关**：属内部接口设计，但需在 API 文档中标注「仅供服务间调用」，否则调用方会撞 401。
3. **重复点赞/收藏的幂等分支无法验证**：布隆过滤器缺失时该路径根本走不到，BUG-01 修复后需回归。
4. **测试副作用**：用例修改过用户昵称（已还原为「深海」）、创建过测试笔记与两个测试账号（已清理，ES 索引已重建）。

---

## 六、回归建议

| 优先级 | 内容 |
| --- | --- |
| P0 | 修复 BUG-01 后回归：点赞 → 取消点赞 → 收藏 → 取消收藏 → 计数最终一致 → 点赞/收藏列表 |
| P1 | 修复 BUG-02/03 后回归：发布 → 用返回 ID 查详情 → 前端跳转；并用非 JS 客户端（curl）验证 ID 字符串 |
| P1 | 修复 BUG-04 后回归：exe、双扩展名、伪造 MIME、超大文件四类上传 |
| P2 | BUG-05/06/07/08 修复后跑对应用例 |

---

## 七、结论

- **可以继续开发/演示**：网关鉴权、登录注册、用户资料、笔记发布/详情/列表/越权保护、关注关系、KV（Cassandra）、对象存储、搜索、分布式 ID、智能助手（含函数调用）均通过。
- **不可交付**：点赞/收藏/计数链路因 RedisBloom 缺失完全不可用（P0）；发布不返回 ID、Long 精度、上传不校验类型属 P1，建议本轮修复。

---

# 附录：缺陷修复与回归（2026-11-02 凌晨）

## 已修复

| 编号 | 修复内容 | 验证 |
| --- | --- | --- |
| **BUG-01** | 在虚拟机编译 RedisBloom 2.2.18（`make`），产物 `redisbloom.so` 挂载进容器；`redis.conf` 加 `loadmodule`；镜像 `redis:7.4-alpine` → **`redis:7.4`（Debian/glibc）**，`REDIS_VERSION` 同步改 | `MODULE LIST` 出现 `bf 20218`；`BF.ADD`/`BF.EXISTS` 正常；点赞、重复点赞拦截（`NOTE-20010`）、收藏、计数 `likeTotal=1` 全部通过 |
| **BUG-02** | `NoteServiceImpl.publishNote` 返回 `Response.success(noteId)`（原来返回 `success()`，`data` 为 null） | 发布接口直接返回 `noteId`，用例 NOTE-01 通过 |
| **BUG-03** | `JacksonAutoConfiguration` 注册 `SimpleModule`，把 `Long`/`long` 统一用 `ToStringSerializer` 序列化成字符串（雪花 ID 19 位超出 JS 安全整数） | 列表/详情返回 `"noteId":"2116..."`，前端与非 JS 客户端都不再丢精度 |
| **BUG-04** | 新增 `FileTypeValidator`：空文件 → 大小（200MB）→ 扩展名白名单（jpg/jpeg/png/gif/webp/bmp/mp4/mov/webm）→ Content-Type 前缀 → **文件头魔数** 五层校验；`FileServiceImpl` 调用 | `.exe` 上传被拒（`OSS-20001`）；真实 PNG 上传成功且 URL 可访问 |

## 撤回（测试用例假设错误，非产品缺陷）

| 编号 | 说明 |
| --- | --- |
| **BUG-06** | `ParamUtils.checkNickname` 规则是 **2~24 字符**（不是 6~15，6~15 是「拾光笔记号」的规则）。5 字昵称本来就合法，原用例期望写错了。已改为：昵称 1 字拒绝 / 2 字通过（USER-08），另加拾光笔记号 5 位拒绝 / 6 位通过（USER-09）。 |

## 未修复（保留建议）

| 编号 | 原因 | 建议 |
| --- | --- | --- |
| BUG-05 | 字段改名 `id` → `noteId` 属破坏性变更，需前后端同步发版 | 单独排期，一次性改完并同步前端 |
| BUG-07 | 用户详情是否需要返回 `lumenoteId` 属产品口径问题（前端个人页目前从 nickname/注册返回值拿） | 产品确认后在 VO 补字段 |
| BUG-08 | kv 对 Cassandra 的重试属环境调优，不影响功能 | 加连接重试或错峰启动即可 |

## 回归结果

修复后重跑同一套接口用例：**30 条用例，PASS 30 / FAIL 0**。

额外做的健壮性调整：

- 搜索用例从「固定等 6 秒」改为「轮询最多 25 秒」——实测 Canal → ES 同步延迟可达 10~20 秒，固定等待会产生假失败；
- 文件上传用例改用真实 1×1 PNG（原用例上传 `.txt`，在新增白名单后会被正确拒绝）。

测试数据已清理（用户回到 3 个、笔记回到 8 篇，ES 重建后 8 条文档，昵称已还原）。

---

# 附录二：历史数据补迁（2026-11-02 00:00）

上一版报告里的「三处数据缺口」已全部补齐。

| 项目 | 迁移前 | 迁移后 | 验证 |
| --- | --- | --- | --- |
| 笔记正文（Cassandra） | 0 行 | **4 行**（对应 4 篇有 `content_uuid` 的笔记） | `/note/detail` 返回正文；UI 详情页正常显示 |
| 图片/视频（MinIO） | 只有 `deepsea-lumenote` 桶 | 增加 `deepsea-xiaohashu` 桶（**41 个对象，105MB**） | 旧桶对象 HTTP 200；头像/封面/视频全部正常 |
| 桶访问策略 | — | `deepsea-xiaohashu` 设为匿名只读 | 前端 `/minio/...` 代理返回 200 |

迁移要点（详见 deploy/README.md「历史数据补迁」）：

1. 旧 Cassandra 键空间是 `xiaohashu`，不是 `lumenote`；用 `COPY TO/FROM` 走 CSV。
2. MinIO 对象可以直接按 `bucket/对象名/xl.meta` 整目录拷贝，再 `docker restart minio` 识别新桶。
3. **迁完必须清笔记详情缓存**：Redis 的 `note:detail:*` + 进程内 Caffeine（重启 note 服务），且顺序是先清 Redis 再重启。

## 迁移过程中的一次误操作（已恢复）

清理 Cassandra 测试数据时，我的 KEEP 名单漏了一条历史正文 `bdbf68c9-...`（笔记「升本后醒来」，用户 2100），导致该正文被删。
发现后从旧 Cassandra 整表重新导出并覆盖导入，**4 条正文已全部恢复**（`992b…`、`f81d…`、`f9ad…`、`bdbf68c9…`）。

## 新发现的缺陷（未修，建议排期）

| 编号 | 级别 | 问题 |
| --- | --- | --- |
| BUG-09 | P2 | 笔记详情缓存**不区分「正文为空」与「取正文失败」**：kv 超时/异常时 `content=null` 会被写入 Redis + Caffeine 并缓存 1 小时，期间用户看不到正文。建议失败时不写缓存，或缩短 TTL |

## 最终状态

- 接口回归：**30/30 PASS**
- UI 实测：登录、主页、发现页、笔记详情（视频播放、正文、头像）、点赞/取消点赞 均正常
- 数据：3 个用户、8 篇笔记、4 篇有正文、图片/视频可访问

---

# 附录三：BUG-07 / BUG-09 修复 + 新发现 BUG-10（2026-11-02 00:10~00:30）

## 已修复

| 编号 | 修复内容 | 验证 |
| --- | --- | --- |
| **BUG-07** | `FindUserByIdRspDTO` 增加 `lumenoteId`；`findById` / `findByIds` 都回填；前端 `session.js` 组装 `me` 时带上该字段、`store.js` 的 `setMe` 白名单补上、`app.js` 启动时强制刷新一次资料（原来只在 `!auth.me` 时才拉，旧缓存永远不更新）；个人页「用户 ID」改为「拾光笔记号」 | `/user/findById` 返回 `lumenoteId`；个人页显示「拾光笔记号 deepsea_java」 |
| **BUG-09** | ① kv 取正文失败时吞掉异常返回 null（不让整个详情接口 500）；② **正文缺失时不写详情缓存**（有 `contentUuid` 但取不到正文 → 跳过 Redis/Caffeine 写入，并打 WARN） | 实验：删掉 Cassandra 正文行 → 详情返回空、**Redis 无 `note:detail` 写入**；恢复正文后详情立刻可见、缓存正常写入 |

## 新发现并修复：BUG-10（P0）· Canal 毒丸事件导致搜索同步永久阻塞

- **现象**：搜索偶发「刚发布的笔记搜不到」，且延迟不稳定（10 秒 ~ 60 秒+），`ES 文档数 < MySQL 笔记数`。
- **根因**：`CanalSchedule.handleUserCountEvent` 对 `t_user_count` 做 ES `update`，目标文档不存在时 ES 抛的是
  `ElasticsearchStatusException(document_missing_exception)`，而代码只 catch 了 `DocumentMissingException`；
  异常一路抛到 `run()` 的 catch → `rollback(batchId)` → **整批无限重试**，同批次里的笔记 INSERT 永远轮不到 → 搜索永久缺数据。
- **修复**：
  1. `catch (DocumentMissingException | ElasticsearchStatusException e)` → 退化为全量 `syncUserIndex/syncNoteIndex`；
  2. `processEntry` 里对**每条事件**做 try/catch 隔离，单条坏事件只记 ERROR 并跳过，不再拖垮整批。
- **验证**：重建索引后 ES = MySQL = 8；发布新笔记 → 搜索可见 **1.2 秒**（修复前 >60 秒甚至永久丢失）。

## 回归

30 条接口用例：**PASS 30 / FAIL 0**。

数据已复位：3 个用户、8 篇笔记、4 篇有正文；昵称「深海」、拾光笔记号 `deepsea_java` 已还原；ES 8 条文档。
