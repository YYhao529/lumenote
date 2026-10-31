package com.deepsea.lumenote.user.api;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.constant.ApiConstants;
import com.deepsea.lumenote.user.dto.req.*;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByPhoneRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {

    String PREFIX = "/user";

    /**
     * 用户注册
     */
    @PostMapping(value = PREFIX + "/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     */
    @PostMapping(PREFIX + "/findByPhone")
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 修改密码
     */
    @PostMapping(PREFIX + "/password/update")
    Response<?> updatePassword(@RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     */
    @PostMapping(PREFIX + "/findById")
    Response<FindUserByIdRspDTO> findById(@RequestBody FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 批量查询用户信息
     */
    @PostMapping(PREFIX + "/findByIds")
    Response<List<FindUserByIdRspDTO>> findByIds(@RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO);
}
