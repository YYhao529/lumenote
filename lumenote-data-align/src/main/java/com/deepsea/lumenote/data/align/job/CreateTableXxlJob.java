package com.deepsea.lumenote.data.align.job;

import com.deepsea.lumenote.data.align.constant.TableConstants;
import com.deepsea.lumenote.data.align.domain.mapper.CreateTableMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 定时任务：自动创建日增量计数变更表
 */
@Component
@RefreshScope
public class CreateTableXxlJob {

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    @Autowired
    private CreateTableMapper createTableMapper;

    @XxlJob("createTableJobHandler")
    public void createTableJobHandler() throws Exception {
        // 表后缀
        String date = LocalDate.now().plusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        XxlJobHelper.log("## 开始创建日增量数据表，日期: {}...", date);

        if (tableShards > 0) {
            for (int hashKey = 0; hashKey < tableShards; hashKey++) {
                // 表名后缀
                String tableNameSuffix = TableConstants.buildTableNameSuffix(date, hashKey);

                // 创建表
                createTableMapper.createDataAlignFollowingCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignFansCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserCollectCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignUserLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNoteLikeCountTempTable(tableNameSuffix);
                createTableMapper.createDataAlignNotePublishCountTempTable(tableNameSuffix);
            }
        }

        XxlJobHelper.log("## 结束创建日增量数据表，日期: {}...", date);

    }

}
