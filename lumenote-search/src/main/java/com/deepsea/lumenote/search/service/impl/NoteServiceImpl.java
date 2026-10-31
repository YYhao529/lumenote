package com.deepsea.lumenote.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.deepsea.framework.common.constant.DateConstants;
import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.framework.common.util.DateUtils;
import com.deepsea.framework.common.util.NumberUtils;
import com.deepsea.lumenote.search.domain.mapper.SelectMapper;
import com.deepsea.lumenote.search.enums.NotePublishTimeRangeEnum;
import com.deepsea.lumenote.search.enums.NoteSortTypeEnum;
import com.deepsea.lumenote.search.index.NoteIndex;
import com.deepsea.lumenote.search.index.NoteIndexDocBuilder;
import com.deepsea.lumenote.search.model.vo.FindHotNoteReqVO;
import com.deepsea.lumenote.search.model.vo.SearchNoteReqVO;
import com.deepsea.lumenote.search.model.vo.SearchNoteRspVO;
import com.deepsea.lumenote.search.service.NoteService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 笔记搜索业务
 */
@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private SelectMapper selectMapper;

    /**
     * 搜索笔记
     */
    @Override
    public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        // 查询关键词
        String keyword = searchNoteReqVO.getKeyword();
        // 查询页码
        Integer pageNo = searchNoteReqVO.getPageNo();
        // 获取笔记类型
        Integer type = searchNoteReqVO.getType();
        // 获取排序方式
        Integer sort = searchNoteReqVO.getSort();
        // 获取时间范围
        Integer publishTimeRange = searchNoteReqVO.getPublishTimeRange();

        // 构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);

        // 创建查询构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 创建查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(
                QueryBuilders.multiMatchQuery(keyword)
                        .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f) // 手动设置笔记标题的权重值为 2.0
                        .field(NoteIndex.FIELD_NOTE_TOPIC) // 不设置，权重默认为 1.0
        );

        // 若勾选了笔记类型，添加过滤条件
        if (Objects.nonNull(type)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        // 按发布时间范围过滤
        NotePublishTimeRangeEnum notePublishTimeRangeEnum = NotePublishTimeRangeEnum.valueOf(publishTimeRange);

        if (Objects.nonNull(notePublishTimeRangeEnum)) {
            // 结束时间
            String endTime = DateUtils.localDateTime2String(LocalDateTime.now());
            // 开始时间
            String startTime = null;
            switch (notePublishTimeRangeEnum) {
                case DAY -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1));   // 一天之前的时间
                case WEEK -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1));   // 一周之前的时间
                case HALF_YEAR ->
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6));   // 半年内之前的时间
            }
            // 添加发布时间范围过滤条件
            boolQueryBuilder.filter(QueryBuilders.rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME)
                    .gte(startTime) // 大于等于开始时间
                    .lte(endTime)); // 小于等于结束时间
        }

        // 设置排序
        // "sort": [
        //     {
        //       "_score": {
        //         "order": "desc"
        //       }
        //     }
        //   ]
        NoteSortTypeEnum noteSortTypeEnum = NoteSortTypeEnum.valueOf(sort);
        if (Objects.nonNull(noteSortTypeEnum)) {
            switch (noteSortTypeEnum) {
                // 按笔记发布时间降序
                case LATEST ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                // 按点赞数降序
                case MOST_LIKE ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                // 按评论数降序
                case MOST_COMMENT ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                // 按收藏数降序
                case MOST_COLLECT ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            // 设置查询
            sourceBuilder.query(boolQueryBuilder);
        } else {
            // 综合排序，自定义评分，并按 _score 评分降序
            sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

            // 创建 FilterFunctionBuilder 数组
            // "functions": [
            //         {
            //           "field_value_factor": {
            //             "field": "like_total",
            //             "factor": 0.5,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "collect_total",
            //             "factor": 0.3,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "comment_total",
            //             "factor": 0.2,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         }
            //       ],
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                    // function 1
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                    .factor(0.5f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    // function 2
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                    .factor(0.3f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    // function 3
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                    .factor(0.2f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    )
            };

            // 构建 function_score 查询
            // "score_mode": "sum",
            // "boost_mode": "sum"
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder,
                            filterFunctionBuilders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM) // score_mode 为 sum
                    .boostMode(CombineFunction.SUM); // boost_mode 为 sum

            // 设置查询
            sourceBuilder.query(functionScoreQueryBuilder);
        }

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest.source().toString());

            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = parseHits(searchResponse.getHits().getHits());
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }

    /**
     * 查询热门笔记（发现页推荐流）
     *
     * 无关键词，以全部笔记为候选，复用「综合排序」的评分逻辑
     * （点赞 * 0.5 + 收藏 * 0.3 + 评论 * 0.2，均开平方），
     * 按评分降序返回排名最靠前的笔记。
     */
    @Override
    public PageResponse<SearchNoteRspVO> findHotNoteList(FindHotNoteReqVO findHotNoteReqVO) {
        // 查询页码
        Integer pageNo = findHotNoteReqVO.getPageNo();

        // 构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);

        // 创建查询构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 与「综合排序」一致的评分函数
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                // function 1
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                .factor(0.5f)
                                .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                .missing(0)
                ),
                // function 2
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                .factor(0.3f)
                                .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                .missing(0)
                ),
                // function 3
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                .factor(0.2f)
                                .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                .missing(0)
                )
        };

        // 以全部文档为候选，按总评分降序
        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                        QueryBuilders.matchAllQuery(), filterFunctionBuilders)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM) // score_mode 为 sum
                .boostMode(CombineFunction.SUM); // boost_mode 为 sum

        sourceBuilder.query(functionScoreQueryBuilder);
        sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = new ArrayList<>();
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest.source().toString());

            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = parseHits(searchResponse.getHits().getHits());
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }

    /**
     * 重建笔记索引（全量同步：回填数据库中的笔记，并清理数据库中已不存在的孤儿文档）
     */
    @Override
    public Response<Integer> rebuildNoteIndex() {
        // 查询全部笔记数据
        List<Map<String, Object>> records = selectMapper.selectEsNoteIndexData(null);

        // 数据库中真实存在的笔记 ID
        Set<String> existingNoteIds = new HashSet<>();

        int count = 0;
        for (Map<String, Object> record : records) {
            try {
                String noteId = String.valueOf(record.get(NoteIndex.FIELD_NOTE_ID));
                IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
                indexRequest.id(noteId);
                indexRequest.source(NoteIndexDocBuilder.build(record));
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);

                existingNoteIds.add(noteId);
                count++;
            } catch (Exception e) {
                log.error("==> 重建笔记索引失败, noteId: {}", record.get(NoteIndex.FIELD_NOTE_ID), e);
            }
        }

        // 清理 ES 中数据库已不存在的文档（历史测试数据等）
        int deleted = deleteOrphanDocuments(existingNoteIds);

        // 刷新索引，让结果立即可见
        try {
            Request refreshRequest = new Request("POST", "/" + NoteIndex.NAME + "/_refresh");
            restHighLevelClient.getLowLevelClient().performRequest(refreshRequest);
        } catch (Exception e) {
            log.warn("==> 刷新笔记索引失败", e);
        }

        log.info("==> 重建笔记索引完成, 同步 {} 条, 清理 {} 条", count, deleted);
        return Response.success(count);
    }

    /**
     * 删除 ES 中数据库已不存在的笔记文档
     *
     * @return 清理的文档数
     */
    private int deleteOrphanDocuments(Set<String> existingNoteIds) {
        int deleted = 0;
        try {
            // 查询 ES 中全部的文档 ID
            SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
            searchRequest.source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .fetchSource(false)
                    .size(10000));

            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            for (SearchHit hit : searchResponse.getHits().getHits()) {
                if (!existingNoteIds.contains(hit.getId())) {
                    restHighLevelClient.delete(new DeleteRequest(NoteIndex.NAME, hit.getId()), RequestOptions.DEFAULT);
                    log.info("==> 清理 ES 孤儿文档, noteId: {}", hit.getId());
                    deleted++;
                }
            }
        } catch (Exception e) {
            log.error("==> 清理 ES 孤儿文档失败", e);
        }
        return deleted;
    }

    /**
     * 解析 ES 命中的笔记文档，组装返参
     */
    private List<SearchNoteRspVO> parseHits(SearchHit[] hits) {
        List<SearchNoteRspVO> searchNoteRspVOS = new ArrayList<>();
        for (SearchHit hit : hits) {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
            String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
            String videoUri = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_VIDEO_URI);
            String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
            String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
            String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
            LocalDateTime updateTime = LocalDateTime.parse((String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME), DateConstants.DATE_FORMAT_Y_M_D_H_M_S);

            Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);
            Integer commentTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COMMENT_TOTAL);
            Integer collectTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COLLECT_TOTAL);

            // 笔记类型：0 图文，1 视频
            Object typeObj = sourceAsMap.get(NoteIndex.FIELD_NOTE_TYPE);
            Integer type = Objects.isNull(typeObj) ? null : Integer.valueOf(typeObj.toString());

            // 获取高亮字段
            String highlightTitle = null;
            if (CollUtil.isNotEmpty(hit.getHighlightFields())
                    && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                highlightTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
            }

            SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                    .noteId(noteId)
                    .cover(cover)
                    .type(type)
                    .videoUri(videoUri)
                    .title(title)
                    .highlightTitle(highlightTitle)
                    .avatar(avatar)
                    .nickname(nickname)
                    .updateTime(DateUtils.formatRelativeTime(updateTime))
                    .likeTotal(NumberUtils.formatNumberString(likeTotal))
                    .commentTotal(NumberUtils.formatNumberString(commentTotal))
                    .collectTotal(NumberUtils.formatNumberString(collectTotal))
                    .build();
            searchNoteRspVOS.add(searchNoteRspVO);
        }
        return searchNoteRspVOS;
    }
}
