package com.deepsea.lumenote.data.align.consumer;

import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.data.align.config.TableConfig;
import com.deepsea.lumenote.data.align.constant.RedisKeyConstants;
import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.InsertMapper;
import com.deepsea.lumenote.data.align.model.dto.FollowUnfollowMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 日增量数据落库：用户关注、取消关注
 */
@Component
@Slf4j
public class TodayUserFollowIncrementData2DBConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private InsertMapper insertMapper;
    @Autowired
    private TableConfig tableConfig;

    @RabbitListener(queues = MQConstants.COUNT_FOLLOWINGS_UNFOLLOW_ALIGN_QUEUE)
    public void consume(String message) {
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", message);

        FollowUnfollowMqDTO followUnfollowMqDTO = JsonUtils.parseObject(message, FollowUnfollowMqDTO.class);

        if (Objects.isNull(followUnfollowMqDTO)) return;

        // 获取表分片数
        int tableShards = tableConfig.getShards();

        Long userId = followUnfollowMqDTO.getUserId();  // 原用户
        Long targetUserId = followUnfollowMqDTO.getTargetUserId();  // 目标用户

        // 今日日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // ------------------------- 源用户的关注数变更记录 -------------------------
        // 构建布隆过滤器键
        String bloomUserFollowListKey = RedisKeyConstants.buildBloomUserFollowListKey(date);

        // 查看关注数布隆过滤器是否包含原用户
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_follow_check.lua")));
        script.setResultType(Long.class);
        long result = redisTemplate.execute(script, List.of(bloomUserFollowListKey), userId);

        RedisScript<Long> redisScript = RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])", Long.class);

        // 如果不包含，则插入该用户到日增量关注数变更表中
        if (result == 0) {
            // 用原用户ID对分片数取余，得到分片号
            long userIdHashKey = userId % tableShards;
            // 构建表的后缀名
            String tableNameSuffix = TableConstants.buildTableNameSuffix(date, userIdHashKey);

            try {
                insertMapper.insert2DataAlignUserFollowingCountTempTable(tableNameSuffix, userId);
            } catch (Exception e) {
                log.error("", e);
            }

            // 数据库写入成功后，再添加布隆过滤器中
            redisTemplate.execute(redisScript, List.of(bloomUserFollowListKey), userId);
        }

        // ------------------------- 目标用户的粉丝数变更记录 -------------------------
        // 构建布隆过滤器键
        String bloomUserFansListKey = RedisKeyConstants.buildBloomUserFansListKey(date);

        // 查看粉丝数布隆过滤器是否包含目标用户
        result = redisTemplate.execute(script, List.of(bloomUserFansListKey), targetUserId);

        // 如果不包含，则插入该用户到日增量粉丝数变更表中
        if (result == 0) {
            // 用目标用户ID对分片数取余，得到分片号
            long targetUserIdHashKey = targetUserId % tableShards;
            // 构建表的后缀名
            String tableNameSuffix = TableConstants.buildTableNameSuffix(date, targetUserIdHashKey);

            try {
                insertMapper.insert2DataAlignUserFansCountTempTable(tableNameSuffix, targetUserId);
            } catch (Exception e) {
                log.error("", e);
            }

            // 数据库写入成功后，再添加布隆过滤器中
            redisTemplate.execute(redisScript, List.of(bloomUserFansListKey), targetUserId);
        }
    }
}
