package com.deepsea.lumenote.count.biz.domain.mapper;

import com.deepsea.lumenote.count.biz.domain.dataobject.NoteCountDO;
import org.apache.ibatis.annotations.Param;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    /**
     * 根据笔记 ID 查询计数
     */
    NoteCountDO selectByNoteId(@Param("noteId") Long noteId);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);

    /**
     * 添加笔记计数记录或更新笔记点赞数
     */
    int insertOrUpdateLikeTotalByNoteId(@Param("count") Integer count,@Param("noteId") Long noteId);

    /**
     * 添加笔记收藏数记录或更新笔记收藏数
     */
    int insertOrUpdateCollectTotalByNoteId(@Param("count") Integer count,@Param("noteId") Long noteId);
}