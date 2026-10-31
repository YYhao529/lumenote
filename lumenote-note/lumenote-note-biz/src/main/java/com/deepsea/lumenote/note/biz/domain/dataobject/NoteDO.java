package com.deepsea.lumenote.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteDO {
    private Long id;

    private String title;

    private Boolean isContentEmpty;

    private Long creatorId;

    private Long topicId;

    private String topicName;

    private Boolean isTop;

    private Integer type;

    private String imgUris;

    private String videoUri;

    private Integer visible;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer status;

    private String contentUuid;

    /**
     * 点赞总数（联表 t_note_count 查询得到，非 t_note 表字段）
     */
    private Long likeTotal;

    /**
     * 收藏总数（联表 t_note_count 查询得到，非 t_note 表字段）
     */
    private Long collectTotal;
}