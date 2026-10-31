package com.deepsea.lumenote.data.align.domain.mapper;

public interface DeleteTableMapper {

    /**
     * 删除日增量表：关注数计数变更
     */
    void deleteDataAlignFollowingCountTempTable(String tableNameSuffix);

    /**
     * 删除日增量表：粉丝数计数变更
     */
    void deleteDataAlignFansCountTempTable(String tableNameSuffix);

    /**
     * 删除日增量表：笔记收藏数计数变更
     */
    void deleteDataAlignNoteCollectCountTempTable(String tableNameSuffix);

    /**
     * 删除日增量表：用户被收藏数计数变更
     */
    void deleteDataAlignUserCollectCountTempTable(String tableNameSuffix);

    /**
     * 删除日增量表：用户被点赞数计数变更
     */
    void deleteDataAlignUserLikeCountTempTable(String tableNameSuffix);

    /**
     * 删除日增量表：笔记点赞数计数变更
     */
    void deleteDataAlignNoteLikeCountTempTable(String tableNameSuffix);

    /**
     * 删除日增量表：笔记发布数计数变更
     */
    void deleteDataAlignNotePublishCountTempTable(String tableNameSuffix);
}
