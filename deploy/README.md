# 虚拟机里跑 Docker + MySQL 8（lumenote 开发环境）

一套脚本，把「装 Docker CE → 起 MySQL 8 → 建业务库和账号 → 自检」串起来，适配
**CentOS 7 / Rocky Linux / AlmaLinux**（CentOS 7 已 EOL，脚本会自动切 vault 归档源）。

| 文件 | 作用 |
| --- | --- |
| `docker/install-docker.sh` | 装 Docker CE + Compose 插件，写 `daemon.json`（含镜像加速），放行 3306 |
| `mysql8/docker-compose.yml` | MySQL 8 服务定义（utf8mb4、Asia/Shanghai、**数据绑定挂载到宿主机目录**） |
| `mysql8/conf/my.cnf` | 服务端参数：字符集、时区、认证插件、慢查询、连接数 |
| `mysql8/init/01-init-db.sql` | 首次启动的初始化 SQL（只在数据目录为空时执行） |
| `mysql8/.env.example` | 密码 / 端口 / 库名 / 数据目录模板，复制成 `.env` 使用 |
| `mysql8/start-mysql.sh` | 准备数据目录并 chown 给容器内 mysql 用户 → 启动容器 → 等健康 → 建账号 |
| `mysql8/ensure-app-user.sh` | 幂等创建业务账号 `lumenote@%` |
| `mysql8/env-setup.sh` | 生成/更新 `.env`（权限 600） |
| `mysql8/check.sh` | 部署后自检：容器、端口、版本、账号登录、库是否存在 |
| `deploy-to-vm.sh` | 从 Windows 通过 SSH 完成上面全部步骤并输出连接信息 |

---

## 0. 前置条件

- 虚拟机系统：CentOS 7 / Rocky / AlmaLinux，**能上网**（装包要访问阿里云镜像）。
- 内存 ≥ 2G、磁盘 ≥ 10G（MySQL 8 镜像 600M+，数据另算）。
- 虚拟机内 sshd 在跑：`systemctl status sshd`；查看 IP：`ip a`。
- VMware 用 **NAT** 或 **桥接** 都行；NAT 下宿主机可以直接访问虚拟机 IP（通常是 `192.168.100.x`）。

> CentOS 7 默认 `firewalld` 开着。脚本只在检测到 firewalld 运行时放行 3306，不改动其它规则。

---

## 方案 A：从 Windows 一键部署（推荐）

只在**第一次**需要给虚拟机授权你的公钥（之后都是免密）。

**第 1 步：在 PowerShell 里生成并授权密钥**（把 IP 换成虚拟机的）

```powershell
ssh-keygen -t ed25519 -f $env:USERPROFILE\.ssh\lumenote_vm        # 连按两次回车（不设密码）
type $env:USERPROFILE\.ssh\lumenote_vm.pub | ssh root@192.168.100.128 "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo KEY_OK"
```

第二条命令会问你虚拟机的 root 密码，看到 `KEY_OK` 即成功。（首次连接问 `yes/no` 输 `yes`。）

**第 2 步：在 Git Bash 里执行部署**

```bash
cd /d/JAVA_Code/lumenote
bash deploy/deploy-to-vm.sh root@192.168.100.128 --key ~/.ssh/lumenote_vm
```

可选参数：

```bash
--port 2222                     # sshd 不是 22
--skip-docker                   # 机器上已有 docker，只装 MySQL
--root-password 'Your#Pwd2024'  # 指定 root 密码（默认用 .env.example 里的）
--app-password 'App#Pwd2024'    # 指定业务账号密码
```

脚本会依次：检查 SSH → 上传到 `/opt/lumenote/deploy` → 装 Docker → 生成 `.env` → 起 MySQL →
跑 `check.sh` 自检，最后打印 JDBC 串和运维命令。

---

## 方案 B：直接在虚拟机终端里跑

把 `deploy` 目录弄进虚拟机（拖拽 / 共享文件夹 / `scp -r deploy root@IP:/opt/lumenote/`），然后：

```bash
sudo bash /opt/lumenote/deploy/docker/install-docker.sh
sudo bash /opt/lumenote/deploy/mysql8/env-setup.sh 'root密码' '业务密码'
sudo bash /opt/lumenote/deploy/mysql8/start-mysql.sh
sudo bash /opt/lumenote/deploy/mysql8/check.sh
```

`start-mysql.sh` 会自动等 MySQL 初始化完成（首次 30~60 秒）再建业务账号，可重复执行。

---

## 方案 C：最小手工命令（不想用脚本时等价操作）

```bash
# 1) 装 Docker（CentOS 7 先切 vault 源，否则 yum 会 404）
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo sed -i 's|download.docker.com|mirrors.aliyun.com/docker-ce|g' /etc/yum.repos.d/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker

# 2) 起 MySQL 8（数据目录先建好并交给容器里的 mysql 用户，否则初始化会 Permission denied）
sudo mkdir -p /opt/lumenote/mysql8/{data,conf,init}
sudo chown -R 999:999 /opt/lumenote/mysql8/data && sudo chmod 750 /opt/lumenote/mysql8/data
docker run -d --name mysql8 --restart unless-stopped \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD='lumenote@123456' \
  -e MYSQL_ROOT_HOST='%' \
  -e MYSQL_DATABASE=lumenote \
  -e TZ=Asia/Shanghai \
  -v /opt/lumenote/mysql8/data:/var/lib/mysql \
  mysql:8.0 \
  --default-authentication-plugin=mysql_native_password \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci \
  --default-time-zone='+08:00'

# 3) 建业务账号
docker exec -i mysql8 mysql -uroot -p'lumenote@123456' <<'SQL'
CREATE USER IF NOT EXISTS 'lumenote'@'%' IDENTIFIED WITH mysql_native_password BY 'lumenote@123456';
GRANT ALL PRIVILEGES ON lumenote.* TO 'lumenote'@'%';
FLUSH PRIVILEGES;
SQL
```

---

## 数据落在宿主机磁盘（挂载）

MySQL 的数据**不放在容器里，也不放匿名卷**，而是绑定挂载到 Linux 宿主机目录：

```
宿主机 /opt/lumenote/mysql8/data  ──bind mount──►  容器 /var/lib/mysql
```

好处：容器 `docker rm` / 重建镜像都不影响数据，直接 `ls`、`tar`、`rsync` 就能备份，`du -sh` 看得见增长。

三个必须注意的点：

1. **属主要对**。容器里的 mysqld 以 `mysql` 用户（镜像里 uid/gid 通常是 `999:999`）运行，
   宿主机目录如果是 `root:root` 会初始化失败（`Can't create/write to file ... Permission denied`）。
   `start-mysql.sh` 会自动按镜像内的实际 uid/gid 执行 `chown -R`，手工部署时要自己 chown。
2. **SELinux**。Enforcing 时容器读不到挂载目录，`install-docker.sh` 已把开发虚拟机设成 permissive；
   生产环境请用 `chcon -Rt container_file_t /opt/lumenote/mysql8/data` 或加 `:Z` 挂载选项。
3. **初始化只跑一次**。`init/*.sql` 只在数据目录为空时执行；想重跑就 `docker compose down` +
   删空目录（**会丢数据**，先备份）。

想放到**独立数据盘**上（比如给虚拟机加了块 `/dev/sdb`）：

```bash
sudo mkfs.xfs /dev/sdb
echo '/dev/sdb  /data  xfs  defaults  0 0' | sudo tee -a /etc/fstab
sudo mkdir -p /data && sudo mount -a
# 然后把 .env 里的 MYSQL_DATA_DIR 改成 /data/mysql8，重跑 start-mysql.sh
```

---

## 接进 lumenote 项目

各服务的 `application-dev.yaml` 默认是 `jdbc:mysql://127.0.0.1:3306/lumenote`、`DB_USERNAME=root`、
`DB_PASSWORD` 留空。把地址改成虚拟机 IP，并用环境变量给密码：

```yaml
url: jdbc:mysql://192.168.100.128:3306/lumenote?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai
```

```bash
# IDEA：Run/Debug Configurations → Environment variables
DB_USERNAME=root
DB_PASSWORD=1234
```

> 为什么把认证插件设成 `mysql_native_password`：项目连接串带 `useSSL=false` 且没有
> `allowPublicKeyRetrieval=true`，MySQL 8 默认的 `caching_sha2_password` 在这种明文连接上
> 会握手失败（报 `Public Key Retrieval is not allowed`）。用 native 插件可以不动项目配置。

---

## 验证

```bash
# 虚拟机上
bash deploy/mysql8/check.sh
docker logs --tail 30 mysql8

# Windows 上测端口可达（PowerShell）
Test-NetConnection 192.168.100.128 -Port 3306
```

客户端连接（Navicat / DataGrip / IDEA）：

- Host `虚拟机IP`，Port `3306`，User `root`，Password `1234`
- 或业务账号 `lumenote` / `lumenote@123456`（最小权限，只能访问 `lumenote` 库）

### dev 配置当前指向（2026-10-31 已统一改到虚拟机）

| 依赖 | 地址 | 状态 |
| --- | --- | --- |
| MySQL | `192.168.100.111:3306`，root/1234 | ✅ 已改 6 个 `application-dev.yaml` |
| Redis | `192.168.100.111:6379`，无密码 | ✅ 已改 7 个 dev 配置 |
| Nacos | `192.168.100.111:8848`，namespace `lumenote` | ✅ 已改 11 个 `bootstrap.yaml` |
| RabbitMQ | `192.168.100.111:5672`，vhost `/lumenote`，`deepsea`/`1234` | ✅ 已改 note 的 dev 配置 |
| Cassandra | `192.168.100.111:9042`，keyspace `lumenote` | ⚠️ 已部署，kv 的 dev 配置待改（仍是 127.0.0.1） |
| Elasticsearch | `192.168.100.111:9200`，7.3.0，single-node | ✅ 已改 search 的 ES 地址 |
| Canal | 跑在宿主机 Windows，读 `192.168.100.111:3306` 的 binlog | ✅ MySQL 复制账号已建；`canal.address` 保持 `127.0.0.1:11111` |
| ZooKeeper | `192.168.100.111:2181`（Leaf 雪花 workerId） | ✅ 已改 `leaf.properties`（ZK 地址 + leaf 库 + snowflake.port=8085） |
| MinIO | `192.168.100.111:9000`，`deepsea`/`12345678`，桶 `deepsea-lumenote` | ✅ 已改 oss 的 dev 配置 |
| xxl-job | `192.168.100.111:7777`，admin/123456，`accessToken=default_token` | ✅ 已部署（systemd 托管，见下节） |

> 注意：`search`、`kv`、`gateway`、`user`、`note`、`distributed-id-generator` 的 `bootstrap.yaml` 里只有
> `discovery`（服务注册），没有 `config` 段，所以它们只注册到 Nacos、不会拉远程配置。
> 想让它们也吃 Nacos 配置，需要在 `bootstrap.yaml` 里补 `spring.cloud.nacos.config` 段
> （`prefix`/`file-extension`/`namespace`/`server-addr`）。

---

## 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| `yum` 报 404 / `Cannot find a valid baseurl` | CentOS 7 已 EOL，基础源要切 `vault.centos.org`（脚本已处理；手工装则改 `/etc/yum.repos.d/CentOS-*.repo`） |
| `docker pull` 卡住或超时 | Docker Hub 国内受限。`/etc/docker/daemon.json` 的 `registry-mirrors` 已配 daocloud/1ms；仍失败就换镜像站，或离线：`docker save/load` |
| `Error response from daemon: driver failed programming external connectivity` | 3306 被占用：`ss -lntp \| grep 3306`，改 `.env` 里 `MYSQL_PORT=3307` 重跑 |
| 宿主机连不上 3306 | 虚拟机 `firewall-cmd --add-port=3306/tcp --permanent && firewall-cmd --reload`；确认 VMware 网卡是 NAT/桥接而非「仅主机」 |
| 容器起了但反复重启 | `docker logs mysql8`；常见是 `my.cnf` 参数拼错，或数据卷是旧版本初始化的 |
| mysqld 反复重启，日志 `unknown variable 'xxx'` | `my.cnf` 里写了 8.0 不存在的参数（本次实测踩到：把 `max_connect_errors` 写成了 `max_errors`）。`start-mysql.sh` 会先用 `mysqld --validate-config` 预检并直接打印出来，改完再启动 |
| dockerd 起不来，日志 `ZONE_CONFLICT: 'docker0' already bound to a zone` | docker0 被手工加进了 trusted/public 域，和 Docker 自己维护的 `docker` 域冲突。`firewall-cmd --permanent --zone=trusted --remove-interface=docker0 && firewall-cmd --reload && systemctl start docker`（脚本里已改为自动清理，不再把 docker0 塞进 trusted 域） |
| 数据目录 owner 显示 `polkitd:input` | CentOS 7 上 uid/gid 999 恰好是 polkitd/input，容器内是 mysql；**数字一致即可**，不用改 |
| 启动报 `Permission denied` 且目录是挂载的 | 宿主机目录属主不是 `999:999`：`sudo chown -R 999:999 /opt/lumenote/mysql8/data` |
| 挂载目录里没有数据、`docker inspect` 显示匿名卷 | compose 里 `MYSQL_DATA_DIR` 没生效（`.env` 没写或路径为空），改好 `.env` 后 `docker compose up -d --force-recreate` |
| 改了 `init/*.sql` 却没生效 | 初始化只在数据卷为空时执行：`docker compose down -v` 再起（**会清库**） |
| 中文乱码 | 服务端已设 utf8mb4；检查客户端/连接串 `characterEncoding=utf-8` |
| `.env` 密码含特殊字符 | 不要用空格、`#`、单引号；`@`、`#`（非行首）、`!` 可用 |

## 运维速查

```bash
docker ps                                  # 容器状态
docker logs -f mysql8                      # 实时日志
docker exec -it mysql8 mysql -uroot -p     # root 进 CLI
docker exec -it mysql8 mysql -ulumenote -p # 业务账号进 CLI
docker stats mysql8                        # 资源占用

# 备份 / 恢复
docker exec mysql8 mysqldump -uroot -p'密码' --single-transaction --databases lumenote > lumenote.sql
docker exec -i mysql8 mysql -uroot -p'密码' < lumenote.sql

# 重启 / 停止 / 彻底删除（含数据）
docker restart mysql8
docker stop mysql8
cd /opt/lumenote/deploy/mysql8 && docker compose down            # 只删容器，数据还在宿主机目录
docker compose down && rm -rf /opt/lumenote/mysql8/data          # 连数据一起删（危险）
```

## 卸载 Docker

```bash
systemctl stop docker
yum remove -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
rm -rf /var/lib/docker /var/lib/containerd /etc/docker
```

---

## 从旧库（xiaohashu）迁移业务数据

项目改名前叫 `xiaohashu`，旧数据在 Docker Desktop 的 `mysql8` 容器里（`mysql:8.0.27`，库名 `xiaohashu`）。
迁到虚拟机 `lumenote` 库时有两个改名点，直接 dump 过来会踩坑：

| 旧 | 新 |
| --- | --- |
| 库 `xiaohashu` | 库 `lumenote` |
| 列 `t_user.xiaohashu_id`（含唯一键 `uk_xiaohashu_id`） | 列 `t_user.lumenote_id`（`uk_lumenote_id`） |

做法（流式完成，不落中间库）：

```bash
docker exec mysql8 mysqldump -uroot -p'密码' --single-transaction \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases xiaohashu \
  | sed -e 's/xiaohashu_id/lumenote_id/g' -e 's/`xiaohashu`/`lumenote`/g' \
  | ssh root@192.168.100.111 \
      'docker exec -i mysql8 mysql -uroot -p1234 --default-character-set=utf8mb4'
```

三个注意点：

1. **两边都要 `--default-character-set=utf8mb4`**，否则 Windows 客户端默认 gbk，中文会变 `?`。
2. PowerShell 重定向（`>` / `Set-Content`）会加 **UTF-8 BOM**，导入前用 `tail -c +4` 剥掉，
   否则首条语句可能解析失败。
3. 导入前先比对表结构（`information_schema.columns`），确认除改名点外**列顺序和类型一致**——
   mysqldump 的 `INSERT` 是按位置插入的。

验证：逐表 `COUNT(*)` 对齐 + 关键文本列 `MD5(GROUP_CONCAT(...))` 双向比对
（本次 `t_user.nickname`、`t_note.title` 的 MD5 完全一致，中文无乱码）。

---

## MySQL（业务库 + binlog）

| 项 | 值 |
| --- | --- |
| 版本 | `mysql:8.0`（当前 8.0.46），容器 `mysql8`，`0.0.0.0:3306` |
| 账号 | `root` / `1234`（另保留业务账号 `lumenote` / `lumenote@123456`） |
| 数据 | `/opt/lumenote/mysql8/data`（bind mount，含 binlog 文件） |
| 参数 | [conf/my.cnf](mysql8/conf/my.cnf)，启动前用 `mysqld --validate-config` 预检 |

### 二进制日志（Canal 依赖）

MySQL 8.0 默认就开 binlog，但 `my.cnf` 里已**显式写死**，不依赖默认值：

```
log-bin                    = binlog
server-id                  = 1
binlog_format              = ROW      # Canal 只认 ROW
binlog_row_image           = FULL
sync_binlog                = 1
max_binlog_size            = 512M
binlog_expire_logs_seconds = 604800   # 保留 7 天
```

要点：

- `log-bin` 这类参数**改完必须重启**才能生效（`format` 之类可以运行时改），重启不影响已落盘数据。
- MySQL 8.0.46 会提示 `'binlog_format' is deprecated`，这是预期警告：8.0 只支持 ROW，参数仍可用。
- 验证：`SHOW MASTER STATUS` 拿到 file/position；`SHOW BINLOG EVENTS` 里应能看到 `Table_map` / `Write_rows` 事件。
- Canal 还需要一个具备 `SELECT, REPLICATION SLAVE, REPLICATION CLIENT` 的账号，
  [start-extras.sh](extras/start-extras.sh) 会自动创建 `canal` / `canal`（`mysql_native_password`）。

---

## Redis（缓存 / 会话）

| 项 | 值 |
| --- | --- |
| 镜像 | `redis:7.4-alpine`，容器名 `redis`，`restart: unless-stopped` |
| 端口 | `6379` |
| 密码 | 开发环境不设（项目里 `password: ${REDIS_PASSWORD:}` 默认为空）；要用就改 [conf/redis.conf](redis/conf/redis.conf) 的 `requirepass` 并给项目配 `REDIS_PASSWORD` |
| 持久化 | AOF（`appendonly yes` + `appendfsync everysec`）+ RDB 快照兜底 |
| 数据 | `/opt/lumenote/redis/data`（bind mount，Redis 7 的 AOF 在 `appendonlydir/` 下） |

脚本：[start-redis.sh](redis/start-redis.sh)、[check.sh](redis/check.sh)。

两个容易踩的点：

1. **`protected-mode`**：官方镜像不带配置文件启动时，无密码 + 未显式 `bind` 会拒绝外部连接。
   `conf/redis.conf` 里显式写了 `bind 0.0.0.0` 和 `protected-mode no`，否则 Windows 侧连不上。
2. **数据目录属主**：容器内 redis 用户是 `999:1000`（alpine 镜像），宿主机目录要给对属主，
   `start-redis.sh` 会按镜像内实际 uid/gid 执行 chown。

### 从旧 Redis 迁移数据

旧环境的 Redis 是 Docker Desktop 里的 `redis:7.2.3` 容器（`requirepass 123456`）。用 `MIGRATE` 逐键复制，
`COPY` 保证源端不删、`REPLACE` 允许覆盖同名键，**TTL 会一并带过去**：

```bash
# 在源容器里执行；目标是无密码的 192.168.100.111:6379
docker exec redis redis-cli -a 123456 --no-auth-warning \
  MIGRATE 192.168.100.111 6379 "<key>" 0 8000 COPY REPLACE
```

注意三个点：

1. **`MIGRATE ... KEYS k1 k2` 的 key 参数必须传空串**，而 PowerShell 会把空参数吞掉（报
   `the key argument must be set to the empty string`）——要么逐键迁移，要么在容器的 sh 里执行。
2. 有一个键要**改名**：旧的 `xiaohashu.id.generator` → 新的 `lumenote.id.generator`
   （见 `RedisKeyConstants.LUMENOTE_ID_GENERATOR_KEY`）。其余前缀
   （`user:roles:` / `role:permission:` / `user:info:` / `verification_code:` / `Authorization:login:`）都没变。
3. `Authorization:login:token|session:*` 是 **Sa-Token** 的登录态（`token-name: Authorization`），
   新旧代码一致，迁过去后旧的登录会话仍然有效。

验证：`DBSIZE` 对齐 + 逐键 `STRLEN` 对比 + 检查两个带 TTL 的键过期时间是否被保留。

---

## XXL-Job 调度中心 + 数据对齐（lumenote-data-align）

镜像拉不下来（Docker Hub 被墙），所以调度中心**跑在虚拟机里、用 systemd 托管**，jar 由本机源码构建后拷进去。

### 部署方式

```bash
# 1. 虚拟机装 JDK 8（xxl-job 2.4.1 是 JDK8 项目，不要用 21）
yum install -y java-1.8.0-openjdk-headless      # 1.8.0_412

# 2. 本机构建 admin（源码在 D:\JAVA_Code\xxl-job-2.4.1）
cd /d/JAVA_Code/xxl-job-2.4.1
JAVA_HOME=/d/java8/jdk mvn -DskipTests -pl xxl-job-admin -am package   # 需联网，离线可改用 maven.aliyun 补依赖

# 3. 拷进虚拟机 + systemd 托管
scp xxl-job-admin/target/xxl-job-admin-2.4.1.jar root@192.168.100.111:/opt/lumenote/xxl-job/
# 单元文件：/etc/systemd/system/xxl-job-admin.service（Requires=docker.service，Restart=on-failure）
systemctl enable --now xxl-job-admin
firewall-cmd --permanent --add-port=7777/tcp && firewall-cmd --reload
```

访问 `http://192.168.100.111:7777/xxl-job-admin`，`admin` / `123456`。

启动参数（写在 `ExecStart` 里）：`--server.port=7777`、数据源指向 `192.168.100.111:3306/xxl_job`、
`--xxl.job.accessToken=default_token`（必须与 `application-dev.yaml` 里一致）。

### 执行器与任务

执行器名 `xxl-job-executor-lumenote`（= `application-dev.yaml` 的 `xxl.job.appName`），端口 `10001`，
admin 地址 `http://192.168.100.111:7777/xxl-job-admin`。共 **9 个 handler、9 条任务**，已全部启用：

| 任务 | handler | cron |
| --- | --- | --- |
| 创建日增量表 | `createTableJobHandler` | `0 0 23 * * ?`（建**明天**的表） |
| 删除历史表 | `deleteTableJobHandler` | `0 0 23 * * ?`（删 1–30 天前） |
| 关注数对齐 | `followingCountShardingJobHandler` | `0 0 3 * * ?`（广播分片） |
| 粉丝数 / 作品数 / 笔记点赞 / 笔记收藏 / 用户点赞 / 用户收藏 | `*CountShardingJobHandler` | `0 0 3 * * ?` |

### 三个坑（都踩过）

1. **执行器名要和 admin 里的分组名一致**。库里原来是 `xxl-job-executor-xiaohashu`，改名后必须改
   `xxl_job_group.app_name`，否则 admin 永远显示「无可用执行器」。
2. **手工往 `xxl_job_info` 插任务时 `glue_updatetime` 不能为 NULL**：admin 触发时执行
   `jobInfo.getGlueUpdatetime().getTime()`（`XxlJobTrigger.java:137`），为 NULL 直接 NPE，
   日志里只看到 `ERROR JobTriggerPoolHelper - null`，任务状态永远停在 `handle_code=0`。
   要么走控制台新建任务，要么插入时一并写 `glue_updatetime/glue_source/glue_remark`。
3. **各 handler 的日期口径不一致**：`followingCountSharding` 和 `noteCollectCountSharding` 读**当天**的
   临时表，而 `fans/noteLike/notePublish/userLike/userCollect` 读**昨天**
   （`LocalDate.now().minusDays(1)`）。环境刚搭好、历史上没有表时，任务会报
   `Table 'lumenote.t_data_align_*_2026xxxx_N' doesn't exist`。稳态下每天 23:00 建表能覆盖，
   但两个口径并存本身值得统一（要么都读当天，要么都读昨天）。

### 验证

```bash
# 手动触发某条任务（拿 cookie 再调接口）
curl -c /tmp/xc -d "userName=admin&password=123456&ifRemember=on" \
  http://192.168.100.111:7777/xxl-job-admin/login
curl -b /tmp/xc -d "id=1" http://192.168.100.111:7777/xxl-job-admin/jobinfo/trigger

# 看结果：trigger_code / handle_code 都应为 200
# 库名 xxl_job，表 xxl_job_log；执行器在线看 xxl_job_registry
```

---

## Canal（运行在宿主机 Windows，不在 Docker 里）

Canal 的镜像拉不下来、官方包也没有可用的 Docker 源，最终决定**在宿主机直接跑**。
虚拟机这边只需要保证它要的东西齐了：

| 需要 | 状态 |
| --- | --- |
| MySQL binlog（ROW + FULL + server_id） | ✅ 已在 `my.cnf` 里显式开启 |
| 复制账号 `canal` / `canal`（`SELECT, REPLICATION SLAVE, REPLICATION CLIENT`） | ✅ 已建好 |
| MySQL 端口可从宿主机访问 | ✅ `192.168.100.111:3306` |

宿主机 canal 的 `conf/example/instance.properties` 关键几项：

```properties
canal.instance.mysql.slaveId=2
canal.instance.master.address=192.168.100.111:3306
canal.instance.dbUsername=canal
canal.instance.dbPassword=canal
canal.instance.connectionCharset = UTF-8
canal.instance.tsdb.enable=false
canal.instance.filter.regex=lumenote\\..*
```

`search` 模块的 `canal.address` 保持 `127.0.0.1:11111` 即可——应用和 canal 都跑在 Windows 上。

### 宿主机 canal 启动要点（实测踩过）

1. **必须用 JDK 8**。`bin/startup.bat` 里是 `java ... -XX:PermSize=128m`，而 PATH 里的 `java` 可能是 JDK 21，
   会直接报 `Unrecognized VM option 'PermSize'`。本机 `JAVA_HOME=D:\java8\jdk`（1.8.0_311），显式用它启动：
   ```powershell
   Start-Process "D:\java8\jdk\bin\java.exe" -WorkingDirectory "D:\canal\canal.deployer-1.1.7\bin" -ArgumentList @(
     "-Xms128m","-Xmx512m","-Dfile.encoding=UTF-8","-DappName=otter-canal",
     "-Dlogback.configurationFile=D:\canal\canal.deployer-1.1.7\conf\logback.xml",
     "-Dcanal.conf=D:\canal\canal.deployer-1.1.7\conf\canal.properties",
     "-classpath","D:\canal\canal.deployer-1.1.7\conf;D:\canal\canal.deployer-1.1.7\lib\*",
     "com.alibaba.otter.canal.deployer.CanalLauncher")
   ```
2. **工作目录要设成 `bin`**。`conf/logback.xml` 里的日志路径是相对的 `../logs/${destination}/...`，
   从 canal 根目录启动会把日志写到上级 `D:\canal\logs`，看起来像"日志没输出"。
3. `conf/example/meta.dat` 是 binlog 位点。换 MySQL 后必须删掉（否则会从旧库的文件/位点开始），
   删了之后 canal 会从 `SHOW MASTER STATUS` 的当前位置开始，之后自动续传。

验证方式（三种，从粗到细）：

```bash
# 1. MySQL 里能看到 canal 以 Binlog Dump 身份挂着，state = "sent all binlog to replica"
SHOW PROCESSLIST;
# 2. canal 日志：find start position successfully ... the next step is binlog dump
tail -f D:\canal\canal.deployer-1.1.7\logs\example\example.log
# 3. 客户端 API：订阅 lumenote.* 后能收到 ROW 事件（INSERT/UPDATE + 列值）
```

> `extras/canal-build/Dockerfile` 和 `extras/conf/canal/` 是当时准备的容器化方案，留作备查，
> 默认不在 `docker-compose.yml` 里。

---

## ZooKeeper（Leaf 雪花模式的 workerId 分配）

`lumenote-distributed-id-generator` 用的是美团 **Leaf**：号段模式走 MySQL 的 `leaf` 库，
雪花模式走 ZooKeeper 分配 workerId。

| 项 | 值 |
| --- | --- |
| 镜像 | `zookeeper:3.5.6`（Docker Hub 官方 library 镜像），容器 `zookeeper`，单节点 |
| 端口 | `2181`（客户端。`2888/3888` 是集群内部端口，单节点不需要暴露） |
| 数据 | `/opt/lumenote/zookeeper/{data,datalog}`（bind mount，宿主机上能看到 `version-2/snapshot.0`） |
| 4lw | 已开 `ZOO_4LW_COMMANDS_WHITELIST=*`，`ruok` / `srvr` / `mntr` 可用 |

### 和 id 生成服务对接的四个点

1. **`leaf.snowflake.zk.address=192.168.100.111:2181`** —— ZK 的 znode 路径是
   `/snowflake/{leaf.name}`，当前 `leaf.name=com.sankuai.leaf.opensource.test`。
   Leaf 启动时会在该路径下注册**临时节点** `{ip}:{port}` 抢占一个 workerId（0~1023 递增找空位）。
2. **`leaf.snowflake.port` 必须等于模块的 `server.port`**（本项目是 **8085**，原来是 2222）。
   Leaf 会拿这个端口去探测已注册的 workerId 对应实例是否还活着，端口不对会导致 workerId 误判复用。
3. **`leaf.jdbc.url` 指向 `192.168.100.111:3306/leaf`**，号段表 `leaf_alloc` 已在 MySQL 里建好：
   ```sql
   CREATE TABLE leaf_alloc (
     biz_tag varchar(128) NOT NULL DEFAULT '',
     max_id bigint NOT NULL DEFAULT 1,
     step int NOT NULL,
     description varchar(256) DEFAULT NULL,
     update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     PRIMARY KEY (biz_tag)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
   ```
   已插入一条示例号段 `leaf-segment-test / step=2000`；业务要新号段就往这张表加 `biz_tag`。
4. **`leaf.segment.enable` / `leaf.snowflake.enable` 都是 true**，两种模式共用同一份 `leaf.properties`。

排查用的小命令：

```bash
docker exec zookeeper zkCli.sh -server 127.0.0.1:2181 ls -R /snowflake   # 看 workerId 注册情况
printf 'ruok' | timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/2181; printf ruok >&3; cat <&3'
```

> 该模块的 `application-dev.yaml` 是空的，配置全在 `leaf.properties` + Nacos 里，所以改地址要改 `leaf.properties`。

---

## Elasticsearch（搜索，search 模块用）

| 项 | 值 |
| --- | --- |
| 镜像 | `elasticsearch:7.3.0`（与项目 `rest-high-level-client 7.3.0` **同版本**，兼容性最好） |
| 端口 | `9200` |
| 模式 | `discovery.type=single-node`，`ES_JAVA_OPTS=-Xms512m -Xmx512m`，xpack 安全关闭 |
| 数据 | `/opt/lumenote/es/data`（bind mount 到 `/usr/share/elasticsearch/data`，宿主机上能看到 `nodes/`） |

两个必须注意的点：

1. **`vm.max_map_count` 必须 ≥ 262144**，否则容器启动时 bootstrap check 直接退出。
   `start-extras.sh` 会自动 `sysctl -w` 并写 `/etc/sysctl.d/99-elasticsearch.conf` 持久化；
   手工部署时记得：`sysctl -w vm.max_map_count=262144`。
2. **版本别乱升**：客户端是 7.3.0，服务端留在 7.x 且最好是 7.3.0。升到 8.x 会因
   `application/x-www-form-urlencoded` 之类的协议差异直接连不上。

---

## Kibana（ES 的可视化 / 监控台）

| 项 | 值 |
| --- | --- |
| 镜像 | `kibana:7.3.0`（**必须与 ES 完全同版本**，否则启动直接拒绝） |
| 端口 | `5601` → `http://192.168.100.111:5601` |
| 配置 | [extras/conf/kibana/kibana.yml](extras/conf/kibana/kibana.yml)（只读挂载到 `/usr/share/kibana/config/kibana.yml`） |
| 启动方式 | 在 `docker-compose.yml` 的 `kibana` 服务里，**profile `kibana`**（镜像拉不到时不阻塞整套 extras） |
| 资源 | `mem_limit: 1300m`，`NODE_OPTIONS=--max-old-space-size=1024` |

启动：

```bash
cd /opt/lumenote/deploy/extras
set -a; . ../mysql8/.env; set +a           # 必须：compose 里的 xxl-job 段引用了 MYSQL_PASSWORD
export MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD}"
docker compose --profile kibana up -d kibana
```

配置要点（`kibana.yml`）：

```yaml
elasticsearch.hosts: ["http://elasticsearch:9200"]   # 同 compose 网络，用服务名
xpack.security.enabled: false                        # ES 侧没开安全，这里也关，否则要求登录
xpack.monitoring.enabled: true                       # 打开 Stack Monitoring
i18n.locale: "zh-CN"                                 # 中文界面
```

### 首次启动会很慢（正常现象）

7.x 第一次启动要做 bundle 优化，日志停在：

```
Optimizing and caching bundles for graph, monitoring, ... This may take a few minutes
```

这期间 `curl /api/status` 全是 **503**，看起来像挂了其实是正常的。实测这台虚拟机（4 vCPU，
同时跑着 ES / Cassandra / Nacos）**约 6 分钟**才变 200。

**堆一定要给够**：一开始配 `--max-old-space-size=384`，优化时直接
`FATAL ERROR: CALL_AND_RETRY_LAST Allocation failed - JavaScript heap out of memory`，
崩了之后自动从头再来，表现就是「一直 503、`optimize/bundles` 里始终 0 个文件、CPU 却一直 150%+」。
改成 1024 后一次通过。判断命令：

```bash
docker logs kibana 2>&1 | grep -c "heap out of memory"       # 必须为 0
docker exec kibana sh -c 'find /usr/share/kibana/optimize/bundles -type f -size +10k | wc -l'  # 正常会到 ~50
docker stats --no-stream kibana
```

优化完成后稳态只占 ~650MB；`optimize/bundles` 已落盘，之后重启是秒级。

### 预置的索引模式

部署时已通过 API 建好两个，打开 Discover 直接能选：

| 索引模式 | 对应 ES 索引 | 说明 |
| --- | --- | --- |
| `note*` | `note` | 笔记文档（search 模块写入） |
| `user*` | `user` | 用户文档 |

```bash
curl -X POST "http://127.0.0.1:5601/api/saved_objects/index-pattern/note" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"attributes":{"title":"note*","fields":"[]"}}'
```

### 能拿来干什么

| 场景 | 位置 |
| --- | --- |
| 看索引 / 文档 / 字段 | **Discover**（先建 index pattern，如 `note*`、`user*`） |
| 看 ES 健康、堆、分片、请求量 | **Stack Monitoring** |
| 直接跑 DSL / 调 ES 接口 | **Dev Tools** |
| 排查 search 模块同步（Canal → ES） | Discover 里按 `update_time` 排序看最新文档 |

---

## MinIO（对象存储，oss 模块用）

| 项 | 值 |
| --- | --- |
| 镜像 | 官方 `minio/minio` 已从 Docker Hub 撤下，本机用的是镜像站转存：`swr.cn-north-4.myhuaweicloud.com/ddn-k8s/quay.io/minio/minio:RELEASE.2025-07-23T15-54-02Z`（`.env` 的 `MINIO_IMAGE` 可换） |
| 端口 | `9000` S3 API、`9001` 控制台 |
| 账号 | `deepsea` / `12345678`（MinIO 要求密码 ≥ 8 位，所以不能用 `deepsea`） |
| 桶 | `deepsea-lumenote`（代码 `FileServiceImpl.BUCKET_NAME` 写死） |
| 数据 | `/opt/lumenote/minio/data`（bind mount，桶在数据目录里就是一个子目录） |

两个必须注意的点：

1. **桶不会自动创建**：`MinioFileStrategy.uploadFile` 直接 `putObject`，之前的代码里没有 `makeBucket`，
   所以桶必须预先存在，否则第一次上传就报 `NoSuchBucket`。建桶两种方式：
   - 控制台 `http://虚拟机IP:9001` → 登录 → Buckets → Create Bucket → `deepsea-lumenote`
   - 或 `mc mb local/deepsea-lumenote`（需要 `minio/mc` 镜像）
2. **密码长度**：MinIO 的 root 密码最少 8 位，项目原来默认的 `deepsea`（7 位）用不了，
   oss 的 dev 配置里 secretKey 默认值已改成 `12345678`。

---

## RabbitMQ（消息队列，note 模块用）

| 项 | 值 |
| --- | --- |
| 镜像 | `rabbitmq:3.13-management-alpine`，容器 `rabbitmq`，`restart: unless-stopped` |
| 端口 | `5672` AMQP、`15672` 管理后台（`http://虚拟机IP:15672`） |
| vhost | `/lumenote`（对应 note 配置的 `virtual-host`） |
| 账号 | 业务 `deepsea` / `1234`；管理员 `admin` / `admin123` |
| 数据 | `/opt/lumenote/rabbitmq/data`（bind mount，含 `.erlang.cookie` 与节点元数据） |

脚本：[start-rabbitmq.sh](rabbitmq/start-rabbitmq.sh)（起容器 + 幂等创建 vhost/用户/权限）、[check.sh](rabbitmq/check.sh)。

三个必须注意的点：

1. **`hostname` 必须固定**（compose 里 `hostname: rabbitmq`）。节点名是 `rabbit@<hostname>` 且会写进数据目录，
   容器重建后主机名变了会报 `Cannot change node name` 直接起不来。
2. **就绪判断用 `rabbitmq-diagnostics -q check_running`**，不要用 `ping`——`ping` 只证明 Erlang 节点可达，
   此时 `rabbit` 应用可能还没起来，紧接着 `rabbitmqctl add_vhost` 会报
   `this command requires the 'rabbit' app to be running`。
3. **管理 API 里的 vhost 要 URL 编码**：vhost 名带前导斜杠，路径写 `/api/queues/%2Flumenote/...`；
   写成 `/api/queues/lumenote/...` 会返回 `vhost_not_found`。

另外 `rabbitmqctl -q list_users` **不能带列名参数**（3.13 会报 `too many arguments`），取第一列即可。

---

## Nacos（注册中心 + 配置中心）

版本按项目依赖选：`spring-cloud-alibaba 2023.0.3.4` → `nacos-client 2.4.x`，服务端走 **2.x 线（v2.4.3）**，
不用 3.x（3.x 表结构已改名 `tenant_id`，与 2.x 的 `tenant` 不兼容）。

> 升级说明：项目已从 Spring Boot `3.0.2` 升到 **`3.4.0`**（为了兼容 Spring AI 1.0）。
> 客户端随之升到 nacos-client 2.4.x，与服务端 2.4.3 完全对齐。

| 项 | 值 |
| --- | --- |
| 镜像 | `nacos/nacos-server:v2.4.3`，`MODE=standalone` |
| 端口 | `8848` 控制台/OpenAPI、`9848` 客户端 gRPC、`9849` 服务端间 gRPC |
| 存储 | MySQL 库 `nacos_config`（不用内置 Derby），12 张表由镜像里的 `mysql-schema.sql` 初始化 |
| 命名空间 | `lumenote`（`bootstrap.yaml` 里 `namespace: lumenote` 指的就是这个 ID） |
| 鉴权 | 开发环境关闭（`NACOS_AUTH_ENABLE=false`），生产必须打开并配置 `token.secret.key` |
| 目录 | 日志 `/opt/lumenote/nacos/logs`，数据 `/opt/lumenote/nacos/data` |

脚本：[start-nacos.sh](nacos/start-nacos.sh)（建库 + 导表 + 起容器 + 建命名空间）、[check.sh](nacos/check.sh)。

**必须暴露 9848/9849**：Nacos 2.x 客户端走 gRPC 长连接，只开 8848 会报
`Client not connected, current status: STARTING` 这类错误。

**必须公告宿主机 IP（`NACOS_SERVER_IP`）**：Nacos 2.x 会把自己的地址返回给客户端
（接口 `/nacos/v1/core/cluster/nodes`），客户端拿它 `+1000` 当 gRPC 地址。默认在容器里自检到的是
**bridge 内网 IP**，返回成这样：

```bash
$ curl -s http://192.168.100.111:8848/nacos/v1/core/cluster/nodes
{"data":[{"ip":"172.20.0.2","address":"172.20.0.2:8848",...}]}   # ❌ 外部永远连不上 172.20.0.2:9848
```

所以 `docker-compose.yml` 里显式设置了 `NACOS_SERVER_IP: ${NACOS_SERVER_IP:-192.168.100.111}`
（镜像会把它转成 `-Dnacos.inetutils.ip-address=`）。修复后：

```bash
$ curl -s http://192.168.100.111:8848/nacos/v1/core/cluster/nodes
{"data":[{"ip":"192.168.100.111","address":"192.168.100.111:8848",...}]}   # ✅
# 客户端日志：grpc client connection server:192.168.100.111 ip,serverPort:9848
```

换机器/换网段时记得同步改 `deploy/nacos/.env` 里的 `NACOS_SERVER_IP`，否则又会被解析成内网 IP。

### 从已有环境迁移配置

把别的 Nacos（比如 WSL 里那套 3.x）的配置搬过来时，不要直接拷 `nacos_config` 库——两边表结构不同。
用 OpenAPI 逐条发布最稳，内容可以直接流过去，不用落地文件：

```bash
# 注意 --default-character-set=utf8mb4：客户端默认 gbk 会把中文转成 GBK 字节，
# 发布后 Nacos 按 UTF-8 解码失败，内容变成一串 �（U+FFFD）
mysql -h127.0.0.1 -uroot -p'密码' -N -B -r --default-character-set=utf8mb4 \
  -e "SELECT content FROM nacos_config.config_info WHERE data_id='xxx'" \
| ssh root@<虚拟机> "curl -s -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
    -d 'dataId=<新dataId>' -d 'group=DEFAULT_GROUP' -d 'tenant=lumenote' -d 'type=yaml' \
    --data-urlencode content@-"
```

dataId 要跟 `spring.application.name` + profile 对齐（`lumenote-auth-dev.yaml` 这种），
否则服务端不会加载——本项目沿用旧项目名 `xiaohashu-*` 的配置，迁移时统一改名成 `lumenote-*`。

---

## 本机实例实况（2026-10-31 实际部署记录）

| 项 | 实际值 |
| --- | --- |
| 虚拟机 | `D:\virtual_machine\lumenote\lumenote.vmx`，CentOS 7.5.1804，6C/10G，NAT `192.168.100.111` |
| Docker | `26.1.4`（el7 仓库当前最新），overlay2，cgroup driver = systemd，开机自启 |
| MySQL | `8.0.46`，容器名 `mysql8`，监听 `0.0.0.0:3306`；utf8mb4 / utf8mb4_0900_ai_ci / +08:00 |
| Nacos | `2.4.3`，容器名 `nacos`，`0.0.0.0:8848/9848/9849`；控制台 `http://192.168.100.111:8848/nacos`（免登录）；命名空间 `lumenote` |
| Redis | `7.4.12`，容器名 `redis`，`0.0.0.0:6379`，无密码；AOF 持久化到 `/opt/lumenote/redis/data` |
| RabbitMQ | `3.13.7`，容器名 `rabbitmq`，`5672`/`15672`，vhost `/lumenote`，账号 `deepsea`/`1234` |
| Cassandra | `4.1`，容器名 `cassandra`，`0.0.0.0:9042`，keyspace `lumenote`，数据 `/opt/lumenote/cassandra/data` |
| MinIO | `RELEASE.2025-07-23`，容器名 `minio`，`9000`/`9001`，`deepsea`/`12345678`，桶 `deepsea-lumenote`，数据 `/opt/lumenote/minio/data` |
| Elasticsearch | `7.3.0`，容器名 `elasticsearch`，`0.0.0.0:9200`，single-node，数据 `/opt/lumenote/es/data` |
| ZooKeeper | `3.5.6`，容器名 `zookeeper`，`0.0.0.0:2181`，standalone，数据 `/opt/lumenote/zookeeper/{data,datalog}` |
| Leaf 号段库 | MySQL `leaf` 库 + `leaf_alloc` 表（示例号段 `leaf-segment-test`，step 2000） |
| Nacos 配置 | 5 条：`lumenote-auth`、`lumenote-auth-dev.yaml`、`lumenote-data-align-dev.yaml`、`lumenote-oss-dev.yaml`、`lumenote-user-relation-dev.yaml`（源：WSL 里 Nacos 3.x 的 `xiaohashu` 命名空间，已按 UTF-8 原样迁移） |
| 数据 | `/opt/lumenote/mysql8/data`（bind mount，约 200M，已生成 `ibdata1`、`mysql/`、`lumenote/`） |
| 部署产物 | 虚拟机内 `/opt/lumenote/deploy/`（`docker/`、`mysql8/`） |
| 软件源 | 基础源 → 阿里云 `centos-vault/7.9.2009`（原源备份在 `/etc/yum.repos.d/backup-*`）；docker-ce → 阿里云镜像 |
| 账号 | `root` / `1234`（2026-10-31 按使用者要求改成简单口令，DataGrip 直连用），业务账号 `lumenote` / `lumenote@123456` 保留 |
| 为部署做的系统变更 | SELinux Enforcing → permissive；firewalld 放行 3306/tcp；`/etc/docker/daemon.json` 加镜像加速 |

---

## 版本升级记录（Spring Boot 3.4.0 + Spring AI 1.0）

目标：让 Spring AI 1.0 可用（它要求 Spring Boot ≥ 3.4）。2026-11-01 完成全量升级并回归。

### 版本矩阵

| 组件 | 升级前 | 升级后 | 说明 |
| --- | --- | --- | --- |
| Spring Boot | 3.0.2 | **3.4.0** | Spring AI 1.0 的门槛 |
| Spring Cloud | 2022.0.0 | **2024.0.0** | 与 Boot 3.4 官方对应 |
| Spring Cloud Alibaba | 2022.0.0.0 | **2023.0.3.4** | SCA **没有 2024.0 线**，见下 |
| nacos-client | 2.2.x | 2.4.x | 与服务端 2.4.3 对齐 |
| Lombok | 1.18.30 | 1.18.36 | 跟随 Boot 3.4 |
| Jackson | 2.16.1 | 2.18.2 | 跟随 Boot 3.4，避免和 SCA 冲突 |
| Druid | 1.2.23 | 1.2.24 | Boot 3.4 兼容版本 |
| Sa-Token | 1.38.0 | 1.39.0 | Boot 3.4 兼容版本 |
| MyBatis starter | 3.0.2（误绑） | **3.0.4** | 见坑 2 |
| Spring AI | 1.0.0（不可用） | **1.0.0（可用）** | Boot 3.4 起可正常引入 |

### SCA 为什么是 2023.0.3.4 而不是 2024.x

Spring Cloud Alibaba 的发布线是：`2023.0.3.x`（对应 Spring Cloud 2023.0 / Boot 3.3）→
`2025.0.0.0`（对应 Spring Cloud 2025.0 / Boot 3.5），**中间没有 2024.0 线**。
所以 Boot 3.4 只能用 `2023.0.3.4`，属于"跨一个小版本"使用；本项目 12 个模块实测可用
（注册发现、配置中心、Feign 全通）。如果以后 SCA 出了 2024/2025 稳定线，再跟着升。

### 四个坑（都踩过并修复）

**1. `-parameters` 编译参数（最隐蔽）**

Spring Framework 6.2 移除了基于字节码的参数名发现（`LocalVariableTableParameterNameDiscoverer`），
项目原先没开 `-parameters`，导致所有"按参数名注入"失效：

```
Parameter 0 of method followMessageBinding ... required a single bean, but 21 were found
```

`RabbitMQConfig` 里有 21 个 `Queue` Bean，靠参数名 `followMessageQueue` 匹配以前是隐式的，
升级后直接报 no unique bean。修复：root pom 的 `maven-compiler-plugin` 加 `<parameters>true</parameters>`
（Spring Boot 官方父 pom 默认就带）。

**2. `mybatis-spring-boot-starter` 版本被误绑**

原 pom 写的是 `<version>${spring-boot.version}</version>`，升 Boot 后会变成 `3.4.0`——这个版本不存在。
修复：新增独立属性 `mybatis-spring-boot.version=3.0.4`。

**3. `feign-form-spring` 失去版本管理**

Spring Cloud 2024 的 BOM 不再管理 `io.github.openfeign.form:feign-form-spring`，oss 模块因此构建失败
（`'dependencies.dependency.version' ... is missing`）。修复：root pom 的 dependencyManagement 里显式给版本（3.8.0）。

**4. 配置中心地址必须显式配**

引入 `nacos-config` 的模块若 bootstrap 只配了 `discovery`，配置中心会退到默认 `127.0.0.1:8848`
（gRPC `127.0.0.1:9848`）→ 启动直接 `Connection refused`。新建模块时务必把 `config` 段一起写上。

### 升级后回归结果（2026-11-01）

| 项 | 结果 |
| --- | --- |
| `mvn clean install` | BUILD SUCCESS（12 个模块） |
| 11 个服务 + gateway 启动 | 全部 `Started` 并注册 Nacos（note 那一次失败是因为 8086 被已运行实例占用，非版本问题） |
| 网关路由 | `POST :8000/search/note/hot` → 正常返回 ES 结果 |
| Feign 链路 | `user:8082` → `idgen:8085` 注册用户成功（返回号段 ID） |
| 配置中心 | auth 正常拉取 `lumenote-auth-dev.yaml` |
| 中间件 | 无需改动（MySQL/Redis/MQ/Nacos/ES/Cassandra/MinIO/ZK 版本均兼容） |

> AIGC 模块只需把 `spring-ai-*` 依赖补上即可开始接模型（BOM 已随 Boot 3.4 变得可用）。

### 配置中心（Nacos）在升级后失效的修复

升级到 Boot 3.4 + Cloud 2024 + SCA 2023.0.3.4 后，**`bootstrap.yaml` 里的 `spring.cloud.nacos.config` 不再生效**：
配置客户端会正常初始化（`~/logs/nacos/config.log` 里有 `NacosConfigService` 初始化），
但**从不拉取任何 dataId**，服务实际跑的全是本地 `application.yaml` 的值。

验证方法（决定性）：把 Nacos 上 `lumenote-oss-dev.yaml` 加一行 `server.port: 8089`，
服务仍然监听本地的 `8081` → 说明远程配置没生效。

**修复**：改用 SCA 2023 推荐的新写法 `spring.config.import`，加在各模块的 `application.yaml`：

```yaml
spring:
  config:
    import:
      - optional:nacos:lumenote-oss-dev.yaml?group=DEFAULT_GROUP&refreshEnabled=true
  cloud:
    nacos:
      config:
        server-addr: 192.168.100.111:8848
        namespace: lumenote
        group: DEFAULT_GROUP
        file-extension: yaml
        refresh-enabled: true
```

- `optional:` 保证 Nacos 上没这条 dataId 时也能启动（如 `lumenote-aigc-dev.yaml` 目前就不存在）。
- 已改的模块：`auth`、`oss`、`user-relation`、`data-align`、`aigc`（其余模块本来就不引 nacos-config）。
- 验证：Nacos 服务端 `logs/config-trace.log` 能看到 5 个 dataId 的拉取记录；改 Nacos 值重启能覆盖本地。

### MinIO 桶策略（上传 URL 可访问）

桶默认私有，上传接口返回的是裸 URL，直接访问 403。已设置匿名只读：

```json
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
 "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::deepsea-lumenote/*"]}]}
```

设置后 `GET /deepsea-lumenote/<object>` 返回 200。脚本：`/tmp/minio_policy.py`（SigV4 手写签名，VM 上 python3 执行）。

### Cassandra 表（kv 模块）

`kv` 启动正常但调用报 `table note_content does not exist` —— keyspace 建了、表没建。补：

```cql
CREATE KEYSPACE IF NOT EXISTS lumenote WITH replication = {'class':'SimpleStrategy','replication_factor':1};
CREATE TABLE IF NOT EXISTS lumenote.note_content ( id uuid PRIMARY KEY, content text );
```

---

## AIGC 智能助手（DeepSeek + Spring AI 1.0）

前端入口 **`#/assistant`**（导航栏「小拾助手」），链路：

```
浏览器 → server.js 代理 /aigc → 网关 8000（Sa-Token 鉴权）→ aigc:8088 → DeepSeek API
```

### 后端接口

| 接口 | 说明 |
| --- | --- |
| `POST /aigc/chat` | 一次性对话，返回 `{sessionId, reply}` |
| `POST /aigc/chat/stream` | 流式对话，SSE（`data:` 逐 token，出错时 `event:error`） |
| `POST /aigc/chat/clear` | 清空该会话的 Redis 记忆 |

- 会话记忆：Redis `aigc:chat:history:{userId}:{sessionId}`，只留最近 `aigc.history-turns` 轮，TTL `aigc.history-ttl`
- 限流：Redis `aigc:chat:rate:{userId}`，每分钟 `aigc.rate-limit-per-minute` 次
- 鉴权：网关默认拦截 `/aigc/**`，未登录直接 401（不在白名单里）

### 依赖与版本

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-deepseek</artifactId>
</dependency>
```

版本由父 pom 的 `spring-ai-bom:1.0.0` 管理（1.0.0 适配 Spring Boot 3.4.x；Spring AI 2.0.x 需要 Boot 4.1，暂不升级）。

### 配置

`lumenote-aigc/src/main/resources/config/application-dev.yaml`：

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:sk-not-configured}   # 只从环境变量读，绝不入库
      base-url: https://api.deepseek.com
      chat:
        model: ${AIGC_MODEL:deepseek-flash}           # 也可 deepseek-v4-pro
        temperature: 0.7
        completions-path: /chat/completions
aigc:
  history-turns: 8
  history-ttl: 3600
  rate-limit-per-minute: 20
```

要点：

1. **模型名必须显式写**：Spring AI 1.0.0 的 `DeepSeekApi.ChatModel` 枚举只有 `deepseek-chat` / `deepseek-reasoner`（已退役），
   而 DeepSeek 现在的主模型是 `deepseek-flash` / `deepseek-v4-pro`。配置是字符串，不受枚举限制。
2. **没有 API Key 也能启动**：`ChatClient` 用 `ObjectProvider<DeepSeekChatModel>` 懒取，拿不到就返回 null，
   接口会返回 `AIGC-20001` 的友好提示，不会让整个服务起不来。
   （踩坑记录：一开始用 `@ConditionalOnBean(DeepSeekChatModel.class)`，用户配置先于自动配置求值，
   条件永远不成立，ChatClient 永远建不出来。）
3. `DEEPSEEK_API_KEY` 是**用户级环境变量**时，已运行的终端/IDE 看不到，需要重启 IDE 或从注册表读取后再传给子进程。

### 已验证（2026-11-01）

| 项 | 结果 |
| --- | --- |
| 真实调用（直连 8088） | 返回「拾光笔记是一个记录生活灵感、发现同好的图文笔记社区。」 |
| 流式 SSE | 逐 token 下发（`data:1`、`data:,`…） |
| 经网关 + 登录态 | 200 + 真实文案；未带 token 返回 401 |
| 多轮上下文 | 追问「第 2 个标题再活泼一点」能正确接上文 |
| 清空对话 | 前后端记忆同时清空，回到空态 |

### 函数调用（Function Calling）

助手已接入两个**只读**工具，让模型能真正读到用户的笔记：

| 工具 | 作用 | 数据来源 |
| --- | --- | --- |
| `searchMyNotes(keyword, limit)` | 搜当前用户自己的笔记（标题模糊匹配，关键词为空则取最近 5 篇） | MySQL `t_note` |
| `getNoteDetail(noteId)` | 取单篇笔记的标题/话题/正文/图片，用于改写、总结 | MySQL `t_note` + Cassandra（经 kv 服务 Feign） |

实现要点：

- `NoteTools` 用 `@Tool` / `@ToolParam` 声明，`ChatClientConfig` 里 `.defaultTools(noteTools)` 全局注册；
- **身份隔离**：工具内部只用 `LoginUserContextHolder.getUserId()`，SQL 强制带 `creator_id`，
  绝不接受模型传入的 userId（防提示词注入越权）；
- 返回给模型的都是轻量结构（`NoteBrief` / `NoteDetail` 的 record），不塞整个 `NoteDO`；
- 找不到笔记时**抛可读异常**，Spring AI 会把错误文本回给模型，模型会如实说"没找到"，而不是编造。

实测日志（以用户 3「深海」登录）：

```
工具调用 searchMyNotes, userId: 3, keyword: 咖啡, 命中: 0
工具调用 searchMyNotes, userId: 3, keyword: null, 命中: 5
工具调用 getNoteDetail, userId: 3, noteId: 2114601458199429127, 正文长度: 0
```

效果：问「帮我找一下我关于咖啡的笔记」→ 模型调搜索、0 命中、如实回答没找到；
问「把《瑞士湖边小镇》改写成文艺风」→ 模型先搜到笔记、再取正文，发现正文为空后
明确说明"拿不到内容"，并给出仅按标题写的参考版本，没有编造原文。

> ⚠️ **数据缺口**：笔记正文存在 Cassandra `lumenote.note_content`，目前是 **0 行**
> （迁移时只迁了 MySQL，历史笔记的 `content_uuid` 指向的正文从没导过来）。
> 所以"改写正文"这类操作对历史笔记暂时无效，新发布的笔记才带正文。
> 要补的话需要从旧环境的 Cassandra 导出 `note_content` 再导入。

### RedisBloom 模块（点赞/收藏依赖）

代码里点赞/收藏用布隆过滤器做「今天是否已记录」判断（`BF.ADD` / `BF.EXISTS` / `BF.RESERVE`），
**官方 redis 镜像不带这个模块**，缺了会导致点赞/收藏全报
`ERR Unknown Redis command called from script`（错误码 `NOTE-10000`）。

**镜像必须是 Debian 版（glibc）**：`redis:7.4`。不要用 `redis:7.4-alpine`——alpine 是 musl，
静态编译/glibc 链接的 `.so` 虽然偶尔能 dlopen 成功，但内存分配实现不同，不可靠。

编译（在虚拟机里，CentOS 7 + gcc 4.8 即可）：

```bash
tar xzf RedisBloom-2.2.18.tar.gz -C /root
cd /root/RedisBloom-2.2.18 && make          # 产出 redisbloom.so（约 330KB）
mkdir -p /opt/lumenote/redis/modules
cp redisbloom.so /opt/lumenote/redis/modules/
```

配置（[redis.conf](redis/conf/redis.conf) 里加一行）：

```
loadmodule /opt/redis/modules/redisbloom.so
```

compose 里挂载（[docker-compose.yml](redis/docker-compose.yml)）：

```yaml
image: redis:${REDIS_VERSION:-7.4}        # Debian(glibc)
volumes:
  - ${REDIS_MODULES_DIR:-/opt/lumenote/redis/modules}:/opt/redis/modules
```

验证：

```bash
docker exec redis redis-cli MODULE LIST      # 应有 name=bf ver=20218
docker exec redis redis-cli BF.ADD t 1       # 返回 1
docker exec redis redis-cli BF.EXISTS t 1    # 返回 1
```

> ⚠️ `MODULE LOAD` 运行时会报 `MODULE command not allowed`（需要 `enable-module-command local`），
> 所以用 `loadmodule` 写在配置文件里、随容器启动加载，不要指望热加载。

### 历史数据补迁（正文 + 图片/视频）

从旧环境（Docker Desktop 上的 `cassandra` / `minio` 容器）迁到虚拟机时，**MySQL 里的元数据迁了，但正文和对象没迁**，表现是：

- 笔记详情显示「作者暂未填写正文内容」→ 正文在 Cassandra，没导
- 头像、封面裂开 / 控制台 502 → 图片在 MinIO 的 `deepsea-xiaohashu` 桶，没迁

#### 1. 正文（Cassandra）

旧库键空间叫 `xiaohashu`（不是 `lumenote`）：

```bash
# 旧环境导出
docker exec cassandra cqlsh -e "COPY xiaohashu.note_content (id, content) TO '/tmp/nc.csv' WITH HEADER = true;"
docker cp cassandra:/tmp/nc.csv /tmp/nc.csv

# 虚拟机导入
scp /tmp/nc.csv root@192.168.100.111:/tmp/
docker cp /tmp/nc.csv cassandra:/tmp/nc.csv
docker exec cassandra cqlsh -e "COPY lumenote.note_content (id, content) FROM '/tmp/nc.csv' WITH HEADER = true;"
```

> ⚠️ **cqlsh 的 DELETE 不支持 `NOT IN`**，清理数据只能按主键逐条删。

#### 2. 图片/视频（MinIO）

MinIO 的对象在磁盘上是 `bucket/对象名/xl.meta` 的目录结构，可以直接整目录拷贝，不必用 mc 客户端：

```bash
# 旧环境打包（宿主机挂载路径 D:\docker\minio\data）
tar czf xiaohashu-bucket.tgz deepsea-xiaohashu
scp xiaohashu-bucket.tgz root@192.168.100.111:/tmp/

# 虚拟机解压到数据目录并重启
cd /opt/lumenote/minio/data && tar xzf /tmp/xiaohashu-bucket.tgz
docker restart minio
```

再给新桶加匿名只读策略（否则对象 403）：

```bash
python3 /tmp/minio_public.py deepsea-xiaohashu
curl -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9000/deepsea-xiaohashu/<某个对象>
```

#### 3. 迁完必须清缓存（否则正文仍不显示）

笔记详情有**两层缓存**，迁移前缓存的「空正文」会继续命中：

```bash
# Redis 层
docker exec redis redis-cli --scan --pattern "note:detail:*" | xargs -r -n1 docker exec redis redis-cli DEL

# 进程内 Caffeine 层：重启 note 服务
```

> 顺序很重要：先删 Redis，再重启 note。否则 note 重启后会立刻把 Redis 里的旧值再读进本地缓存。

> 已知缺陷（未修）：正文取失败（如 kv 超时）时，`content=null` 也会被写入缓存 1 小时，属缓存穿透式「脏数据」。
