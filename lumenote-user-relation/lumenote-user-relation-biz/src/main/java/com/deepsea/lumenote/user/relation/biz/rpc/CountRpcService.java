package com.deepsea.lumenote.user.relation.biz.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.relation.biz.model.dto.FindUserCountsReqDTO;
import com.deepsea.lumenote.user.relation.biz.model.dto.UserCountRspDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计数服务 RPC 封装
 */
@Slf4j
@Component
public class CountRpcService {

    @Autowired
    private CountFeignApi countFeignApi;

    /**
     * 批量查询用户计数，返回 userId -> 计数 的 Map
     */
    public Map<Long, UserCountRspDTO> findUserCounts(List<Long> userIds) {
        if (Objects.isNull(userIds) || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Response<List<UserCountRspDTO>> response = countFeignApi.findUserCounts(
                    FindUserCountsReqDTO.builder().userIds(userIds).build());

            if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
                return Collections.emptyMap();
            }

            return response.getData().stream()
                    .filter(Objects::nonNull)
                    .filter(item -> Objects.nonNull(item.getUserId()))
                    .collect(Collectors.toMap(UserCountRspDTO::getUserId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            // 计数服务不可用时不影响列表主体展示
            log.warn("==> 批量查询用户计数失败, userIds: {}", userIds, e);
            return Collections.emptyMap();
        }
    }
}
