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
public class ChannelTopicRelDO {
    private Long id;

    private Long channelId;

    private Long topicId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}