package com.deepsea.lumenote.note.biz.consumer;

import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.lumenote.note.biz.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Redis 笔记缓存失效消费者
 */
@Component
@Slf4j
public class DelayDeleteNoteRedisCacheConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = MQConstants.NOTE_REDIS_CACHE_INVALIDATE_QUEUE)
    public void deleteNoteRedisCache(Long noteId) {
        // 判空
        if (Objects.isNull(noteId)) {
            log.error("## 拒绝消费：消息体缺失笔记 ID，转入错误队列");
            throw new AmqpRejectAndDontRequeueException("消息内容为空，无法提取笔记 ID！");
        };

        log.info("## 即将删除 Redis 笔记缓存, 笔记 ID: {}", noteId);
        // 删除 Redis 缓存中的笔记数据
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);
        log.info("## 成功删除 Redis 笔记缓存, 笔记 ID: {}", noteId);
    }
}
