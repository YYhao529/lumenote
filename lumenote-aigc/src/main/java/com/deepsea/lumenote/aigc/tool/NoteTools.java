package com.deepsea.lumenote.aigc.tool;

import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.aigc.domain.mapper.NoteQueryMapper;
import com.deepsea.lumenote.aigc.model.vo.NoteBriefVO;
import com.deepsea.lumenote.aigc.model.vo.NoteDetailVO;
import com.deepsea.lumenote.kv.api.KeyValueFeignApi;
import com.deepsea.lumenote.kv.dto.req.FindNoteContentReqDTO;
import com.deepsea.lumenote.kv.dto.rsp.FindNoteContentRspDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 智能助手可调用的「笔记工具」（只读）。
 *
 * <p>安全约定：用户身份一律取自 {@link LoginUserContextHolder}，<b>绝不</b>接受模型传入的 userId，
 * 避免提示词注入导致的越权读取。</p>
 */
@Component
@Slf4j
public class NoteTools {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    @Autowired
    private NoteQueryMapper noteQueryMapper;

    @Autowired
    private KeyValueFeignApi keyValueFeignApi;

    /**
     * 搜索结果（给模型的轻量结构）
     */
    public record NoteBrief(Long id, String title, String topic, String createTime) {
    }

    /**
     * 笔记详情
     */
    public record NoteDetail(Long id, String title, String topic, String content,
                             List<String> images, String createTime) {
    }

    @Tool(name = "searchMyNotes",
            description = "搜索当前登录用户自己写的笔记，返回笔记ID、标题、话题和创建时间。"
                    + "当用户提到“我的笔记”“我写的”“我之前发的”“那篇关于X的笔记”时调用。")
    public List<NoteBrief> searchMyNotes(
            @ToolParam(description = "搜索关键词，可以是标题里的词，如 咖啡、穿搭；不传则返回最近的笔记", required = false)
            String keyword,
            @ToolParam(description = "返回条数，1~10，默认 5", required = false)
            Integer limit) {

        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            return List.of();
        }

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));

        List<NoteBriefVO> rows = noteQueryMapper.searchByCreator(userId, normalizedKeyword, size);
        log.info("工具调用 searchMyNotes, userId: {}, keyword: {}, 命中: {}", userId, normalizedKeyword, rows.size());

        return rows.stream()
                .map(row -> new NoteBrief(row.getId(), row.getTitle(), row.getTopicName(), row.getCreateTime()))
                .toList();
    }

    @Tool(name = "getNoteDetail",
            description = "读取当前用户某篇笔记的完整内容（标题、话题、正文、图片），"
                    + "用于改写、总结、润色。noteId 必须来自 searchMyNotes 的返回结果。")
    public NoteDetail getNoteDetail(
            @ToolParam(description = "笔记ID，来自 searchMyNotes 返回的 id")
            Long noteId) {

        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null || noteId == null) {
            throw new IllegalArgumentException("缺少笔记ID或未登录");
        }

        NoteDetailVO detail = noteQueryMapper.findByIdAndCreator(noteId, userId);
        if (detail == null) {
            throw new IllegalArgumentException("没有找到这篇笔记，或它不属于当前用户");
        }

        String content = findContent(detail.getContentUuid());
        log.info("工具调用 getNoteDetail, userId: {}, noteId: {}, 正文长度: {}",
                userId, noteId, content == null ? 0 : content.length());

        return new NoteDetail(detail.getId(), detail.getTitle(), detail.getTopicName(), content,
                parseImages(detail.getImgUris()), detail.getCreateTime());
    }

    /**
     * 正文存在 Cassandra（kv 服务），调用失败时降级为 null，不影响标题等信息返回
     */
    private String findContent(String contentUuid) {
        if (!StringUtils.hasText(contentUuid)) {
            return null;
        }
        try {
            Response<FindNoteContentRspDTO> response = keyValueFeignApi.findNoteContent(
                    FindNoteContentReqDTO.builder().uuid(contentUuid).build());
            if (response != null && response.isSuccess() && Objects.nonNull(response.getData())) {
                return response.getData().getContent();
            }
        } catch (Exception e) {
            log.warn("读取笔记正文失败, uuid: {}, error: {}", contentUuid, e.getMessage());
        }
        return null;
    }

    private List<String> parseImages(String imgUris) {
        if (!StringUtils.hasText(imgUris)) {
            return List.of();
        }
        // t_note.img_uris 是逗号分隔的 URL 字符串，不是 JSON 数组
        return Arrays.stream(imgUris.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
