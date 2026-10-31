package com.deepsea.lumenote.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.note.biz.domain.dataobject.NoteCollectionDO;
import com.deepsea.lumenote.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.deepsea.lumenote.note.biz.enums.CollectUnCollectNoteTypeEnum;
import com.deepsea.lumenote.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 笔记收藏、取消收藏 MQ 消费者
 */
@Component
@Slf4j
public class CollectUnCollectNoteConsumer {

    @Autowired
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_QUEUE)
    public void consume(CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        log.info("==> CollectUnCollectNoteConsumer 消费了消息 {}", collectUnCollectNoteMqDTO);

        if (Objects.isNull(collectUnCollectNoteMqDTO)) {
            log.warn("==> CollectUnCollectNoteMqDTO 为空");
            return;
        }

        // 根据操作类型处理消息
        Integer type = collectUnCollectNoteMqDTO.getType();
        if (Objects.equals(type, CollectUnCollectNoteTypeEnum.COLLECT.getCode())) {
            // 收藏笔记
            handleCollectNoteMessage(collectUnCollectNoteMqDTO);
        } else if (Objects.equals(type, CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode())) {
            // 取消收藏笔记
            handleUnCollectNoteMessage(collectUnCollectNoteMqDTO);
        }
    }

    /**
     * 收藏笔记
     */
    private void handleCollectNoteMessage(CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO) {
        // 构建 DO 实体
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(collectUnCollectNoteMqDTO.getUserId())
                .noteId(collectUnCollectNoteMqDTO.getNoteId())
                .createTime(collectUnCollectNoteMqDTO.getCreateTime())
                .status(collectUnCollectNoteMqDTO.getType())
                .build();

        // 添加或更新笔记收藏记录
        int count = noteCollectionDOMapper.insertOrUpdate(noteCollectionDO);

        // 异步发送 MQ 消息
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_COLLECT_NOTE_EXCHANGE,
                        MQConstants.COUNT_COLLECT_NOTE_ROUTING_KEY, JsonUtils.toJsonString(collectUnCollectNoteMqDTO));
                log.info("==> 【计数: 笔记收藏】MQ 发送成功");
            } catch (Exception e) {
                log.error("==> 【计数: 笔记收藏】MQ 发送异常: ", e);
            }
        });
    }

    /**
     * 取消收藏笔记
     */
    private void handleUnCollectNoteMessage(CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO) {
        // 构建 DO 实体
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(collectUnCollectNoteMqDTO.getUserId())
                .noteId(collectUnCollectNoteMqDTO.getNoteId())
                .createTime(collectUnCollectNoteMqDTO.getCreateTime())
                .status(collectUnCollectNoteMqDTO.getType())
                .build();

        // 取消收藏：记录更新
        int count = noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(noteCollectionDO);

        if (count == 0) return;

        // 异步发送 MQ 消息
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_COLLECT_NOTE_EXCHANGE,
                        MQConstants.COUNT_COLLECT_NOTE_ROUTING_KEY, JsonUtils.toJsonString(collectUnCollectNoteMqDTO));
                log.info("==> 【计数: 笔记取消收藏】MQ 发送成功");
            } catch (Exception e) {
                log.error("==> 【计数: 笔记取消收藏】MQ 发送异常: ", e);
            }
        });
    }

}
