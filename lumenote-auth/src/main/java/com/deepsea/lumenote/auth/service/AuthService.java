package com.deepsea.lumenote.auth.service;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.auth.model.vo.user.UpdatePasswordReqVO;
import com.deepsea.lumenote.auth.model.vo.user.UserLoginReqVO;

public interface AuthService {

    /**
     * 登录与注册
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    /**
     * 退出登录
     */
    Response<?> logout();

    /**
     * 修改密码
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
