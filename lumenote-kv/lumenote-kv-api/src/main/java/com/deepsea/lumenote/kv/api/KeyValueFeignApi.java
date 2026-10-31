package com.deepsea.lumenote.kv.api;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.kv.constant.ApiConstants;
import com.deepsea.lumenote.kv.dto.req.AddNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.DeleteNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.FindNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.rsp.FindNoteContentRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * K-V 键值存储 Feign 接口
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface KeyValueFeignApi {

    String PREFIX = "/kv";

    @PostMapping(value = PREFIX + "/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO addNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/note/content/find")
    Response<FindNoteContentRspDTO> findNoteContent(@RequestBody FindNoteContentReqDTO findNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/note/content/delete")
    Response<?> deleteNoteContent(@RequestBody DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
