package com.deepsea.lumenote.auth.service;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.auth.model.vo.verificationcode.SendVerificationCodeReqVO;

public interface VerificationCodeService {

    /**
     * 发送短信验证码
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}
