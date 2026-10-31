package com.deepsea.lumenote.user.relation.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.api.UserFeignApi;
import com.deepsea.lumenote.user.dto.req.FindUserByIdReqDTO;
import com.deepsea.lumenote.user.dto.req.FindUsersByIdsReqDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 用户 RPC 服务
 */
@Component
public class UserRpcService {

    @Autowired
    private UserFeignApi userFeignApi;

    /**
     * 根据用户 ID 查询用户信息
     */
    public FindUserByIdRspDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if (!response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }
        return response.getData();
    }

    /**
     * 批量查询用户信息
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds) {
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(userIds);

        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);

        if (!response.isSuccess() || CollUtil.isEmpty(response.getData())) {
            return null;
        }

        return response.getData();
    }
}
