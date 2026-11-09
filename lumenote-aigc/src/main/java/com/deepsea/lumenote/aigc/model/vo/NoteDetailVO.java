package com.deepsea.lumenote.aigc.model.vo;

import lombok.Data;

/**
 * 笔记详情（含正文 uuid，正文从 Cassandra 取）
 */
@Data
public class NoteDetailVO {

    private Long id;

    private String title;

    private String topicName;

    private String contentUuid;

    /**
     * 图片地址，数据库里是 JSON 数组字符串
     */
    private String imgUris;

    private String createTime;

    private Integer visible;
}
