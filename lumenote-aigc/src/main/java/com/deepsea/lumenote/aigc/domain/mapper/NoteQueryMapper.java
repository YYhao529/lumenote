package com.deepsea.lumenote.aigc.domain.mapper;

import com.deepsea.lumenote.aigc.model.vo.NoteBriefVO;
import com.deepsea.lumenote.aigc.model.vo.NoteDetailVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记只读查询（给智能助手的函数调用用）
 *
 * <p>只查当前用户自己的笔记，creatorId 一律由服务端从登录上下文取，不接受模型传参。</p>
 */
public interface NoteQueryMapper {

    /**
     * 按关键词搜索当前用户的笔记
     *
     * @param creatorId 用户 ID（服务端注入）
     * @param keyword   关键词，可为空（为空则返回最近的笔记）
     * @param limit     返回条数
     */
    List<NoteBriefVO> searchByCreator(@Param("creatorId") Long creatorId,
                                      @Param("keyword") String keyword,
                                      @Param("limit") int limit);

    /**
     * 按 ID 查当前用户的单篇笔记
     */
    NoteDetailVO findByIdAndCreator(@Param("noteId") Long noteId,
                                    @Param("creatorId") Long creatorId);
}
