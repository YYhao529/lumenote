package com.deepsea.lumenote.note.biz.domain.mapper;

import com.deepsea.lumenote.note.biz.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteDO record);

    int insertSelective(NoteDO record);

    NoteDO selectByPrimaryKey(Long id);

    /**
     * 根据笔记 ID 查询笔记是否存在
     */
    int selectCountByNoteId(Long noteId);

    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);

    int updateVisible(NoteDO noteDO);

    int updateIsTop(NoteDO noteDO);

    /**
     * 查询笔记的发布者用户 ID
     */
    Long selectCreatorIdByNoteId(Long noteId);

    /**
     * 统计用户发布的笔记数量
     *
     * @param includePrivate 是否包含「仅自己可见」的笔记
     */
    int selectCountByCreatorId(@Param("creatorId") Long creatorId, @Param("includePrivate") boolean includePrivate);

    /**
     * 分页查询用户发布的笔记（置顶优先，按发布时间倒序）
     */
    List<NoteDO> selectPageByCreatorId(@Param("creatorId") Long creatorId,
                                       @Param("includePrivate") boolean includePrivate,
                                       @Param("start") long start,
                                       @Param("limit") long limit);

    /**
     * 根据笔记 ID 批量查询笔记
     */
    List<NoteDO> selectByIds(@Param("ids") List<Long> ids);
}