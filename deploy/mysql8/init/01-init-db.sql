-- 只在数据卷为空（首次启动）时执行，改这里的内容需要先把 mysql8-data 卷删掉。
-- MYSQL_DATABASE=lumenote 已由镜像创建，这里只做字符集兜底和扩展库。

ALTER DATABASE `lumenote` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 需要额外库时按下面格式追加（库名要与各服务的 application-dev.yaml 对应）：
-- CREATE DATABASE IF NOT EXISTS `lumenote_other`
--   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
