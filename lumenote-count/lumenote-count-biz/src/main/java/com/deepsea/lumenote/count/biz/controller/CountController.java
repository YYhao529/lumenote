package com.deepsea.lumenote.count.biz.controller;

import com.deepsea.framework.biz.operationlog.aspect.ApiOperationLog;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.count.biz.model.vo.FindNoteCountReqVO;
import com.deepsea.lumenote.count.biz.model.vo.FindUserCountReqVO;
import com.deepsea.lumenote.count.biz.model.vo.FindUserCountsReqVO;
import com.deepsea.lumenote.count.biz.model.vo.NoteCountRspVO;
import com.deepsea.lumenote.count.biz.model.vo.UserCountRspVO;
import com.deepsea.lumenote.count.biz.service.CountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计数查询
 */
@RestController
@RequestMapping("/count")
@Slf4j
public class CountController {

    @Autowired
    private CountService countService;

    @PostMapping("/note")
    @ApiOperationLog(description = "查询笔记计数")
    public Response<NoteCountRspVO> findNoteCount(@RequestBody @Validated FindNoteCountReqVO findNoteCountReqVO) {
        return Response.success(countService.findNoteCount(findNoteCountReqVO));
    }

    @PostMapping("/user")
    @ApiOperationLog(description = "查询用户计数")
    public Response<UserCountRspVO> findUserCount(@RequestBody @Validated FindUserCountReqVO findUserCountReqVO) {
        return Response.success(countService.findUserCount(findUserCountReqVO));
    }

    @PostMapping("/user/batch")
    @ApiOperationLog(description = "批量查询用户计数")
    public Response<List<UserCountRspVO>> findUserCounts(@RequestBody @Validated FindUserCountsReqVO findUserCountsReqVO) {
        return Response.success(countService.findUserCounts(findUserCountsReqVO));
    }
}
