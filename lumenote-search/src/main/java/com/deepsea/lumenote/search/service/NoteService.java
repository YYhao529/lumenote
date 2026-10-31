package com.deepsea.lumenote.search.service;

import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.search.model.vo.FindHotNoteReqVO;
import com.deepsea.lumenote.search.model.vo.SearchNoteReqVO;
import com.deepsea.lumenote.search.model.vo.SearchNoteRspVO;

/**
 * 笔记搜索业务
 */
public interface NoteService {

    /**
     * 搜索笔记
     */
    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /**
     * 查询热门笔记（综合评分排名最靠前的笔记，用于发现页推荐流）
     */
    PageResponse<SearchNoteRspVO> findHotNoteList(FindHotNoteReqVO findHotNoteReqVO);

    /**
     * 重建笔记索引（全量回填，用于修复历史文档缺少封面等字段）
     */
    Response<Integer> rebuildNoteIndex();
}
