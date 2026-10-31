package com.deepsea.lumenote.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelDO {
    private Long id;

    private String name;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}