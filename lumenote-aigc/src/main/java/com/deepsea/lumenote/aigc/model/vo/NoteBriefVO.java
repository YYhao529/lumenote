package com.deepsea.lumenote.aigc.model.vo;

import lombok.Data;

/**
 * 笔记摘要（给模型的轻量结构，别塞整篇正文）
 */
@Data
public class NoteBriefVO {

    private Long id;

    private String title;

    private String topicName;

    private String createTime;
}
