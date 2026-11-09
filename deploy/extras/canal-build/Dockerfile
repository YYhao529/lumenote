# Canal deployer 本地构建镜像
# 背景：canal/canal-server 官方镜像拉不下来（Docker Hub 被墙），
# 这里用本地已有的 nacos 镜像做基底——它自带 OpenJDK 1.8.0_402，正是 Canal 1.1.7 要求的 JDK 8。
#
# 构建上下文需要包含 canal.deployer-1.1.7.tar.gz（解压后得到 canal-server/）
#   docker build -t canal/canal-server:v1.1.7 .
FROM nacos/nacos-server:v2.4.3

USER root
WORKDIR /home/admin

# 注意：canal.deployer-1.1.7.tar.gz 里没有顶层目录（直接是 bin/ conf/ lib/），
# 所以要 ADD 到 canal-server/ 下面，否则会散落在 /home/admin 里
ADD canal.deployer-1.1.7.tar.gz /home/admin/canal-server/
RUN chmod +x /home/admin/canal-server/bin/*.sh \
 && mkdir -p /home/admin/canal-server/logs

EXPOSE 11111 11112

# start.sh 是后台启动（nohup）后立即返回，容器里必须自己 hold 住前台，
# 否则主进程一退出容器就结束了
CMD ["sh", "-c", "/home/admin/canal-server/bin/startup.sh && tail -F /home/admin/canal-server/logs/canal/canal.log /home/admin/canal-server/logs/example/example.log 2>/dev/null"]
