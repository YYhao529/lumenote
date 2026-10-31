package com.deepsea.lumenote.auth.controller;

import com.deepsea.framework.biz.operationlog.aspect.ApiOperationLog;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.deepsea.lumenote.auth.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class VerificationCodeController {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @PostMapping("/verification/code/send")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> sendVerificationCode(@Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }
}
