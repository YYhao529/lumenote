package com.deepsea.lumenote.kv.biz.controller;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.kv.biz.service.NoteContentService;
import com.deepsea.lumenote.kv.dto.req.AddNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.DeleteNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.FindNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.rsp.FindNoteContentRspDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记内容存储控制器
 */
@RestController
@RequestMapping("/kv")
@Slf4j
public class NoteContentController {

    @Autowired
    private NoteContentService noteContentService;

    /**
     * 添加笔记内容
     */
    @PostMapping("/note/content/add")
    public Response<?> addNoteContent(@RequestBody @Validated AddNoteContentReqDTO addNoteContentReqDTO) {
        return noteContentService.addNoteContent(addNoteContentReqDTO);
    }

    /**
     * 查询笔记内容
     */
    @PostMapping("/note/content/find")
    public Response<FindNoteContentRspDTO> findNoteContent(@RequestBody @Validated FindNoteContentReqDTO findNoteContentReqDTO) {
        return noteContentService.findNoteContent(findNoteContentReqDTO);
    }

    /**
     * 删除笔记内容
     */
    @PostMapping("/note/content/delete")
    public Response<?> deleteNoteContent(@RequestBody @Validated DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        return noteContentService.deleteNoteContent(deleteNoteContentReqDTO);
    }
}
