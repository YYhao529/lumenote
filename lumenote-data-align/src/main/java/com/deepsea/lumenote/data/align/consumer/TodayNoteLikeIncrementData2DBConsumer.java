package com.deepsea.lumenote.data.align.consumer;

import cn.hutool.core.util.StrUtil;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.data.align.config.TableConfig;
import com.deepsea.lumenote.data.align.constant.RedisKeyConstants;
import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.InsertMapper;
import com.deepsea.lumenote.data.align.model.dto.LikeUnlikeNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 日增量数据落库：笔记点赞、取消点赞
 */
@Component
@Slf4j
public class TodayNoteLikeIncrementData2DBConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private InsertMapper insertMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private TableConfig tableConfig;

    @RabbitListener(queues = MQConstants.COUNT_LIKE_NOTE_ALIGN_QUEUE)
    public void consume(String message) {
        log.info("## TodayNoteLikeIncrementData2DBConsumer 消费到了 MQ: {}", message);

        // 布隆过滤器判断该日增量数据是否已经记录（包括笔记点赞数和用户获得点赞数）
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = JsonUtils.parseObject(message, LikeUnlikeNoteMqDTO.class);

        if (Objects.isNull(likeUnlikeNoteMqDTO)) return;

        // 获取表总分片数
        int tableShards = tableConfig.getShards();

        Long noteCreatorId = likeUnlikeNoteMqDTO.getNoteCreatorId();  // 笔记创建者
        Long noteId = likeUnlikeNoteMqDTO.getNoteId();  // 笔记 ID

        // 当前日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 构建笔记点赞数布隆过滤器 Key
        String bloomNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeNoteIdListKey(date);

        // 构建 Lua 脚本：判断笔记点赞数是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_note_like_check.lua")));
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        long result = redisTemplate.execute(script, List.of(bloomNoteLikeListKey), noteId);

        // 如果该笔记点赞数还未记录，继续判断用户获得点赞数是否已经记录
        if (result == 0) {
            // 笔记ID对分片数取余得到分片序号
            long noteHashKey = noteId % tableShards;
            // 构建表名后缀
            String noteLikeCountTempTableNameSuffix = TableConstants.buildTableNameSuffix(date, noteHashKey);

            // 构建用户获得点赞数布隆过滤器 Key
            String bloomUserLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeUserIdListKey(date);
            // 构建 Lua 脚本：判断用户获得点赞数是否已经记录
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_like_check.lua")));
            script.setResultType(Long.class);
            // 执行 Lua 脚本，拿到返回结果
            result = redisTemplate.execute(script, List.of(bloomUserLikeListKey), noteCreatorId);

            // 用户获得点赞数：计数变更表名后缀
            String userLikeCountTempTableNameSuffix = "";
            // 说明该用户获得点赞数也还未记录
            if (result == 0) {
                // 用户ID对分片数取余得到分片序号
                long userHashKey = noteCreatorId % tableShards;
                // 构建表名后缀
                userLikeCountTempTableNameSuffix = TableConstants.buildTableNameSuffix(date, userHashKey);
            }

            // 最终用户获得点赞数：计数变更表名后缀
            String finalUserLikeCountTempTableNameSuffix = userLikeCountTempTableNameSuffix;

            // 开启事务：保证多语句的原子性
            Boolean isSuccess = transactionTemplate.execute(status -> {
                try {
                    // 插入笔记点赞数：计数变更表
                    insertMapper.insert2DataAlignNoteLikeCountTempTable(noteLikeCountTempTableNameSuffix, noteId);
                    // 如果表明后缀不为空，说明
                    if (StrUtil.isNotBlank(finalUserLikeCountTempTableNameSuffix)) {
                        // 插入用户获得的点赞数：计数变更表
                        insertMapper.insert2DataAlignUserLikeCountTempTable(finalUserLikeCountTempTableNameSuffix, noteCreatorId);
                    }
                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly(); // 标记为回滚状态
                    log.error("", e);
                }
                return false;
            });

            // 数据库写入成功后，再添加布隆过滤器中
            if (Boolean.TRUE.equals(isSuccess)) {
                RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
                redisTemplate.execute(bloomAddScript, List.of(bloomNoteLikeListKey), noteId);

                // 如果用户获得点赞数：计数变更表名后缀不为空，说明该用户获得点赞数也还未记录，需要添加到布隆过滤器中
                if (StrUtil.isNotBlank(finalUserLikeCountTempTableNameSuffix)) {
                    redisTemplate.execute(bloomAddScript, List.of(bloomUserLikeListKey), noteCreatorId);
                }
            }

        }
    }
}
