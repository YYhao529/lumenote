package com.deepsea.lumenote.data.align.job;

import cn.hutool.core.collection.CollUtil;
import com.deepsea.lumenote.data.align.constant.RedisKeyConstants;
import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.DeleteMapper;
import com.deepsea.lumenote.data.align.domain.mapper.SelectMapper;
import com.deepsea.lumenote.data.align.domain.mapper.UpdateMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时分片广播任务：对当日发生变更的笔记收藏数进行对齐
 */
@Component
@Slf4j
public class NoteCollectCountShardingXxlJob {

    @Autowired
    private SelectMapper selectMapper;
    @Autowired
    private UpdateMapper updateMapper;
    @Autowired
    private DeleteMapper deleteMapper;
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @XxlJob("noteCollectCountShardingJobHandler")
    public void noteCollectCountShardingJobHandler() throws Exception{
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的笔记收藏数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 表后缀
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);

        // 一批次 1000 条
        int batchSize = 1000;
        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;

        // 1. 分批次查询 t_data_align_note_collect_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
        while(true){
            List<Long> noteIds = selectMapper.selectBatchFromDataAlignNoteCollectCountTempTable(tableNameSuffix, batchSize);

            if (CollUtil.isEmpty(noteIds)) break;

            // 遍历 noteIds
            noteIds.forEach(noteId->{
                // 查询该笔记的总收藏数
                int collectTotal = selectMapper.selectCountFromNoteCollectionTableByNoteId(noteId);

                // 更新 t_note_count 表中的收藏数
                int count = updateMapper.updateNoteCollectTotalByNoteId(noteId,collectTotal);
                if (count>0){
                   // 如果更新成功，继续更新 redis 中该笔记的收藏数 hash 字段
                    String countNoteKey = RedisKeyConstants.buildCountNoteKey(noteId);
                    // 判断该hash是否存在
                    boolean hashKey = redisTemplate.hasKey(countNoteKey);
                    if (hashKey){
                        // 如果该哈希存在，更新收藏数
                        redisTemplate.opsForHash().put(countNoteKey,RedisKeyConstants.FIELD_COLLECT_TOTAL,collectTotal);
                    }
                }
            });
            // 4. 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignNoteCollectCountTempTable(tableNameSuffix,noteIds);

            // 当前已处理的记录数
            processedTotal += noteIds.size();
        }

        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的笔记收藏数进行对齐，共对齐记录数：{}", processedTotal);
    }

}
