package com.deepsea.lumenote.auth.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.api.UserFeignApi;
import com.deepsea.lumenote.user.dto.req.FindUserByPhoneReqDTO;
import com.deepsea.lumenote.user.dto.req.RegisterUserReqDTO;
import com.deepsea.lumenote.user.dto.req.UpdateUserPasswordReqDTO;
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
     * 修改密码
     */
    public void updatePassword(String encodePassword){
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = new UpdateUserPasswordReqDTO();
        updateUserPasswordReqDTO.setEncodePassword(encodePassword);

        userFeignApi.updatePassword(updateUserPasswordReqDTO);
    }
}
