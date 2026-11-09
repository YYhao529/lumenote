package com.deepsea.lumenote.auth.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.api.UserFeignApi;
import com.deepsea.lumenote.user.dto.req.FindUserByPhoneReqDTO;
import com.deepsea.lumenote.user.dto.req.RegisterUserReqDTO;
import com.deepsea.lumenote.user.dto.req.UpdateUserPasswordReqDTO;
import com.deepsea.lumenote.user.dto.req.VerifyPasswordByUserIdReqDTO;
import com.deepsea.lumenote.user.dto.req.VerifyPasswordReqDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByPhoneRspDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户 RPC 服务
 */
@Component
public class UserRpcService {

    @Autowired
    private UserFeignApi userFeignApi;

    /**
     * 用户注册
     */
    public Long registerUser(String phone) {
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();
        registerUserReqDTO.setPhone(phone);

        Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);

        if (!response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 根据手机号查询用户信息
     */
    public FindUserByPhoneRspDTO findByPhone(String phone) {
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();
        findUserByPhoneReqDTO.setPhone(phone);

        Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(findUserByPhoneReqDTO);

        if (!response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 校验手机号 + 密码，匹配返回用户 ID，否则返回 null
     */
    public Long verifyPassword(String phone, String password) {
        VerifyPasswordReqDTO verifyPasswordReqDTO = VerifyPasswordReqDTO.builder()
                .phone(phone)
                .password(password)
                .build();

        Response<Long> response = userFeignApi.verifyPassword(verifyPasswordReqDTO);

        if (!response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 按用户 ID 校验密码（修改密码前校验原密码）
     */
    public boolean verifyPasswordByUserId(Long userId, String password) {
        VerifyPasswordByUserIdReqDTO verifyPasswordByUserIdReqDTO = VerifyPasswordByUserIdReqDTO.builder()
                .userId(userId)
                .password(password)
                .build();

        Response<Boolean> response = userFeignApi.verifyPasswordByUserId(verifyPasswordByUserIdReqDTO);

        return response.isSuccess() && Boolean.TRUE.equals(response.getData());
    }

    /**
     * 修改密码（原密码 + 新密码，由用户服务校验并加密）
     */
    public boolean updatePassword(String oldPassword, String newPassword) {
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = UpdateUserPasswordReqDTO.builder()
                .oldPassword(oldPassword)
                .newPassword(newPassword)
                .build();

        Response<?> response = userFeignApi.updatePassword(updateUserPasswordReqDTO);
        return response.isSuccess();
    }
}
