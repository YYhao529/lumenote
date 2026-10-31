package com.deepsea.lumenote.search.index;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 笔记索引文档构建
 */
public class NoteIndexDocBuilder {

    private NoteIndexDocBuilder() {
    }

    /**
     * 将数据库查询结果转换为 Elasticsearch 文档
     *
     * 数据库里的 img_uris 是逗号拼接的多张图片地址，取第一张作为封面写入 cover 字段，
     * 否则 ES 中没有 cover，搜索结果就拿不到笔记封面图。
     */
    public static Map<String, Object> build(Map<String, Object> record) {
        Map<String, Object> doc = new HashMap<>(record);

        Object imgUris = record.get("img_uris");
        if (Objects.nonNull(imgUris)) {
            String firstImage = String.valueOf(imgUris).split(",")[0].trim();
            if (!firstImage.isEmpty()) {
                doc.put(NoteIndex.FIELD_NOTE_COVER, firstImage);
            }
        }

        return doc;
    }
}
