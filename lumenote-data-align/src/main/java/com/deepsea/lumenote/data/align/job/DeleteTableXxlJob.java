package com.deepsea.lumenote.data.align.job;

import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.DeleteTableMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 定时任务：自动删除最近一个月的日增量临时表
 */
@Component
@Slf4j
public class DeleteTableXxlJob {

    @Autowired
    private DeleteTableMapper deleteTableMapper;

    @Value("${table.shards}")
    private int tableShards;

    @XxlJob("deleteTableJobHandler")
    public void deleteTableJobHandler() throws Exception {
        XxlJobHelper.log("## 开始删除最近一个月的日增量临时表");

        // 从昨天开始，往前推30天，遍历删除这30天的日增量临时表
        LocalDate now = LocalDate.now();
        for (int i = 1; i <= 30; i++) {
            String date = now.minusDays(i).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 表名后缀
            for (int hashKey = 0; hashKey < tableShards; hashKey++) {
                String tableNameSuffix= TableConstants.buildTableNameSuffix(date, hashKey);
                XxlJobHelper.log("删除表后缀: {}", tableNameSuffix);

                // 删除该日的日增量表
                deleteTableMapper.deleteDataAlignFollowingCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignFansCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignNoteCollectCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignUserCollectCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignUserLikeCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignNoteLikeCountTempTable(tableNameSuffix);
                deleteTableMapper.deleteDataAlignNotePublishCountTempTable(tableNameSuffix);
            }

        }

        XxlJobHelper.log("## 结束删除最近一个月的日增量临时表");
    }
}
