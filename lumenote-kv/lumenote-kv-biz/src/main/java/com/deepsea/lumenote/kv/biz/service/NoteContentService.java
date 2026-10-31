package com.deepsea.lumenote.kv.biz.service;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.kv.dto.req.AddNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.DeleteNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.FindNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.rsp.FindNoteContentRspDTO;

/**
 * 笔记内容存储业务
 */
public interface NoteContentService {

    /**
     * 添加笔记内容
     */
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

    /**
     * 查询笔记内容
     */
    Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO);

    /**
     * 删除笔记内容
     */
    Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
