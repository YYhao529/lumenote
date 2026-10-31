package com.deepsea.lumenote.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.util.concurrent.RateLimiter;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.count.biz.domain.mapper.UserCountDOMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 计数：粉丝数存库
 */
@Component
@Slf4j
public class CountFans2DBConsumer {

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @RabbitListener(queues = MQConstants.COUNT_FANS_TO_DB_QUEUE)
    public void consume(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 粉丝数入库】, {}...", body);

        if (StringUtils.isBlank(body)) {
            log.warn("收到空消息，忽略");
            return;
        }

        // 解析 JSON 字符串为 Map 对象
        Map<Long, Integer> countMap = null;
        try {
            countMap = JsonUtils.parseMap(body, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(countMap)) {
            // 判断数据库中，若目标用户的记录不存在，则插入；若记录已存在，则直接更新
            countMap.forEach((k,v)->userCountDOMapper.insertOrUpdateFansTotalByUserId(v,k));
        }

    }
}
