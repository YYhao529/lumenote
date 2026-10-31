package com.deepsea.lumenote.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.count.biz.domain.mapper.NoteCountDOMapper;
import com.deepsea.lumenote.count.biz.domain.mapper.UserCountDOMapper;
import com.deepsea.lumenote.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 计数：笔记点赞数落库
 */
@Component
@Slf4j
public class CountNoteLike2DBConsumer {

    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private UserCountDOMapper userCountDOMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.COUNT_LIKE_NOTE_TO_DB_QUEUE)
    public void consume(String message) {
        // 从令牌桶中获取令牌
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 笔记点赞数入库】, {}...", message);

        List<AggregationCountLikeUnlikeNoteMqDTO> countList = null;
        try {
            countList = JsonUtils.parseList(message, AggregationCountLikeUnlikeNoteMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(countList)) {
            // 判断数据库中 t_user_count 和 t_note_count 表，若笔记计数记录不存在，则插入；若记录已存在，则直接更新
            countList.forEach((item) -> {
                Long creatorId = item.getCreatorId();
                Long noteId = item.getNoteId();
                Integer count = item.getCount();

                // 编程式事务，保证两条语句的原子性
                transactionTemplate.execute(status -> {
                    try {
                        noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(count, noteId);
                        userCountDOMapper.insertOrUpdateLikeTotalByUserId(count, creatorId);
                        return true;
                    } catch (Exception ex) {
                        status.setRollbackOnly(); // 标记事务为回滚
                        log.error("", ex);
                    }
                    return false;
                });
            });
        }
    }

}
