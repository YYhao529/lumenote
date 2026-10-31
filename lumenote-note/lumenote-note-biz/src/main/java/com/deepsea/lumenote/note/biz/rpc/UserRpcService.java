package com.deepsea.lumenote.note.biz.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.api.UserFeignApi;
import com.deepsea.lumenote.user.dto.req.FindUserByIdReqDTO;
import com.deepsea.lumenote.user.dto.req.FindUsersByIdsReqDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务
 */
@Component
public class UserRpcService {

    @Autowired
    private UserFeignApi userFeignApi;

    /**
     * 根据用户 id 查询用户信息
     */
    public FindUserByIdRspDTO findById(Long userId){
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);
        if (Objects.isNull(response)||!response.isSuccess()){
            return null;
        }

        return response.getData();
    }

    /**
     * 批量根据用户 id 查询用户信息
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds) {
        if (Objects.isNull(userIds) || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(userIds);

        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);
        if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
            return Collections.emptyList();
        }

        return response.getData();
    }
}
