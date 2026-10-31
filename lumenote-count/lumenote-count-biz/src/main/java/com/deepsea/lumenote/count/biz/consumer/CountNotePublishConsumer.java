package com.deepsea.lumenote.count.biz.consumer;

import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.count.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.count.biz.domain.mapper.UserCountDOMapper;
import com.deepsea.lumenote.count.biz.enums.NoteOperateEnum;
import com.deepsea.lumenote.count.biz.model.dto.NoteOperateMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 计数：笔记发布数
 */
@Component
@Slf4j
public class CountNotePublishConsumer {

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = MQConstants.COUNT_PUBLISH_NOTE_QUEUE)
    public void consume(String message) {
        log.info("==> CountNotePublishConsumer 消费了消息 {}", message);

        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(message, NoteOperateMqDTO.class);

        if (Objects.isNull(noteOperateMqDTO)) {
            log.error("NoteOperateMqDTO 为空");
            return;
        }

        // 获取操作类型
        Integer type = noteOperateMqDTO.getType();
        NoteOperateEnum noteOperateEnum = NoteOperateEnum.valueOf(type);

        if (Objects.equals(noteOperateEnum, NoteOperateEnum.PUBLISH)) {
            handleMessage(noteOperateMqDTO,1); // 笔记发布
        }else if (Objects.equals(noteOperateEnum, NoteOperateEnum.DELETE)) {
            handleMessage(noteOperateMqDTO,-1); // 笔记删除
        }
    }

    /**
     * 处理笔记操作消息
     */
    private void handleMessage(NoteOperateMqDTO noteOperateMqDTO, Integer count) {
        // 笔记发布者 ID
        Long creatorId = noteOperateMqDTO.getCreatorId();
        
        // 更新 Redis 中用户维度的计数 Hash
        String countUserKey = RedisKeyConstants.buildCountUserKey(creatorId);
        // 判断 hash 是否存在
        boolean isCountUserExisted = redisTemplate.hasKey(countUserKey);

        // 若存在才会更新
        // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
        if (isCountUserExisted){
            // 对目标用户 Hash 中的笔记发布总数，进行加减操作
            redisTemplate.opsForHash().increment(countUserKey, RedisKeyConstants.FIELD_NOTE_TOTAL,count);
        }

        // 更新 t_user_count 表
        userCountDOMapper.insertOrUpdateNoteTotalByUserId(count, creatorId);
    }

}
