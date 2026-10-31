package com.deepsea.lumenote.count.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.lumenote.count.biz.domain.mapper.UserCountDOMapper;
import com.deepsea.lumenote.count.biz.enums.FollowUnfollowTypeEnum;
import com.deepsea.lumenote.count.biz.model.dto.CountFollowUnfollowMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 计数：关注数入库
 */
@Component
@Slf4j
public class CountFollowing2DBConsumer {

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.COUNT_FOLLOWINGS_TO_DB_QUEUE)
    public void consume(CountFollowUnfollowMqDTO countFollowUnfollowMqDTO) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 关注数入库】, {}...", countFollowUnfollowMqDTO);

        if (Objects.isNull(countFollowUnfollowMqDTO)) return;

        // 操作类型
        Integer type = countFollowUnfollowMqDTO.getType();
        // 用户 ID
        Long userId = countFollowUnfollowMqDTO.getUserId();

        // 关注数：关注 +1， 取关 -1
        int count = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
        // 判断数据库中，若原用户的记录不存在，则插入；若记录已存在，则直接更新
        userCountDOMapper.insertOrUpdateFollowingTotalByUserId(count, userId);
    }
}
