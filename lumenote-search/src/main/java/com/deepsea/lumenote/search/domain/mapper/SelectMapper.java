package com.deepsea.lumenote.search.domain.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface SelectMapper {

    /**
     * 查询笔记文档所需的全字段数据
     *
     * @param noteId 为 null 时查询全部笔记（用于全量重建索引）
     */
    List<Map<String, Object>> selectEsNoteIndexData(@Param("noteId") Long noteId);
}
