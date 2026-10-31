package com.deepsea.lumenote.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.util.concurrent.RateLimiter;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.count.biz.domain.mapper.NoteCountDOMapper;
import com.deepsea.lumenote.count.biz.domain.mapper.UserCountDOMapper;
import com.deepsea.lumenote.count.biz.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 计数：笔记收藏数落库
 */
@Component
@Slf4j
public class CountNoteCollect2DBConsumer {

    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private UserCountDOMapper userCountDOMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // 每秒创建 5000 个令牌
    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.COUNT_COLLECT_NOTE_TO_DB_QUEUE)
    public void consume(String message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 笔记收藏数入库】, {}...", message);

        List<AggregationCountCollectUnCollectNoteMqDTO> countList=null;
        try {
            countList = JsonUtils.parseList(message, AggregationCountCollectUnCollectNoteMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(countList)) {
            // 判断数据库中 t_user_count 和 t_note_count 表，若笔记计数记录不存在，则插入；若记录已存在，则直接更新
            countList.forEach(item -> {
                Long creatorId = item.getCreatorId();
                Long noteId = item.getNoteId();
                Integer count = item.getCount();

                // 编程式事务，保证两条语句的原子性
                transactionTemplate.execute(status -> {
                    try{
                        noteCountDOMapper.insertOrUpdateCollectTotalByNoteId(count, noteId);
                        userCountDOMapper.insertOrUpdateCollectTotalByUserId(count, creatorId);
                        return true;
                    }catch (Exception e){
                        status.setRollbackOnly();
                        log.error("", e);
                    }
                    return false;
                });

            });
        }
    }

}
