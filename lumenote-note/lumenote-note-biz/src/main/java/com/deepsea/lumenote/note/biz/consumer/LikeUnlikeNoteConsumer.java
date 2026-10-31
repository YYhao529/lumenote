package com.deepsea.lumenote.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.note.biz.domain.dataobject.NoteLikeDO;
import com.deepsea.lumenote.note.biz.domain.mapper.NoteLikeDOMapper;
import com.deepsea.lumenote.note.biz.enums.LikeUnlikeNoteTypeEnum;
import com.deepsea.lumenote.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 笔记点赞、取消点赞 MQ 消费者
 */
@Component
@Slf4j
public class LikeUnlikeNoteConsumer {

    @Autowired
    private NoteLikeDOMapper noteLikeMapper;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.LIKE_UNLIKE_NOTE_TO_DB_QUEUE)
    public void consume(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 判空
        if (Objects.isNull(likeUnlikeNoteMqDTO)) {
            log.error("笔记点赞、取消点赞 MQ 消息为空");
        }

        log.info("==> LikeUnlikeNoteConsumer 消费了消息 {}", likeUnlikeNoteMqDTO);

        // 消息类型
        Integer type = likeUnlikeNoteMqDTO.getType();

        // 判断消息类型
        if (Objects.equals(type, LikeUnlikeNoteTypeEnum.LIKE.getCode())) {
            // 点赞笔记
            handleLikeNoteMessage(likeUnlikeNoteMqDTO);
        } else if (Objects.equals(type, LikeUnlikeNoteTypeEnum.UNLIKE.getCode())) {
            // 取消点赞笔记
            handleUnlikeNoteMessage(likeUnlikeNoteMqDTO);
        } else {
            log.error("笔记点赞、取消点赞 MQ 消息类型错误");
        }
    }

    /**
     * 笔记点赞
     */
    private void handleLikeNoteMessage(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {
        // 构造 NoteLikeDO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(likeUnlikeNoteMqDTO.getUserId())
                .noteId(likeUnlikeNoteMqDTO.getNoteId())
                .createTime(likeUnlikeNoteMqDTO.getCreateTime())
                .status(likeUnlikeNoteMqDTO.getType())
                .build();

        // 添加或更新笔记点赞记录
        int count = noteLikeMapper.insertOrUpdate(noteLikeDO);

        if (count == 0) return;

        // 异步发送 MQ 消息
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_LIKE_NOTE_EXCHANGE,
                        MQConstants.COUNT_LIKE_NOTE_ROUTING_KEY, JsonUtils.toJsonString(likeUnlikeNoteMqDTO));
                log.info("==> 【计数: 笔记点赞】MQ 发送成功");
            } catch (Exception e) {
                log.error("==> 【计数: 笔记点赞】MQ 发送异常: ", e);
            }
        });
    }

    /**
     * 笔记取消点赞
     */
    private void handleUnlikeNoteMessage(LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO) {
        // 构造 NoteLikeDO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(likeUnlikeNoteMqDTO.getUserId())
                .noteId(likeUnlikeNoteMqDTO.getNoteId())
                .createTime(likeUnlikeNoteMqDTO.getCreateTime())
                .status(likeUnlikeNoteMqDTO.getType())
                .build();

        // 取消点赞：记录更新
        int count = noteLikeMapper.update2UnlikeByUserIdAndNoteId(noteLikeDO);

        if (count == 0) return;

        // 异步发送 MQ 消息
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_LIKE_NOTE_EXCHANGE,
                        MQConstants.COUNT_LIKE_NOTE_ROUTING_KEY, JsonUtils.toJsonString(likeUnlikeNoteMqDTO));
                log.info("==> 【计数: 笔记取消点赞】MQ 发送成功");
            } catch (Exception e) {
                log.error("==> 【计数: 笔记取消点赞】MQ 发送异常: ", e);
            }
        });
    }

}
