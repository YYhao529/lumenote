package com.deepsea.framework.common.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepsea.framework.common.mq.constant.MQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转换器
     */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter =
                new Jackson2JsonMessageConverter(objectMapper);
        // 2.配置自动创建消息id，用于识别不同消息，也可以在业务中基于ID判断是否是重复消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }

    // ---------------------- 关注消息、取消关注消息 ----------------------
    // 接收关注、取消关注消息交换机
    @Bean
    public DirectExchange followUnfollowMessageExchange() {
        return new DirectExchange(MQConstants.FOLLOW_UNFOLLOW_MESSAGE_EXCHANGE);
    }

    // 接收关注消息队列
    @Bean
    public Queue followMessageQueue() {
        return QueueBuilder
                .durable(MQConstants.FOLLOW_MESSAGE_QUEUE)
                .lazy()
                .build();
    }

    // 接收关注消息绑定
    @Bean
    public Binding followMessageBinding(Queue followMessageQueue, DirectExchange followUnfollowMessageExchange) {
        return BindingBuilder.bind(followMessageQueue).to(followUnfollowMessageExchange)
                .with(MQConstants.FOLLOW_MESSAGE_ROUTING_KEY);
    }

    // 接收取消关注消息队列
    @Bean
    public Queue unfollowMessageQueue() {
        return QueueBuilder
                .durable(MQConstants.UNFOLLOW_MESSAGE_QUEUE)
                .lazy()
                .build();
    }

    // 接收取消关注消息绑定
    @Bean
    public Binding unfollowMessageBinding(Queue unfollowMessageQueue, DirectExchange followUnfollowMessageExchange) {
        return BindingBuilder.bind(unfollowMessageQueue).to(followUnfollowMessageExchange)
                .with(MQConstants.UNFOLLOW_MESSAGE_ROUTING_KEY);
    }

    // ---------------------- 关注表、粉丝表计数变更消息 ----------------------
    // 接收关注表、粉丝表计数变更消息交换机
    @Bean
    public DirectExchange countFollowingsFansExchange() {
        return new DirectExchange(MQConstants.COUNT_FOLLOWINGS_FANS_EXCHANGE);
    }

    // 关注表计数变更消息队列
    @Bean
    public Queue countFollowingsQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_FOLLOWINGS_QUEUE)
                .lazy()
                .build();
    }

    // 粉丝表计数变更消息队列
    @Bean
    public Queue countFansQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_FANS_QUEUE)
                .lazy()
                .build();
    }

    // 关注表计数变更消息路由键
    @Bean
    public Binding countFollowingsBinding(Queue countFollowingsQueue, DirectExchange countFollowingsFansExchange) {
        return BindingBuilder.bind(countFollowingsQueue).to(countFollowingsFansExchange)
                .with(MQConstants.COUNT_FOLLOWINGS_ROUTING_KEY);
    }

    // 粉丝表计数变更消息路由键
    @Bean
    public Binding countFansBinding(Queue countFansQueue, DirectExchange countFollowingsFansExchange) {
        return BindingBuilder.bind(countFansQueue).to(countFollowingsFansExchange)
                .with(MQConstants.COUNT_FANS_ROUTING_KEY);
    }

    // 关注数、粉丝数对齐消息队列
    @Bean
    public Queue countFollowingsUnfollowAlignQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_FOLLOWINGS_UNFOLLOW_ALIGN_QUEUE)
                .lazy()
                .build();
    }

    // 关注数、粉丝数对齐消息绑定
    @Bean
    public Binding countFollowingsUnfollowAlignBinding(Queue countFollowingsUnfollowAlignQueue, DirectExchange countFollowingsFansExchange) {
        return BindingBuilder.bind(countFollowingsUnfollowAlignQueue).to(countFollowingsFansExchange)
                .with(MQConstants.COUNT_FOLLOWINGS_ROUTING_KEY);
    }

    // ---------------------- 缓存失效消息 ----------------------
    // 本地笔记、Redis 笔记缓存失效交换机
    @Bean
    public DirectExchange deleteNoteCacheExchange() {
        return new DirectExchange(MQConstants.NOTE_CACHE_INVALIDATE_EXCHANGE);
    }

    // 笔记缓存失效临时匿名队列
    @Bean
    public Queue deleteNoteLocalCacheQueue() {
        return new AnonymousQueue();
    }

    // 删除本地笔记缓存绑定
    @Bean
    public Binding deleteNoteLocalCacheBinding(DirectExchange deleteNoteCacheExchange,
                                               Queue deleteNoteLocalCacheQueue) {
        return BindingBuilder.bind(deleteNoteLocalCacheQueue).to(deleteNoteCacheExchange)
                .with(MQConstants.DELETE_NOTE_LOCAL_CACHE_ROUTING_KEY);
    }

    // Redis 笔记缓存失效队列
    @Bean
    public Queue deleteNoteRedisCacheQueue() {
        return QueueBuilder
                .durable(MQConstants.NOTE_REDIS_CACHE_INVALIDATE_QUEUE)
                .lazy()
                .build();
    }

    // Redis 笔记缓存失效绑定
    @Bean
    public Binding deleteNoteRedisCacheBinding(DirectExchange deleteNoteCacheExchange,
                                               Queue deleteNoteRedisCacheQueue) {
        return BindingBuilder.bind(deleteNoteRedisCacheQueue).to(deleteNoteCacheExchange)
                .with(MQConstants.DELETE_NOTE_REDIS_CACHE_ROUTING_KEY);
    }

    // ---------------------- 关注数、粉丝数落库消息 ----------------------
    // 关注数、粉丝数落库消息交换机
    @Bean
    public DirectExchange countFollowingsFans2DBExchange() {
        return new DirectExchange(MQConstants.COUNT_FOLLOWINGS_FANS_TO_DB_EXCHANGE);
    }

    // 关注数落库消息队列
    @Bean
    public Queue countFollowings2DBQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_FOLLOWINGS_TO_DB_QUEUE)
                .lazy()
                .build();
    }

    // 粉丝数落库消息队列
    @Bean
    public Queue countFans2DBQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_FANS_TO_DB_QUEUE)
                .lazy()
                .build();
    }

    // 关注数落库消息绑定
    @Bean
    public Binding followingsToDbBinding(Queue countFollowings2DBQueue, DirectExchange countFollowingsFans2DBExchange) {
        return BindingBuilder.bind(countFollowings2DBQueue).to(countFollowingsFans2DBExchange)
                .with(MQConstants.COUNT_FOLLOWINGS_TO_DB_ROUTING_KEY);
    }

    // 粉丝数落库消息绑定
    @Bean
    public Binding fansToDbBinding(Queue countFans2DBQueue, DirectExchange countFollowingsFans2DBExchange) {
        return BindingBuilder.bind(countFans2DBQueue).to(countFollowingsFans2DBExchange)
                .with(MQConstants.COUNT_FANS_TO_DB_ROUTING_KEY);
    }

    // ---------------------- 笔记点赞数统计消息 ----------------------
    // 笔记点赞数统计消息交换机
    @Bean
    public DirectExchange likeNoteCountExchange() {
        return new DirectExchange(MQConstants.COUNT_LIKE_NOTE_EXCHANGE);
    }

    // 笔记点赞数统计消息队列
    @Bean
    public Queue likeNoteCountQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_LIKE_NOTE_QUEUE)
                .lazy()
                .build();
    }

    // 笔记点赞数统计消息绑定
    @Bean
    public Binding likeNoteCountBinding(Queue likeNoteCountQueue, DirectExchange likeNoteCountExchange) {
        return BindingBuilder.bind(likeNoteCountQueue).to(likeNoteCountExchange)
                .with(MQConstants.COUNT_LIKE_NOTE_ROUTING_KEY);
    }

    // 笔记点赞数对齐消息队列
    @Bean
    public Queue countLikeNoteAlignQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_LIKE_NOTE_ALIGN_QUEUE)
                .lazy()
                .build();
    }

    // 笔记点赞数对齐消息绑定
    @Bean
    public Binding countLikeNoteAlignBinding(Queue countLikeNoteAlignQueue, DirectExchange likeNoteCountExchange) {
        return BindingBuilder.bind(countLikeNoteAlignQueue).to(likeNoteCountExchange)
                .with(MQConstants.COUNT_LIKE_NOTE_ROUTING_KEY);
    }

    // ---------------------- 笔记点赞数落库消息 ----------------------
    // 笔记点赞数落库消息交换机
    @Bean
    public DirectExchange countLikeNoteToDbExchange() {
        return new DirectExchange(MQConstants.COUNT_LIKE_NOTE_TO_DB_EXCHANGE);
    }

    // 笔记点赞数落库消息队列
    @Bean
    public Queue countLikeNoteToDbQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_LIKE_NOTE_TO_DB_QUEUE)
                .lazy()
                .build();
    }

    // 笔记点赞数落库消息绑定
    @Bean
    public Binding countLikeNoteToDbBinding(Queue countLikeNoteToDbQueue, DirectExchange countLikeNoteToDbExchange) {
        return BindingBuilder.bind(countLikeNoteToDbQueue).to(countLikeNoteToDbExchange)
                .with(MQConstants.COUNT_LIKE_NOTE_TO_DB_ROUTING_KEY);
    }

    // ---------------------- 点赞、取消点赞笔记落库消息 ----------------------
    // 点赞、取消点赞笔记落库消息交换机
    @Bean
    public DirectExchange likeUnlikeNoteToDbExchange() {
        return new DirectExchange(MQConstants.LIKE_UNLIKE_NOTE_TO_DB_EXCHANGE);
    }

    // 点赞、取消点赞笔记落库消息队列
    @Bean
    public Queue likeUnlikeNoteToDbQueue() {
        return QueueBuilder
                .durable(MQConstants.LIKE_UNLIKE_NOTE_TO_DB_QUEUE)
                .lazy()
                .build();
    }

    // 点赞、取消点赞笔记落库消息绑定
    @Bean
    public Binding likeUnlikeNoteToDbBinding(Queue likeUnlikeNoteToDbQueue, DirectExchange likeUnlikeNoteToDbExchange) {
        return BindingBuilder.bind(likeUnlikeNoteToDbQueue).to(likeUnlikeNoteToDbExchange)
                .with(MQConstants.LIKE_UNLIKE_NOTE_TO_DB_ROUTING_KEY);
    }

    // ---------------------- 收藏、取消收藏笔记落库消息 ----------------------
    // 收藏、取消收藏笔记落库消息交换机
    @Bean
    public DirectExchange collectUnCollectNoteToDbExchange() {
        return new DirectExchange(MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_EXCHANGE);
    }

    // 收藏、取消收藏笔记落库消息队列
    @Bean
    public Queue collectUnCollectNoteToDbQueue() {
        return QueueBuilder
                .durable(MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_QUEUE)
                .lazy()
                .build();
    }

    // 收藏、取消收藏笔记落库消息绑定
    @Bean
    public Binding collectUnCollectNoteToDbBinding(Queue collectUnCollectNoteToDbQueue, DirectExchange collectUnCollectNoteToDbExchange) {
        return BindingBuilder.bind(collectUnCollectNoteToDbQueue).to(collectUnCollectNoteToDbExchange)
                .with(MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_ROUTING_KEY);
    }

    // ---------------------- 笔记收藏数统计消息 ----------------------
    // 笔记收藏数统计消息交换机
    @Bean
    public DirectExchange collectNoteCountExchange() {
        return new DirectExchange(MQConstants.COUNT_COLLECT_NOTE_EXCHANGE);
    }

    // 笔记收藏数统计消息队列
    @Bean
    public Queue collectNoteCountQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_COLLECT_NOTE_QUEUE)
                .lazy()
                .build();
    }

    // 笔记收藏数统计消息绑定
    @Bean
    public Binding collectNoteCountBinding(Queue collectNoteCountQueue, DirectExchange collectNoteCountExchange) {
        return BindingBuilder.bind(collectNoteCountQueue).to(collectNoteCountExchange)
                .with(MQConstants.COUNT_COLLECT_NOTE_ROUTING_KEY);
    }

    // 笔记收藏数对齐消息队列
    @Bean
    public Queue countCollectNoteAlignQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_COLLECT_NOTE_ALIGN_QUEUE)
                .lazy()
                .build();
    }

    // 笔记收藏数对齐消息绑定
    @Bean
    public Binding countCollectNoteAlignBinding(Queue countCollectNoteAlignQueue, DirectExchange collectNoteCountExchange) {
        return BindingBuilder.bind(countCollectNoteAlignQueue).to(collectNoteCountExchange)
                .with(MQConstants.COUNT_COLLECT_NOTE_ROUTING_KEY);
    }

    // ---------------------- 笔记收藏数落库消息 ----------------------
    // 笔记收藏数落库消息交换机
    @Bean
    public DirectExchange countCollectNoteToDbExchange() {
        return new DirectExchange(MQConstants.COUNT_COLLECT_NOTE_TO_DB_EXCHANGE);
    }

    // 笔记收藏数落库消息队列
    @Bean
    public Queue countCollectNoteToDbQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_COLLECT_NOTE_TO_DB_QUEUE)
                .lazy()
                .build();
    }

    // 笔记收藏数落库消息绑定
    @Bean
    public Binding countCollectNoteToDbBinding(Queue countCollectNoteToDbQueue, DirectExchange countCollectNoteToDbExchange) {
        return BindingBuilder.bind(countCollectNoteToDbQueue).to(countCollectNoteToDbExchange)
                .with(MQConstants.COUNT_COLLECT_NOTE_TO_DB_ROUTING_KEY);
    }

    // ---------------------- 发布笔记数统计消息 ----------------------
    // 发布笔记数统计消息交换机
    @Bean
    public DirectExchange publishNoteCountExchange() {
        return new DirectExchange(MQConstants.COUNT_PUBLISH_NOTE_EXCHANGE);
    }

    // 发布笔记数统计消息队列
    @Bean
    public Queue publishNoteCountQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_PUBLISH_NOTE_QUEUE)
                .lazy()
                .build();
    }

    // 发布笔记数统计消息绑定
    @Bean
    public Binding publishNoteCountBinding(Queue publishNoteCountQueue, DirectExchange publishNoteCountExchange) {
        return BindingBuilder.bind(publishNoteCountQueue).to(publishNoteCountExchange)
                .with(MQConstants.COUNT_PUBLISH_NOTE_ROUTING_KEY);
    }

    // 发布笔记数对齐消息队列
    @Bean
    public Queue countPublishNoteAlignQueue() {
        return QueueBuilder
                .durable(MQConstants.COUNT_PUBLISH_NOTE_ALIGN_QUEUE)
                .lazy()
                .build();
    }

    // 发布笔记数对齐消息绑定
    @Bean
    public Binding countPublishNoteAlignBinding(Queue countPublishNoteAlignQueue, DirectExchange publishNoteCountExchange) {
        return BindingBuilder.bind(countPublishNoteAlignQueue).to(publishNoteCountExchange)
                .with(MQConstants.COUNT_PUBLISH_NOTE_ROUTING_KEY);
    }

    // ---------------------- 定义专门的队列和交换机，用于接收处理失败的消息 ----------------------
    // 接收失败消息交换机
    @Bean
    public DirectExchange errorMessageExchange() {
        return new DirectExchange(MQConstants.ERROR_MESSAGE_EXCHANGE);
    }

    // 接收失败消息队列
    @Bean
    public Queue errorQueue() {
        return new Queue(MQConstants.ERROR_MESSAGE_QUEUE, true);
    }

    // 接收失败消息绑定
    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange) {
        return BindingBuilder.bind(errorQueue).to(errorMessageExchange)
                .with(MQConstants.ERROR_MESSAGE_ROUTING_KEY);
    }

    // 定义一个 RepublishMessageRecoverer，关联队列和交换机
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, MQConstants.ERROR_MESSAGE_EXCHANGE,
                MQConstants.ERROR_MESSAGE_ROUTING_KEY);
    }
}