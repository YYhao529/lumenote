package com.deepsea.lumenote.user.biz.service;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.biz.model.vo.UpdateUserInfoReqVO;
import com.deepsea.lumenote.user.dto.req.*;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByPhoneRspDTO;

import java.util.List;

/**
 * 用户业务
 **/
public interface UserService {

    /**
     * 更新用户信息
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    /**
     * 用户注册
     */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     */
    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 修改密码
     */
    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     */
    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 批量根据用户 ID 查询用户信息
     */
    Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO);
}

