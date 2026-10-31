package com.deepsea.lumenote.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * 笔记发布
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishNoteReqVO {

    @NotNull(message = "笔记类型不能为空")
    private Integer type;

    private List<String> imgUris;

    private String videoUri;

    private String title;

    private String content;

    private Long topicId;

    /**
     * 话题名称（推荐，用户直接填名称；话题不存在时自动创建）
     */
    private String topicName;
}
