package com.deepsea.lumenote.data.align.consumer;

import cn.hutool.core.util.StrUtil;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.data.align.config.TableConfig;
import com.deepsea.lumenote.data.align.constant.RedisKeyConstants;
import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.InsertMapper;
import com.deepsea.lumenote.data.align.model.dto.CollectUnCollectNoteMqDTO;
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
 * 日增量数据落库：笔记收藏、取消收藏
 */
@Component
@Slf4j
public class TodayNoteCollectIncrementData2DBConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private InsertMapper insertMapper;
    @Autowired
    private TableConfig tableConfig;

    @RabbitListener(queues = MQConstants.COUNT_COLLECT_NOTE_ALIGN_QUEUE)
    public void consume(String message) {
        log.info("## TodayNoteCollectIncrementData2DBConsumer 消费到了 MQ: {}", message);

        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(message, CollectUnCollectNoteMqDTO.class);

        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;

        // 获取表总分片数
        int tableShards = tableConfig.getShards();

        // 获取当前日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 获取笔记ID
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        // 获取创作人ID
        Long noteCreatorId = collectUnCollectNoteMqDTO.getNoteCreatorId();

        // 构建布隆过滤器 KEY
        String bloomNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectNoteIdListKey(date);

        // 通过布隆过滤器校验该笔记是否已经被记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_note_collect_check.lua")));
        script.setResultType(Long.class);

        // 执行脚本，返回结果为 0 表示不存在，1 表示存在
        long result = redisTemplate.execute(script, List.of(bloomNoteCollectListKey), noteId);

        // 说明该笔记还没被记录
        if (result == 0) {
            // 用笔记ID对分片进行取余，得到分片号
            long noteHashKey = noteId % tableShards;
            // 构建表的后缀名
            String noteTableNameSuffix = TableConstants.buildTableNameSuffix(date, noteHashKey);

            // 校验创作者是否已经被记录
            // 构建布隆过滤器 KEY
            String bloomUserCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectUserIdListKey(date);

            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_collect_check.lua")));
            script.setResultType(Long.class);
            result = redisTemplate.execute(script, List.of(bloomUserCollectListKey), noteCreatorId);

            String userTableNameSuffix = "";
            if (result == 0) {
                // 说明该创作者也没被记录
                // 用创作者ID对分片进行取余，得到分片号
                long userHashKey = noteCreatorId % tableShards;
                // 构建表的后缀名
                userTableNameSuffix = TableConstants.buildTableNameSuffix(date, userHashKey);
            }
            String finalUserTableNameSuffix = userTableNameSuffix;

            // 往数据库中写入记录
            // 开启事务
            Boolean isSuccess = transactionTemplate.execute(status -> {
                try {
                    // 记录笔记
                    insertMapper.insert2DataAlignNoteCollectCountTempTable(noteTableNameSuffix, noteId);
                    if (StrUtil.isNotBlank(finalUserTableNameSuffix)) {
                        // 如果后缀名不为空，说明该创作者还未记录
                        insertMapper.insert2DataAlignUserCollectCountTempTable(finalUserTableNameSuffix, noteCreatorId);
                    }
                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly(); // 标记状态为回滚
                    log.error("", e);
                }
                return false;
            });

            // 如果添加记录成功，还得往布隆过滤器添加笔记ID以及创作者ID
            if (Boolean.TRUE.equals(isSuccess)){
                RedisScript<Long> redisScript=RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])",Long.class);
                redisTemplate.execute(redisScript,List.of(bloomNoteCollectListKey),noteId);
                if (result == 0){
                    redisTemplate.execute(redisScript,List.of(bloomUserCollectListKey),noteCreatorId);
                }
            }
        }
    }


}
