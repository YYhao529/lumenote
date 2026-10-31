package com.deepsea.lumenote.note.biz.domain.mapper;

import com.deepsea.lumenote.note.biz.domain.dataobject.NoteCollectionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteCollectionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCollectionDO record);

    int insertSelective(NoteCollectionDO record);

    NoteCollectionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCollectionDO record);

    int updateByPrimaryKey(NoteCollectionDO record);

    /**
     * 查询笔记是否已经被收藏
     */
    int selectNoteIsCollected(@Param("userId") Long userId, @Param("noteId") Long noteId );

    /**
     * 查询用户已收藏的笔记
     */
    List<NoteCollectionDO> selectByUserId(Long userId);

    /**
     * 查询用户最新收藏的笔记
     */
    List<NoteCollectionDO> selectCollectedByUserIdAndLimit(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 统计用户收藏的笔记数量（不含「仅作者自己可见」的笔记）
     *
     * @param currentUserId 当前登录用户 ID，用于放行其自己的私密笔记
     */
    int selectCountByUserId(@Param("userId") Long userId, @Param("currentUserId") Long currentUserId);

    /**
     * 分页查询用户收藏的笔记（按收藏时间倒序）
     */
    List<NoteCollectionDO> selectPageByUserId(@Param("userId") Long userId,
                                              @Param("currentUserId") Long currentUserId,
                                              @Param("start") long start,
                                              @Param("limit") long limit);

    /**
     * 新增笔记收藏记录，若已存在，则更新笔记收藏记录
     */
    int insertOrUpdate(NoteCollectionDO noteCollectionDO);

    /**
     * 取消收藏
     */
    int update2UnCollectByUserIdAndNoteId(NoteCollectionDO noteCollectionDO);
}