package com.deepsea.lumenote.count.biz.consumer;

import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.count.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.count.biz.enums.FollowUnfollowTypeEnum;
import com.deepsea.lumenote.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 计数：关注数
 */
@Component
@Slf4j
public class CountFollowingConsumer {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @RabbitListener(queues = MQConstants.COUNT_FOLLOWINGS_QUEUE)
    public void consume(String message) {
        log.info("## 消费到了 MQ 【计数: 关注数】, {}...", message);
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtils.parseObject(message, CountFollowUnfollowMqDTO.class);

        if (Objects.isNull(countFollowUnfollowMqDTO)) return;

        // 关注数和粉丝数计数场景不同，单个用户无法短时间内关注大量用户，所以无需聚合
        // 直接对 Redis 中的 Hash 进行 +1 或 -1 操作即可
        // 获取操作类型
        Integer type = countFollowUnfollowMqDTO.getType();
        // 原用户 ID
        Long userId = countFollowUnfollowMqDTO.getUserId();

        // 构建 Redis redisKey
        String redisKey = RedisKeyConstants.buildCountUserKey(userId);
        // 判断 Redis 中是否存在该 redisKey
        boolean isExisted = redisTemplate.hasKey(redisKey);

        // 根据操作类型，获取对应枚举
        FollowUnfollowTypeEnum followUnfollowTypeEnum = FollowUnfollowTypeEnum.valueOf(type);

        if (Objects.isNull(followUnfollowTypeEnum)) return;

        // 关注数：关注 +1， 取关 -1
        long count = Objects.equals(followUnfollowTypeEnum, FollowUnfollowTypeEnum.FOLLOW) ? 1 : -1;

        // 若存在
        if (isExisted) {
            // 对 Hash 中的 followingTotal 字段进行加减操作
            redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_FOLLOWING_TOTAL, count);
        }

        // 异步发送 MQ, 计数数据落库
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_FOLLOWINGS_FANS_TO_DB_EXCHANGE,
                        MQConstants.COUNT_FOLLOWINGS_TO_DB_ROUTING_KEY, countFollowUnfollowMqDTO);
                log.info("==> 【计数服务：关注数入库】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【计数服务：关注数入库】MQ 发送异常: ", e);
            }
        });
    }

}

