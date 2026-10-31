package com.deepsea.lumenote.user.relation.biz.service;

import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.relation.biz.model.vo.*;

public interface RelationService {

    /**
     * 关注用户
     **/
    Response<?> follow(FollowUserReqVO followUserReqVO);

    /**
     * 取关用户
     */
    Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO);

    /**
     * 查询用户关注列表
     */
    PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO);

    /**
     * 查询粉丝列表
     */
    PageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO findFansListReqVO);
}
