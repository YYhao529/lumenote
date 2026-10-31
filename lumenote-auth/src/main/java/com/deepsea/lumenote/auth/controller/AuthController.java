package com.deepsea.lumenote.auth.controller;

import com.deepsea.framework.biz.operationlog.aspect.ApiOperationLog;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.auth.model.vo.user.UpdatePasswordReqVO;
import com.deepsea.lumenote.auth.model.vo.user.UserLoginReqVO;
import com.deepsea.lumenote.auth.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@RequestBody @Validated UserLoginReqVO userLoginReqVO) {
        return authService.loginAndRegister(userLoginReqVO);
    }

    @PostMapping("/logout")
    @ApiOperationLog(description = "用户退出登录")
    public Response<?> logout(){
        return authService.logout();
    }

    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(@RequestBody @Validated UpdatePasswordReqVO updatePasswordReqVO){
        return authService.updatePassword(updatePasswordReqVO);
    }
}
