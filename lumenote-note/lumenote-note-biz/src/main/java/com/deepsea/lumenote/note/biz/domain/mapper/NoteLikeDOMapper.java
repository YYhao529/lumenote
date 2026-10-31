package com.deepsea.lumenote.note.biz.domain.mapper;

import com.deepsea.lumenote.note.biz.domain.dataobject.NoteLikeDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteLikeDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteLikeDO record);

    int insertSelective(NoteLikeDO record);

    NoteLikeDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteLikeDO record);

    int updateByPrimaryKey(NoteLikeDO record);

    /**
     * 查询笔记是否点赞
     */
    int selectCountByUserIdAndNoteId(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 查询当前用户所有点赞的笔记
     */
    List<NoteLikeDO> selectByUserId(Long userId);

    /**
     * 查询笔记是否被用户点赞
     */
    int selectNoteIsLiked(@Param("userId") Long userId, @Param("noteId") Long noteId);

    /**
     * 批量查询指定笔记中，当前用户已点赞的笔记 ID
     */
    List<Long> selectLikedNoteIds(@Param("userId") Long userId, @Param("noteIds") List<Long> noteIds);

    /**
     * 查询用户最新点赞的笔记
     */
    List<NoteLikeDO> selectLikedByUserIdAndLimit(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 统计用户点赞的笔记数量（不含「仅作者自己可见」的笔记）
     *
     * @param currentUserId 当前登录用户 ID，用于放行其自己的私密笔记
     */
    int selectCountByUserId(@Param("userId") Long userId, @Param("currentUserId") Long currentUserId);

    /**
     * 分页查询用户点赞的笔记（按点赞时间倒序）
     */
    List<NoteLikeDO> selectPageByUserId(@Param("userId") Long userId,
                                        @Param("currentUserId") Long currentUserId,
                                        @Param("start") long start,
                                        @Param("limit") long limit);

    /**
     * 新增笔记点赞记录，若已存在，则更新笔记点赞记录
     */
    int insertOrUpdate(NoteLikeDO noteLikeDO);

    /**
     * 取消点赞
     */
    int update2UnlikeByUserIdAndNoteId(NoteLikeDO noteLikeDO);
}