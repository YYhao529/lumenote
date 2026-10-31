package com.deepsea.lumenote.search.service;

import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.lumenote.search.model.vo.SearchUserReqVO;
import com.deepsea.lumenote.search.model.vo.SearchUserRspVO;

import java.io.IOException;

/**
 * 用户搜索业务
 */
public interface UserService {

    /**
     * 搜索用户
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);
}
