package com.deepsea.lumenote.user.relation.biz.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.user.relation.biz.model.dto.FindUserCountsReqDTO;
import com.deepsea.lumenote.user.relation.biz.model.dto.UserCountRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 计数服务 Feign 接口
 */
@FeignClient(name = "lumenote-count")
public interface CountFeignApi {

    @PostMapping("/count/user/batch")
    Response<List<UserCountRspDTO>> findUserCounts(@RequestBody FindUserCountsReqDTO findUserCountsReqDTO);
}
