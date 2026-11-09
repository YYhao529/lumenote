-- =====================================================================
-- Lumenote 时光笔记  MySQL 8 初始化脚本
--
-- 说明：
-- 1. 建表语句根据 MyBatis Mapper / DO 反推，字段长度和索引可按实际数据量调整。
-- 2. 日增量对账表（t_data_align_*）由 XXL-Job 的 createTableJobHandler 按天自动创建，
--    本脚本只给出表结构模板，不预建具体日期表。
-- 3. Leaf 号段库是独立 schema（leaf），见文件末尾。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `lumenote`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `lumenote`;

-- ---------------------------------------------------------------------
-- 用户与 RBAC
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user` (
    `id`             BIGINT       NOT NULL COMMENT '用户 ID（Leaf 号段）',
    `lumenote_id`    VARCHAR(32)  NOT NULL COMMENT '时光笔记号',
    `password`       VARCHAR(100)          DEFAULT NULL COMMENT 'BCrypt 密文，验证码注册用户可为空',
    `nickname`       VARCHAR(64)  NOT NULL COMMENT '昵称',
    `avatar`         VARCHAR(512)          DEFAULT NULL COMMENT '头像 URL',
    `birthday`       DATE                  DEFAULT NULL COMMENT '生日',
    `background_img` VARCHAR(512)          DEFAULT NULL COMMENT '背景图 URL',
    `phone`          VARCHAR(20)  NOT NULL COMMENT '手机号',
    `sex`            TINYINT               DEFAULT NULL COMMENT '0-女 1-男 2-保密',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    `introduction`   VARCHAR(200)          DEFAULT NULL COMMENT '个人简介',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    UNIQUE KEY `uk_user_lumenote_id` (`lumenote_id`),
    KEY `idx_user_nickname` (`nickname`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

CREATE TABLE IF NOT EXISTS `t_role` (
    `id`          BIGINT      NOT NULL,
    `role_name`   VARCHAR(64) NOT NULL,
    `role_key`    VARCHAR(64) NOT NULL,
    `status`      TINYINT     NOT NULL DEFAULT 1,
    `sort`        INT                  DEFAULT 0,
    `remark`      VARCHAR(255)         DEFAULT NULL,
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  BIT(1)      NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE IF NOT EXISTS `t_permission` (
    `id`             BIGINT       NOT NULL,
    `parent_id`      BIGINT                DEFAULT 0,
    `name`           VARCHAR(64)  NOT NULL,
    `type`           TINYINT      NOT NULL DEFAULT 1 COMMENT '1-菜单 2-按钮',
    `menu_url`       VARCHAR(255)          DEFAULT NULL,
    `menu_icon`      VARCHAR(255)          DEFAULT NULL,
    `sort`           INT                   DEFAULT 0,
    `permission_key` VARCHAR(128) NOT NULL,
    `status`         TINYINT      NOT NULL DEFAULT 1,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_key` (`permission_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限';

CREATE TABLE IF NOT EXISTS `t_user_role_rel` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL,
    `role_id`     BIGINT   NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  BIT(1)   NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色';

CREATE TABLE IF NOT EXISTS `t_role_permission_rel` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT,
    `role_id`       BIGINT   NOT NULL,
    `permission_id` BIGINT   NOT NULL,
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    BIT(1)   NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限';

-- 默认角色：id 必须为 1，注册时 RoleConstants.COMMON_USER_ROLE_ID = 1
INSERT INTO `t_role` (`id`, `role_name`, `role_key`, `status`, `sort`, `remark`)
VALUES (1, '普通用户', 'common_user', 1, 1, '注册时自动分配')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `role_key` = VALUES(`role_key`);

-- ---------------------------------------------------------------------
-- 笔记 / 话题
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_topic` (
    `id`          BIGINT       NOT NULL,
    `name`        VARCHAR(100) NOT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_topic_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '话题';

CREATE TABLE IF NOT EXISTS `t_note` (
    `id`               BIGINT       NOT NULL COMMENT '雪花 ID',
    `title`            VARCHAR(200)          DEFAULT NULL,
    `is_content_empty` BIT(1)       NOT NULL DEFAULT b'1',
    `creator_id`       BIGINT       NOT NULL,
    `topic_id`         BIGINT                DEFAULT NULL,
    `topic_name`       VARCHAR(100)          DEFAULT NULL,
    `is_top`           BIT(1)       NOT NULL DEFAULT b'0',
    `type`             TINYINT      NOT NULL COMMENT '0-图文 1-视频',
    `img_uris`         TEXT                  DEFAULT NULL COMMENT '逗号分隔的图片 URL',
    `video_uri`        VARCHAR(512)          DEFAULT NULL,
    `visible`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0-公开 1-仅自己可见',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '1-正常 2-已删除 3-下架',
    `content_uuid`     VARCHAR(64)           DEFAULT NULL COMMENT 'Cassandra note_content.id',
    PRIMARY KEY (`id`),
    KEY `idx_note_creator` (`creator_id`, `status`, `visible`),
    KEY `idx_note_topic` (`topic_id`),
    KEY `idx_note_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记';

CREATE TABLE IF NOT EXISTS `t_note_like` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL,
    `note_id`     BIGINT   NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '1-点赞 0-取消',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_like` (`user_id`, `note_id`),
    KEY `idx_note_like_note` (`note_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记点赞';

CREATE TABLE IF NOT EXISTS `t_note_collection` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL,
    `note_id`     BIGINT   NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '1-收藏 0-取消',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_collection` (`user_id`, `note_id`),
    KEY `idx_note_collection_note` (`note_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记收藏';

-- 预留：频道（当前业务代码未使用）
CREATE TABLE IF NOT EXISTS `t_channel` (
    `id`          BIGINT      NOT NULL,
    `name`        VARCHAR(64) NOT NULL,
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  BIT(1)      NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '频道（预留）';

CREATE TABLE IF NOT EXISTS `t_channel_topic_rel` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `channel_id`  BIGINT   NOT NULL,
    `topic_id`    BIGINT   NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_topic` (`channel_id`, `topic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '频道-话题（预留）';

-- ---------------------------------------------------------------------
-- 关注 / 粉丝
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_following` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT   NOT NULL COMMENT '关注者',
    `following_user_id` BIGINT   NOT NULL COMMENT '被关注者',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_following` (`user_id`, `following_user_id`),
    KEY `idx_following_target` (`following_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注关系';

CREATE TABLE IF NOT EXISTS `t_fans` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '被关注者',
    `fans_user_id` BIGINT  NOT NULL COMMENT '粉丝',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fans` (`user_id`, `fans_user_id`),
    KEY `idx_fans_user` (`fans_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '粉丝关系';

-- ---------------------------------------------------------------------
-- 计数
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_note_count` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `note_id`       BIGINT NOT NULL,
    `like_total`    BIGINT NOT NULL DEFAULT 0,
    `collect_total` BIGINT NOT NULL DEFAULT 0,
    `comment_total` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_count_note` (`note_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记维度计数';

CREATE TABLE IF NOT EXISTS `t_user_count` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT NOT NULL,
    `fans_total`      BIGINT NOT NULL DEFAULT 0,
    `following_total` BIGINT NOT NULL DEFAULT 0,
    `note_total`      BIGINT NOT NULL DEFAULT 0,
    `like_total`      BIGINT NOT NULL DEFAULT 0,
    `collect_total`   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_count_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户维度计数';

-- ---------------------------------------------------------------------
-- 数据对齐临时表模板（XXL-Job 按天自动创建，表名 = 前缀 + yyyyMMdd + _ + 分片号）
-- 分片数由 data-align 的 table.shards 控制（默认 3）。
-- ---------------------------------------------------------------------
-- CREATE TABLE `t_data_align_following_count_temp_20260101_0` (`user_id` BIGINT NOT NULL, PRIMARY KEY (`user_id`));
-- CREATE TABLE `t_data_align_fans_count_temp_20260101_0` (`user_id` BIGINT NOT NULL, PRIMARY KEY (`user_id`));
-- CREATE TABLE `t_data_align_note_collect_count_temp_20260101_0` (`note_id` BIGINT NOT NULL, PRIMARY KEY (`note_id`));
-- CREATE TABLE `t_data_align_user_collect_count_temp_20260101_0` (`user_id` BIGINT NOT NULL, PRIMARY KEY (`user_id`));
-- CREATE TABLE `t_data_align_user_like_count_temp_20260101_0` (`user_id` BIGINT NOT NULL, PRIMARY KEY (`user_id`));
-- CREATE TABLE `t_data_align_note_like_count_temp_20260101_0` (`note_id` BIGINT NOT NULL, PRIMARY KEY (`note_id`));
-- CREATE TABLE `t_data_align_note_publish_count_temp_20260101_0` (`user_id` BIGINT NOT NULL, PRIMARY KEY (`user_id`));

-- ---------------------------------------------------------------------
-- Leaf 号段库（独立 schema）
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `leaf`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `leaf`;

CREATE TABLE IF NOT EXISTS `leaf_alloc` (
    `biz_tag`     VARCHAR(128) NOT NULL,
    `max_id`      BIGINT       NOT NULL DEFAULT 1,
    `step`        INT          NOT NULL DEFAULT 1000,
    `description` VARCHAR(255)          DEFAULT NULL,
    `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`biz_tag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Leaf 号段';

INSERT INTO `leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`)
VALUES ('leaf-segment-lumenote-id', 1, 1000, '时光笔记号'),
       ('leaf-segment-user-id', 1, 1000, '用户 ID')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
