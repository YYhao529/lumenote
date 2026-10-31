package com.deepsea.lumenote.note.biz.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.kv.api.KeyValueFeignApi;
import com.deepsea.lumenote.kv.dto.req.AddNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.DeleteNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.req.FindNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.rsp.FindNoteContentRspDTO;
import com.deepsea.lumenote.note.biz.model.vo.FindNoteDetailRspVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * K-V 键值存储 RPC 服务
 */
@Component
public class KeyValueRpcService {

    @Autowired
    private KeyValueFeignApi keyValueFeignApi;

    /**
     * 保存笔记内容
     */
    public boolean saveNoteContent(String uuid,String content) {
        AddNoteContentReqDTO addNoteContentReqDTO = AddNoteContentReqDTO.builder()
                .uuid(uuid)
                .content(content)
                .build();

        Response<?> response = keyValueFeignApi.addNoteContent(addNoteContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }

        return true;
    }

    /**
     * 删除笔记内容
     */
    public boolean deleteNoteContent(String uuid) {
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = DeleteNoteContentReqDTO.builder()
                .uuid(uuid)
                .build();

        Response<?> response = keyValueFeignApi.deleteNoteContent(deleteNoteContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }

        return true;
    }

    /**
     * 查询笔记内容
     */
    public String findNoteContent(String uuid) {
        FindNoteContentReqDTO findNoteContentReqDTO = new FindNoteContentReqDTO();
        findNoteContentReqDTO.setUuid(uuid);

        Response<FindNoteContentRspDTO> response = keyValueFeignApi.findNoteContent(findNoteContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }

        return response.getData().getContent();
    }
}
