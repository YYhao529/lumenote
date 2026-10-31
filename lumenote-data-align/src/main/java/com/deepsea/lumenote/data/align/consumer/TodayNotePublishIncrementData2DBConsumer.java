package com.deepsea.lumenote.data.align.consumer;

import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.data.align.config.TableConfig;
import com.deepsea.lumenote.data.align.constant.RedisKeyConstants;
import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.InsertMapper;
import com.deepsea.lumenote.data.align.model.dto.NoteOperateMqDTO;
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
 * 日增量数据落库：笔记发布、删除
 */
@Component
@Slf4j
public class TodayNotePublishIncrementData2DBConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private InsertMapper insertMapper;
    @Autowired
    private TableConfig tableConfig;

    @RabbitListener(queues = MQConstants.COUNT_PUBLISH_NOTE_ALIGN_QUEUE)
    public void consume(String message) {
        log.info("## TodayNotePublishIncrementData2DBConsumer 消费到了 MQ: {}", message);

        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(message, NoteOperateMqDTO.class);

        if (Objects.isNull(noteOperateMqDTO)) return;

        // 获取表分片数
        int tableShards = tableConfig.getShards();

        // 创作人ID
        Long creatorId = noteOperateMqDTO.getCreatorId();
        // 今日日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 构建布隆过滤器键名
        String bloomUserNoteOperateListKey = RedisKeyConstants.buildBloomUserNoteOperateListKey(date);

        // 布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_note_publish_check.lua")));
        script.setResultType(Long.class);

        // 执行脚本，拿到返回结果
        long result = redisTemplate.execute(script, List.of(bloomUserNoteOperateListKey), creatorId);

        if (result == 0) {
            // 结果为0，说明该创作者还未记录，此时需要把创作者 ID 插入数据库表
            // 使用创作人ID对表分片数取余获得分片号
            long userIdHashKey = creatorId % tableShards;
            // 构建表后缀名
            String tableNameSuffix = TableConstants.buildTableNameSuffix(date, userIdHashKey);

            insertMapper.insert2DataAlignUserNotePublishCountTempTable(tableNameSuffix, creatorId);

            // 插入数据成功后，把创作者 ID 加入布隆过滤器
            RedisScript<Long> redisScript = RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])", Long.class);
            redisTemplate.execute(redisScript, List.of(bloomUserNoteOperateListKey), creatorId);
        }
    }
}
