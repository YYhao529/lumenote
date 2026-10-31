package com.deepsea.lumenote.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.count.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.count.biz.enums.CollectUnCollectNoteTypeEnum;
import com.deepsea.lumenote.count.biz.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import com.deepsea.lumenote.count.biz.model.dto.CountCollectUnCollectNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Var;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 计数：笔记收藏数
 */
@Component
@Slf4j
public class CountNoteCollectConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000)  // 缓存队列的最大容量
            .batchSize(1000)    // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(2))  // 多久聚合一次
            .setConsumerEx(this::consumeMessage)  // 设置消费者方法
            .build();

    @RabbitListener(queues = MQConstants.COUNT_COLLECT_NOTE_QUEUE)
    public void consume(String message) {
        // 往缓存队列中添加数据
        bufferTrigger.enqueue(message);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记收藏数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // List<String> 转 List<CountCollectUnCollectNoteMqDTO>
        List<CountCollectUnCollectNoteMqDTO> list = bodys.stream()
                .map(jsonStr -> JsonUtils.parseObject(jsonStr, CountCollectUnCollectNoteMqDTO.class))
                .toList();

        // 根据 noteId 分组
        Map<Long, List<CountCollectUnCollectNoteMqDTO>> groupMap = list.stream().collect(Collectors.groupingBy(CountCollectUnCollectNoteMqDTO::getNoteId));

        // 按组汇总数据，统计出最终的计数
        // 最终操作的计数对象
        List<AggregationCountCollectUnCollectNoteMqDTO> countList = Lists.newArrayList();

        // 遍历 map，对收藏数进行累加
        groupMap.forEach((noteId, countCollectUnCollectNoteMqDTOS) -> {
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountCollectUnCollectNoteMqDTO countCollectUnCollectNoteMqDTO : countCollectUnCollectNoteMqDTOS) {
                // 获取操作类型
                Integer type = countCollectUnCollectNoteMqDTO.getType();

                // 根据操作类型，获取对应枚举
                CollectUnCollectNoteTypeEnum collectUnCollectNoteTypeEnum = CollectUnCollectNoteTypeEnum.valueOf(type);

                // 若枚举为空，跳到下一次循环
                if (Objects.isNull(collectUnCollectNoteTypeEnum)) continue;

                switch (collectUnCollectNoteTypeEnum) {
                    case COLLECT -> finalCount++; // 如果为收藏操作，收藏数 +1
                    case UN_COLLECT -> finalCount--; // 如果为取消收藏操作，收藏数 -1
                }
            }
            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountCollectUnCollectNoteMqDTO.builder()
                    .noteId(noteId)
                    .count(finalCount)
                    .creatorId(countCollectUnCollectNoteMqDTOS.get(0).getNoteCreatorId())
                    .build());
        });

        log.info("## 【笔记收藏数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 更新 Redis 中的笔记收藏数 Hash
        countList.forEach(item -> {
            Long noteId = item.getNoteId();
            int count = item.getCount();
            Long creatorId = item.getCreatorId();

            // 构建笔记维度计数 Key
            String countNoteKey = RedisKeyConstants.buildCountNoteKey(noteId);
            // 判断 Redis 中 Hash 是否存在
            boolean isCountNoteKeyExisted = redisTemplate.hasKey(countNoteKey);

            // 若存在才会更新
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if (isCountNoteKeyExisted) {
                // 对目标用户 Hash 中的收藏总数字段进行计数操作
                redisTemplate.opsForHash().increment(countNoteKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, count);
            }

            // 构建用户维度计数 Key
            String countUserKey = RedisKeyConstants.buildCountUserKey(creatorId);
            boolean isCountUserKeyExisted = redisTemplate.hasKey(countUserKey);
            if (isCountUserKeyExisted) {
                // 对目标用户 Hash 中的收藏总数字段进行计数操作
                redisTemplate.opsForHash().increment(countUserKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, count);
            }
        });

        // 异步发送 MQ 消息，笔记收藏数据落库
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_COLLECT_NOTE_TO_DB_EXCHANGE, MQConstants.COUNT_COLLECT_NOTE_TO_DB_ROUTING_KEY,
                        JsonUtils.toJsonString(countList));
                log.info("==> 【计数服务：笔记收藏数入库】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【计数服务：笔记收藏数入库】MQ 发送异常: ", e);
            }
        });
    }
}
