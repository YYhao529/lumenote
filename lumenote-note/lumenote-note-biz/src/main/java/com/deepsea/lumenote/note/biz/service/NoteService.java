package com.deepsea.lumenote.note.biz.service;

import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.note.biz.model.vo.*;

import java.util.List;

public interface NoteService {

    /**
     * 笔记发布
     */
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    /**
     * 笔记详情
     */
    Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

    /**
     * 笔记更新
     */
    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);

    /**
     * 删除笔记
     */
    Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO);

    /**
     * 删除本地笔记缓存
     */
    void deleteNoteLocalCache(Long noteId);

    /**
     * 笔记仅对自己可见
     */
    Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO);

    /**
     * 笔记设为公开可见（从私密恢复）
     */
    Response<?> visiblePublic(UpdateNoteVisiblePublicReqVO updateNoteVisiblePublicReqVO);

    /**
     * 笔记置顶/取消置顶
     */
    Response<?> topNote(TopNoteReqVO topNoteReqVO);

    /**
     * 点赞笔记
     */
    Response<?> likeNote(LikeNoteReqVO likeNoteReqVO);

    /**
     * 取消点赞笔记
     */
    Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO);

    /**
     * 收藏笔记
     */
    Response<?> collectNote(CollectNoteReqVO collectNoteReqVO);

    /**
     * 取消收藏笔记
     */
    Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO);

    /**
     * 分页查询用户发布的笔记
     */
    PageResponse<FindNoteListItemRspVO> findPublishNoteList(FindUserNoteListReqVO findUserNoteListReqVO);

    /**
     * 分页查询用户收藏的笔记
     */
    PageResponse<FindNoteListItemRspVO> findCollectNoteList(FindUserNoteListReqVO findUserNoteListReqVO);

    /**
     * 分页查询用户点赞的笔记
     */
    PageResponse<FindNoteListItemRspVO> findLikeNoteList(FindUserNoteListReqVO findUserNoteListReqVO);

    /**
     * 批量查询当前用户已点赞的笔记 ID（返回字符串，避免前端 Long 精度丢失）
     */
    Response<List<String>> findLikedNoteIds(FindNoteLikedStatusReqVO findNoteLikedStatusReqVO);
}
