# Lumenote · 拾光笔记

仿小红书的图文 / 视频笔记社区：Spring Cloud Alibaba 微服务后端 + 无构建工具的 PC 原生 JavaScript 前端 + 基于 Spring AI 的「小拾」智能助手。

> 项目原名 `xiaohashu`，现已统一更名为 `lumenote / 拾光笔记`。

---

## 1. 项目亮点

- **微服务拆分**：认证、用户、笔记、关系、计数、搜索、对象存储、KV、ID 生成、数据对齐、AIGC 共 12 个业务模块。
- **高并发读写**：Redis 缓存 + Caffeine 本地缓存 + 布隆过滤器 + ZSet + Lua 原子操作 + RabbitMQ 异步落库 + XXL-Job 日增量对账。
- **搜索**：Canal 订阅 MySQL binlog，同步 Elasticsearch 的笔记 / 用户索引。
- **智能助手**：Spring AI + DeepSeek，支持流式 SSE、Redis 会话记忆、限流，以及只读的「我的笔记」工具调用。
- **可复现部署**：`deploy/` 提供 Docker、MySQL、Nacos、Redis、RabbitMQ、MinIO/ES/Cassandra/Canal/XXL-Job 的初始化脚本。
- **前端零构建**：HTML + CSS + ES Module，hash 路由、深色主题、骨架屏、大整数 ID 安全解析。

---

## 2. 技术栈

| 层 | 技术 |
| --- | --- |
| 语言 / 构建 | Java 17、Maven 3.8+、Spring Boot 3.4.0 |
| 微服务 | Spring Cloud 2024.0.0、Spring Cloud Alibaba 2023.0.3.4（Nacos 注册 / 配置） |
| 网关 / 鉴权 | Spring Cloud Gateway、Sa-Token 1.39（Redis 持久化） |
| 数据访问 | MyBatis 3.0.4、MySQL 8、Druid、Cassandra（笔记正文） |
| 缓存 | Redis 7 + RedisBloom、Caffeine |
| 消息 | RabbitMQ（TTL + 死信实现延迟队列） |
| 搜索 | Elasticsearch 7.x、Canal 1.1.7 |
| 任务调度 | XXL-Job 2.4.1 |
| AI | Spring AI 1.0.0、Spring AI Alibaba 1.0.0.2、DeepSeek |
| 文件 | 阿里云 OSS / MinIO（策略模式） |
| ID | Leaf 号段 + 雪花算法（ZooKeeper） |
| 前端 | HTML + CSS + 原生 ES Module，无 npm 运行时依赖 |

---

## 3. 模块与端口

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| `lumenote-gateway` | 8000 | 路由、Sa-Token 鉴权、CORS、userId Header |
| `lumenote-auth` | 8080 | 登录 / 注册、短信验证码、改密 |
| `lumenote-oss` | 8081 | 文件上传（MinIO / 阿里云 OSS） |
| `lumenote-user` | 8082 | 用户资料、注册、密码校验、RBAC |
| `lumenote-kv` | 8084 | 笔记正文（Cassandra） |
| `lumenote-distributed-id-generator` | 8085 | Leaf 号段 + 雪花 ID |
| `lumenote-note` | 8086 | 笔记发布 / 详情 / 点赞 / 收藏 / 列表 |
| `lumenote-user-relation` | 8087 | 关注 / 取关、关注与粉丝列表 |
| `lumenote-aigc` | 8088 | 智能助手（DeepSeek + SSE + 工具调用） |
| `lumenote-count` | 8090 | 笔记 / 用户维度计数 |
| `lumenote-data-align` | 8091 | 日增量计数对账（XXL-Job） |
| `lumenote-search` | 8092 | ES 搜索、热门笔记、Canal 同步 |
| `lumenote-framework` | — | common / mq / context / operationlog / jackson 基础组件 |
| `frontend` | 5180 | PC 网页（含「小拾助手」页面） |

---

## 4. 架构

```text
浏览器 (frontend :5180, hash 路由)
   │  /auth /user /note /relation /search /count /file /aigc
   ▼
Gateway :8000  ── Sa-Token 校验 + userId Header + CORS ──┐
   ├─ auth :8080 ──Feign──> user :8082                    │
   ├─ user :8082 ──Feign──> oss :8081 / idgen :8085       │
   ├─ note :8086 ──Feign──> user / kv :8084 / idgen       │
   ├─ relation :8087 ──Feign──> user / count :8090        │
   └─ aigc :8088 ──Feign──> kv / MySQL，──HTTPS──> DeepSeek
                                                          ▼
        RabbitMQ :5672  ──> count :8090 ──> MySQL t_note_count / t_user_count
              │                  └──> data-align :8091 (XXL-Job) ──> 日增量临时表 ──> 对账
              └──> note / relation 的落库消费者
        Canal :11111 ──> search :8092 ──> Elasticsearch :9200

基础设施：MySQL 3306 · Redis 6379(含 RedisBloom) · RabbitMQ 5672 · Nacos 8848
          Cassandra 9042 · ES 9200 · XXL-Job 7777/10001 · MinIO 9000 或阿里云 OSS
          ZooKeeper 2181（Leaf）
```

---

## 5. 本地启动

### 5.1 前置条件

- JDK 17+（推荐 21，CI 使用 21）
- Maven 3.8+
- Node.js 18+（只用于前端静态服务）
- MySQL 8、Redis（需要 RedisBloom 模块）、RabbitMQ、Nacos 2.x
- 按需：Cassandra 4、Elasticsearch 7.x、Canal 1.1.7、XXL-Job 2.4.x、MinIO、ZooKeeper
- 智能助手：`DEEPSEEK_API_KEY`（不配也能启动，接口会提示未配置）

### 5.2 初始化数据库

```bash
mysql -uroot -p < docs/schema.sql
cqlsh -f docs/schema.cql
```

- MySQL 建表脚本按 Mapper 反推，首次使用请按实际版本微调。
- 默认角色 `id=1 / common_user` 已在脚本中初始化。
- 日增量对账表由 `lumenote-data-align` 的 XXL-Job 自动创建。

### 5.3 环境变量

仓库不保存生产密码。开发环境默认值写在 `application-dev.yaml`，可用环境变量覆盖：

| 变量 | 说明 | dev 默认 |
| --- | --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号 | `root` / `1234` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 账号 | `deepsea` / `1234` |
| `DRUID_USERNAME` / `DRUID_PASSWORD` | Druid 监控台 | `admin` / 空 |
| `DRUID_STAT_ENABLED` | 是否开启 Druid 监控台 | `false` |
| `CANAL_PASSWORD` | Canal 密码 | `canal` |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 账号 | `deepsea` / `12345678` |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS AK/SK | 空 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-not-configured` 占位 |
| `AIGC_MODEL` | 助手模型 | `deepseek-flash` |

> 当前 `application-dev.yaml` 里的 MySQL / Redis / Nacos 地址是 `192.168.100.111`（开发虚拟机）。换环境时请全局替换成你的地址，或把公共部分抽到 Nacos 配置中心。

PowerShell 示例：

```powershell
$env:DB_PASSWORD="你的MySQL密码"
$env:REDIS_PASSWORD="你的Redis密码"
$env:RABBITMQ_PASSWORD="你的RabbitMQ密码"
$env:DEEPSEEK_API_KEY="sk-你的Key"
```

### 5.4 构建与启动

```bash
mvn -DskipTests clean package
```

按依赖顺序启动：

1. Nacos、MySQL、Redis、RabbitMQ
2. `lumenote-distributed-id-generator`（8085）
3. `lumenote-user`（8082）、`lumenote-auth`（8080）、`lumenote-oss`（8081）
4. `lumenote-kv`（8084）、`lumenote-note`（8086）、`lumenote-user-relation`（8087）
5. `lumenote-count`（8090）、`lumenote-data-align`（8091）
6. `lumenote-search`（8092，需要 ES + Canal）
7. `lumenote-aigc`（8088，需要 DeepSeek Key）
8. `lumenote-gateway`（8000）
9. 前端：`cd frontend && node server.js`，打开 `http://127.0.0.1:5180`

### 5.5 测试

```bash
# 纯单元测试（不依赖 MySQL / Redis / MQ）
mvn -pl lumenote-framework/lumenote-common,lumenote-framework/lumenote-common-mq,lumenote-framework/lumenote-spring-boot-starter-biz-context,lumenote-framework/lumenote-spring-boot-starter-jackson,lumenote-framework/lumenote-spring-boot-starter-biz-operationlog -am test -DforkCount=0
```

`tests/api-test.mjs` 是网关接口回归脚本（Node 18+，需要后端已启动）：

```bash
node tests/api-test.mjs
```

测试计划与最近一次测试结果见 [docs/test-plan.md](docs/test-plan.md)、[docs/test-report.md](docs/test-report.md)。

---

## 6. 部署脚本

`deploy/` 面向 CentOS 7 / Rocky / AlmaLinux，按基础设施拆成可独立执行的脚本：

| 目录 | 作用 |
| --- | --- |
| `deploy/docker/` | 安装 Docker CE + Compose 插件、配置镜像加速 |
| `deploy/mysql8/` | MySQL 8（数据目录绑定挂载、业务账号初始化、自检） |
| `deploy/nacos/` | Nacos 2.x（含 MySQL 配置库、命名空间） |
| `deploy/redis/` | Redis 7 |
| `deploy/rabbitmq/` | RabbitMQ 3.13 + 管理台 |
| `deploy/extras/` | MinIO / Elasticsearch / Cassandra / Canal / XXL-Job |
| `deploy/deploy-to-vm.sh` | 从 Windows 通过 SSH 串起上述步骤 |

详细说明见 [deploy/README.md](deploy/README.md)。

---

## 7. 常用接口

| 服务 | 接口 |
| --- | --- |
| auth | `POST /auth/login`、`/auth/logout`、`/auth/password/update`、`/auth/verification/code/send` |
| user | `POST /user/findByPhone`、`/user/findById`、`/user/findByIds`、`/user/update` |
| note | `POST /note/publish`、`/note/detail`、`/note/update`、`/note/delete`、`/note/like`、`/note/unlike`、`/note/collect`、`/note/uncollect`、`/note/list/publish`、`/note/list/collect`、`/note/list/like`、`/note/liked/status` |
| relation | `POST /relation/follow`、`/relation/unfollow`、`/relation/following/list`、`/relation/fans/list` |
| count | `POST /count/note`、`/count/user`、`/count/user/batch` |
| search | `POST /search/note`、`/search/note/hot`、`/search/note/rebuild`、`/search/user`、`/search/user/rebuild` |
| file | `POST /file/upload`（multipart，字段名 `file`） |
| aigc | `POST /aigc/chat`、`/aigc/chat/stream`（SSE）、`/aigc/chat/clear` |

---

## 8. 项目结构

```text
lumenote/
├── frontend/                 # PC 前端（HTML/CSS/ES Module）
├── lumenote-auth/            # 认证
├── lumenote-user/            # 用户
├── lumenote-note/            # 笔记
├── lumenote-user-relation/   # 关注/粉丝
├── lumenote-count/           # 计数
├── lumenote-data-align/      # 计数对账
├── lumenote-search/          # ES + Canal
├── lumenote-aigc/            # 智能助手（Spring AI）
├── lumenote-oss/             # 对象存储
├── lumenote-kv/              # Cassandra 正文
├── lumenote-distributed-id-generator/ # Leaf
├── lumenote-gateway/         # 网关
├── lumenote-framework/       # 基础组件
├── deploy/                   # 虚拟机 / Docker 部署脚本
├── docs/                     # 数据库脚本、测试计划、测试报告
├── tests/                    # 接口回归脚本
└── pom.xml
```

---

## 9. 已知限制 / Roadmap

- 评论、关注流 / 推荐流、`/user/me`、发布返回笔记 ID 尚未实现。
- 全局 `Long -> String` 序列化未统一；前端对雪花 ID 做了兼容处理。
- 未引入 Sentinel / Seata；当前一致性主要靠 MQ + 对账任务。
- 开发配置中的虚拟机 IP、默认密码仅用于本地联调，生产必须改为环境变量 / 配置中心。
- 旧提交历史中曾包含已删除的云账号密钥，建议轮换相关密钥。

---

## 10. 安全提示

- 不要提交 `.env`、`*.pem`、`application-local.yaml` 等本地配置。
- 生产环境必须设置 `DB_PASSWORD`、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD`、`DEEPSEEK_API_KEY` 等环境变量。
- `admin` / `1234` 这类开发默认值不要带到公网。
