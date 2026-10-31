package com.deepsea.framework.common.mq.constant;

public final class MQConstants {

    private MQConstants() {
    }

    /**
     * 关系服务前缀
     */
    private static final String RELATION_SERVICE_PREFIX = "relation.";

    /**
     * 计数服务前缀
     */
    private static final String COUNT_SERVICE_PREFIX = "count.";

    /**
     * 笔记服务前缀
     */
    private static final String NOTE_SERVICE_PREFIX = "note.";

    /**
     * 数据对齐服务前缀
     */
    private static final String DATA_ALIGN_SERVICE_PREFIX = "data.align.";

    // ---------------------- 关注消息、取消关注消息 ----------------------
    // 关注、取消关注消息交换机
    public static final String FOLLOW_UNFOLLOW_MESSAGE_EXCHANGE = RELATION_SERVICE_PREFIX + "follow.unfollow.direct";

    // 关注消息队列
    public static final String FOLLOW_MESSAGE_QUEUE = RELATION_SERVICE_PREFIX + "follow.queue";

    // 取消关注消息队列
    public static final String UNFOLLOW_MESSAGE_QUEUE = RELATION_SERVICE_PREFIX + "unfollow.queue";

    // 关注消息路由键
    public static final String FOLLOW_MESSAGE_ROUTING_KEY = "follow";

    // 取消关注消息路由键
    public static final String UNFOLLOW_MESSAGE_ROUTING_KEY = "unfollow";

    // ---------------------- 关注、取关计数变更消息 ----------------------
    // 关注表、粉丝表计数变更消息交换机
    public static final String COUNT_FOLLOWINGS_FANS_EXCHANGE = COUNT_SERVICE_PREFIX + "followings.fans.direct";

    // 关注表计数变更消息队列
    public static final String COUNT_FOLLOWINGS_QUEUE = COUNT_SERVICE_PREFIX + "followings.queue";

    // 粉丝表计数变更消息队列
    public static final String COUNT_FANS_QUEUE = COUNT_SERVICE_PREFIX + "fans.queue";

    // 关注表计数变更消息路由键
    public static final String COUNT_FOLLOWINGS_ROUTING_KEY = "count.followings";

    // 粉丝表计数变更消息路由键
    public static final String COUNT_FANS_ROUTING_KEY = "count.fans";

    // 关注数、取关数对齐消息队列
    public static final String COUNT_FOLLOWINGS_UNFOLLOW_ALIGN_QUEUE = DATA_ALIGN_SERVICE_PREFIX + "followings.unfollow.queue";

    // ---------------------- 本地笔记缓存、Redis 笔记缓存失效消息 ----------------------
    // 本地笔记缓存失效交换机
    public static final String NOTE_CACHE_INVALIDATE_EXCHANGE =
            NOTE_SERVICE_PREFIX + "cache.delete.direct";

    // Redis 笔记缓存失效队列
    public static final String NOTE_REDIS_CACHE_INVALIDATE_QUEUE =
            NOTE_SERVICE_PREFIX + "redis.cache.delete.queue";

    // 本地笔记缓存失效路由键
    public static final String DELETE_NOTE_LOCAL_CACHE_ROUTING_KEY =
            NOTE_SERVICE_PREFIX + "local.cache.delete";

    // Redis 笔记缓存失效路由键
    public static final String DELETE_NOTE_REDIS_CACHE_ROUTING_KEY =
            NOTE_SERVICE_PREFIX + "redis.cache.delete";

    // ---------------------- 关注数、粉丝数落库消息 ----------------------
    // 关注数、粉丝数落库消息交换机
    public static final String COUNT_FOLLOWINGS_FANS_TO_DB_EXCHANGE = COUNT_SERVICE_PREFIX + "followings.fans.2db.direct";

    // 关注数落库消息队列
    public static final String COUNT_FOLLOWINGS_TO_DB_QUEUE = COUNT_SERVICE_PREFIX + "followings.2db.queue";

    // 粉丝数落库消息队列
    public static final String COUNT_FANS_TO_DB_QUEUE = COUNT_SERVICE_PREFIX + "fans.2db.queue";

    // 关注数落库消息路由键
    public static final String COUNT_FOLLOWINGS_TO_DB_ROUTING_KEY = "count.followings.2db";

    // 粉丝数落库消息路由键
    public static final String COUNT_FANS_TO_DB_ROUTING_KEY = "count.fans.2db";

    // ---------------------- 笔记点赞数统计消息 ----------------------
    // 笔记点赞数统计消息交换机
    public static final String COUNT_LIKE_NOTE_EXCHANGE = COUNT_SERVICE_PREFIX + "like.note.direct";

    // 笔记点赞数统计消息队列
    public static final String COUNT_LIKE_NOTE_QUEUE = COUNT_SERVICE_PREFIX + "like.note.queue";

    // 笔记点赞数统计消息路由键
    public static final String COUNT_LIKE_NOTE_ROUTING_KEY = "count.like.note";

    // 笔记点赞数对齐消息队列
    public static final String COUNT_LIKE_NOTE_ALIGN_QUEUE = DATA_ALIGN_SERVICE_PREFIX + "like.note.queue";

    // ---------------------- 笔记点赞数落库消息 ----------------------
    // 笔记点赞数落库消息交换机
    public static final String COUNT_LIKE_NOTE_TO_DB_EXCHANGE = COUNT_SERVICE_PREFIX + "like.note.2db.direct";

    // 笔记点赞数落库消息队列
    public static final String COUNT_LIKE_NOTE_TO_DB_QUEUE = COUNT_SERVICE_PREFIX + "like.note.2db.queue";

    // 笔记点赞数落库消息路由键
    public static final String COUNT_LIKE_NOTE_TO_DB_ROUTING_KEY = "count.like.note.2db";

    // ---------------------- 点赞、取消点赞笔记落库消息 ----------------------
    // 点赞、取消点赞笔记落库消息交换机
    public static final String LIKE_UNLIKE_NOTE_TO_DB_EXCHANGE = NOTE_SERVICE_PREFIX + "like.unlike.2db.direct";

    // 点赞、取消点赞笔记落库消息队列
    public static final String LIKE_UNLIKE_NOTE_TO_DB_QUEUE = NOTE_SERVICE_PREFIX + "like.unlike.2db.queue";

    // 点赞、取消点赞笔记落库消息路由键
    public static final String LIKE_UNLIKE_NOTE_TO_DB_ROUTING_KEY = "note.like.unlike.2db";

    // ---------------------- 收藏、取消收藏笔记落库消息 ----------------------
    // 收藏、取消收藏笔记落库消息交换机
    public static final String COLLECT_UNCOLLECTED_NOTE_TO_DB_EXCHANGE = NOTE_SERVICE_PREFIX + "collect.2db.direct";

    // 收藏、取消收藏笔记落库消息队列
    public static final String COLLECT_UNCOLLECTED_NOTE_TO_DB_QUEUE = NOTE_SERVICE_PREFIX + "collect.2db.queue";

    // 收藏、取消收藏笔记落库消息路由键
    public static final String COLLECT_UNCOLLECTED_NOTE_TO_DB_ROUTING_KEY = "note.collect.2db";

    // ---------------------- 笔记收藏数统计消息 ----------------------
    // 笔记收藏数统计消息交换机
    public static final String COUNT_COLLECT_NOTE_EXCHANGE = COUNT_SERVICE_PREFIX + "collect.note.direct";

    // 笔记收藏数统计消息队列
    public static final String COUNT_COLLECT_NOTE_QUEUE = COUNT_SERVICE_PREFIX + "collect.note.queue";

    // 笔记收藏数统计消息路由键
    public static final String COUNT_COLLECT_NOTE_ROUTING_KEY = "count.collect.note";

    // 笔记收藏数对齐消息队列
    public static final String COUNT_COLLECT_NOTE_ALIGN_QUEUE = DATA_ALIGN_SERVICE_PREFIX + "collect.note.queue";

    // ---------------------- 笔记收藏数落库消息 ----------------------
    // 笔记收藏数落库消息交换机
    public static final String COUNT_COLLECT_NOTE_TO_DB_EXCHANGE = COUNT_SERVICE_PREFIX + "collect.note.2db.direct";

    // 笔记收藏数落库消息队列
    public static final String COUNT_COLLECT_NOTE_TO_DB_QUEUE = COUNT_SERVICE_PREFIX + "collect.note.2db.queue";

    // 笔记收藏数落库消息路由键
    public static final String COUNT_COLLECT_NOTE_TO_DB_ROUTING_KEY = "count.collect.note.2db";

    // ---------------------- 发布笔记数统计消息 ----------------------
    // 发布笔记数统计消息交换机
    public static final String COUNT_PUBLISH_NOTE_EXCHANGE = COUNT_SERVICE_PREFIX + "publish.note.direct";

    // 发布笔记数统计消息队列
    public static final String COUNT_PUBLISH_NOTE_QUEUE = COUNT_SERVICE_PREFIX + "publish.note.queue";

    // 笔记发布统计消息路由键
    public static final String COUNT_PUBLISH_NOTE_ROUTING_KEY = "count.publish.note";

    // 笔记发布数对齐消息队列
    public static final String COUNT_PUBLISH_NOTE_ALIGN_QUEUE = DATA_ALIGN_SERVICE_PREFIX + "publish.note.queue";

    // ---------------------- 定义专门的队列和交换机，用于接收处理失败的消息 ----------------------
    // 接收失败消息交换机
    public static final String ERROR_MESSAGE_EXCHANGE = "error.direct";

    // 接收失败消息队列
    public static final String ERROR_MESSAGE_QUEUE = "error.queue";

    // 接收失败消息路由键
    public static final String ERROR_MESSAGE_ROUTING_KEY = "error";
}
