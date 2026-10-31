package com.deepsea.lumenote.count.biz.consumer;

import com.alibaba.nacos.shaded.com.google.common.collect.Maps;
import com.github.phantomthief.collection.BufferTrigger;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.lumenote.count.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.count.biz.enums.FollowUnfollowTypeEnum;
import com.deepsea.lumenote.count.biz.model.dto.CountFollowUnfollowMqDTO;
import com.deepsea.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计数：粉丝数
 */
@Component
@Slf4j
public class CountFansConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(2)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();

    @RabbitListener(queues = MQConstants.COUNT_FANS_QUEUE)
    public void consume(String message) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtils.toJsonString(bodys));

        List<CountFollowUnfollowMqDTO> objList=bodys.stream()
                .map(s -> JsonUtils.parseObject(s, CountFollowUnfollowMqDTO.class))
                .toList();

        // 按目标用户进行分组
        Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = objList.stream()
                .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));

        // 按组汇总数据，统计出最终的计数
        // key 为目标用户ID, value 为最终操作的计数
        Map<Long, Integer> countMap = Maps.newHashMap();

        for (Map.Entry<Long, List<CountFollowUnfollowMqDTO>> entry : groupMap.entrySet()) {
            List<CountFollowUnfollowMqDTO> value = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : value) {
                // 获取操作类型
                Integer type = countFollowUnfollowMqDTO.getType();

                // 根据操作类型，获取对应枚举
                FollowUnfollowTypeEnum followUnfollowTypeEnum = FollowUnfollowTypeEnum.valueOf(type);

                // 若枚举为空，跳到下一次循环
                if (Objects.isNull(followUnfollowTypeEnum)) continue;

                switch (followUnfollowTypeEnum) {
                    case FOLLOW:
                        finalCount += 1;    // 如果为关注操作，粉丝数 +1
                        break;
                    case UNFOLLOW:
                        finalCount -= 1;    // 如果为取关操作，粉丝数 -1
                        break;
                }
            }
            // 将分组后统计出的最终计数，存入 countMap 中
            countMap.put(entry.getKey(), finalCount);
        }

        log.info("## 聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));

        // 更新 Redis
        countMap.forEach((k, v) -> {
            // 构造 Redis Key
            String redisKey = RedisKeyConstants.buildCountUserKey(k);
            // 判断 Redis 中 Hash 是否存在
            boolean isExisted = redisTemplate.hasKey(redisKey);

            // 若存在才会更新
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if (isExisted) {
                // 对目标用户 Hash 中的粉丝数字段进行计数操作
                redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_FANS_TOTAL, v);
            }
        });

        // 异步发送 MQ, 计数数据落库
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_FOLLOWINGS_FANS_TO_DB_EXCHANGE,
                        MQConstants.COUNT_FANS_TO_DB_ROUTING_KEY, JsonUtils.toJsonString(countMap));
                log.info("==> 【计数服务：粉丝数入库】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【计数服务：粉丝数入库】MQ 发送异常: ", e);
            }
        });

    }
}