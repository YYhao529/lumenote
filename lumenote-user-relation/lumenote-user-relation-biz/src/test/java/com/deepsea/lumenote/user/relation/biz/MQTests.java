package com.deepsea.lumenote.user.relation.biz;

import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.lumenote.user.relation.biz.enums.FollowUnfollowTypeEnum;
import com.deepsea.lumenote.user.relation.biz.model.dto.CountFollowUnfollowMqDTO;
import com.deepsea.lumenote.user.relation.biz.model.dto.FollowUserMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;

@SpringBootTest
@Slf4j
class MQTests {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 测试：发送 1000 条 MQ
     */
    @Test
    void testBatchSendMQ() {
        for (long i = 1; i <= 10000; i++) {
            // 构建消息体 DTO
            FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                    .userId(i)
                    .followUserId(i)
                    .createTime(LocalDateTime.now())
                    .build();

            log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);

            // 发送消息
            threadPoolTaskExecutor.execute(() -> {
                try {
                    rabbitTemplate.convertAndSend(MQConstants.FOLLOW_UNFOLLOW_MESSAGE_EXCHANGE,
                            MQConstants.FOLLOW_MESSAGE_ROUTING_KEY, followUserMqDTO);
                    log.info("==> MQ 发送成功");
                } catch (AmqpException e) {
                    log.error("==> MQ 发送异常: ", e);
                }
            });
        }
    }

    /**
     * 测试：发送计数 MQ, 以统计粉丝数
     */
    @Test
    void testSendCountFollowUnfollowMQ() {
        // 循环发送 3200 条 MQ
        for (long i = 0; i < 3200; i++) {
            // 构建消息体 DTO
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(i + 1) // 关注者用户 ID
                    .targetUserId(27L) // 目标用户
                    .type(FollowUnfollowTypeEnum.FOLLOW.getCode())
                    .build();

            // 发送 MQ 通知计数服务：统计粉丝数
            threadPoolTaskExecutor.submit(() -> {
                try {
                    // 发送 MQ 通知计数服务：统计关注数
                    rabbitTemplate.convertAndSend(MQConstants.COUNT_FOLLOWINGS_FANS_EXCHANGE,
                            MQConstants.COUNT_FOLLOWINGS_ROUTING_KEY, countFollowUnfollowMqDTO);
                    log.info("==> 【计数服务：关注数】MQ 发送成功，SendResult: {}", countFollowUnfollowMqDTO);
                } catch (AmqpException e) {
                    log.error("==> 【计数服务：关注数】MQ 发送异常: ", e);
                }

                try {
                    // 发送 MQ 通知计数服务：统计粉丝数
                    rabbitTemplate.convertAndSend(MQConstants.COUNT_FOLLOWINGS_FANS_EXCHANGE,
                            MQConstants.COUNT_FANS_ROUTING_KEY, countFollowUnfollowMqDTO);
                    log.info("==> 【计数服务：粉丝数】MQ 发送成功，SendResult: {}", countFollowUnfollowMqDTO);
                } catch (AmqpException e) {
                    log.error("==> 【计数服务：粉丝数】MQ 发送异常: ", e);
                }
            });
        }
    }



}
