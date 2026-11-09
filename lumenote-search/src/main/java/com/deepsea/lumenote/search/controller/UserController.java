package com.deepsea.lumenote.search.controller;

import com.deepsea.framework.biz.operationlog.aspect.ApiOperationLog;
import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.search.model.vo.SearchUserReqVO;
import com.deepsea.lumenote.search.model.vo.SearchUserRspVO;
import com.deepsea.lumenote.search.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户
 */
@RestController
@RequestMapping("/search")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/user")
    @ApiOperationLog(description = "搜索用户")
    public PageResponse<SearchUserRspVO> searchUser(@RequestBody @Validated SearchUserReqVO searchUserReqVO) {
        return userService.searchUser(searchUserReqVO);
    }

    @PostMapping("/user/rebuild")
    @ApiOperationLog(description = "重建用户索引")
    public Response<Integer> rebuildUserIndex() {
        return userService.rebuildUserIndex();
    }
}
