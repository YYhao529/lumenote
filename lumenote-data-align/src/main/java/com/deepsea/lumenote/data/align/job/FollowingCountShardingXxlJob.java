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
 * 定时分片广播任务：对当日发生变更的用户关注数进行对齐
 */
@Component
@Slf4j
public class FollowingCountShardingXxlJob {

    @Autowired
    private SelectMapper selectMapper;
    @Autowired
    private UpdateMapper updateMapper;
    @Autowired
    private DeleteMapper deleteMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分片广播任务
     */
    @XxlJob("followingCountShardingJobHandler")
    public void followingCountShardingJobHandler() {
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的用户关注数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 当前日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 表后缀
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);

        // 一批次 1000 条
        int batchSize = 1000;
        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;

        while (true) {
            // 1. 分批次查询 t_data_align_following_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
            List<Long> userIds = selectMapper.selectBatchFromDataAlignFollowingCountTempTable(tableNameSuffix, batchSize);

            // 如果记录为空，则跳出循环
            if (CollUtil.isEmpty(userIds)) break;

            // 2: 循环这一批发生变更的用户 ID，对 t_following 关注表执行 count(*) 操作，获取总数
            for (Long userId : userIds) {
                int followingTotal = selectMapper.selectCountFromFollowingTableByUserId(userId);

                // 3: 更新 t_user_count 表
                int count = updateMapper.updateUserFollowingTotalByUserId(userId, followingTotal);
                // 如果表更新成功，更新 Redis 缓存
                if (count > 0) {
                    String redisKey = RedisKeyConstants.buildCountUserKey(userId);
                    // 判断 hash 是否存在
                    boolean hasKey = redisTemplate.hasKey(redisKey);
                    if (hasKey){
                        // 更新 Hash 中的 Field 关注总数
                        redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_FOLLOWING_TOTAL, followingTotal);
                    }
                }
            }
            // 4. 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignFollowingCountTempTable(tableNameSuffix, userIds);

            // 当前已处理的记录数
            processedTotal += userIds.size();
        }

        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的用户关注数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
