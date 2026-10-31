package com.deepsea.lumenote.kv.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 笔记内容
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteContentRspDTO {

    /**
     * 笔记 UUID
     */
    private UUID uuid;

    /**
     * 笔记内容
     */
    private String content;
}
