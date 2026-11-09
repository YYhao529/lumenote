package com.deepsea.lumenote.user.biz.controller;

import com.deepsea.framework.biz.operationlog.aspect.ApiOperationLog;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.biz.model.vo.UpdateUserInfoReqVO;
import com.deepsea.lumenote.user.biz.service.UserService;
import com.deepsea.lumenote.user.dto.req.*;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByPhoneRspDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 更新用户信息
     */
    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> updateUserInfo(@Validated UpdateUserInfoReqVO updateUserInfoReqVO) {
        return userService.updateUserInfo(updateUserInfoReqVO);
    }

    // ===================================== 对其他服务提供的接口 =====================================
    @PostMapping("/register")
    @ApiOperationLog(description = "用户注册")
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO) {
        return userService.register(registerUserReqDTO);
    }

    @PostMapping("/findByPhone")
    @ApiOperationLog(description = "根据手机号查询用户信息")
    public Response<FindUserByPhoneRspDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        return userService.findByPhone(findUserByPhoneReqDTO);
    }

    @PostMapping("/password/verify")
    @ApiOperationLog(description = "校验手机号与密码")
    public Response<Long> verifyPassword(@Validated @RequestBody VerifyPasswordReqDTO verifyPasswordReqDTO) {
        return userService.verifyPassword(verifyPasswordReqDTO);
    }

    @PostMapping("/password/verifyById")
    @ApiOperationLog(description = "按用户 ID 校验密码")
    public Response<Boolean> verifyPasswordByUserId(@Validated @RequestBody VerifyPasswordByUserIdReqDTO verifyPasswordByUserIdReqDTO) {
        return userService.verifyPasswordByUserId(verifyPasswordByUserIdReqDTO);
    }

    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(@Validated @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        return userService.updatePassword(updateUserPasswordReqDTO);
    }

    @PostMapping("/findById")
    @ApiOperationLog(description = "根据用户 ID 查询用户信息")
    public Response<FindUserByIdRspDTO> findById(@Validated @RequestBody FindUserByIdReqDTO findUserByIdReqDTO) {
        return userService.findById(findUserByIdReqDTO);
    }

    @PostMapping("/findByIds")
    @ApiOperationLog(description = "批量查询用户信息")
    public Response<List<FindUserByIdRspDTO>> findByIds(@Validated @RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        return userService.findByIds(findUsersByIdsReqDTO);
    }
}
