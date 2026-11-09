package com.deepsea.lumenote.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.framework.common.util.NumberUtils;
import com.deepsea.lumenote.search.domain.mapper.SelectMapper;
import com.deepsea.lumenote.search.index.UserIndex;
import com.deepsea.lumenote.search.model.vo.SearchUserReqVO;
import com.deepsea.lumenote.search.model.vo.SearchUserRspVO;
import com.deepsea.lumenote.search.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户搜索业务
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private SelectMapper selectMapper;

    /**
     * 搜索用户
     */
    @Override
    public PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO) {
        // 查询关键词
        String keyword = searchUserReqVO.getKeyword();
        // 当前页码
        Integer pageNo = searchUserReqVO.getPageNo();

        // 构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量

        // 构建 multi_match 查询，查询 nickname 和 lumenote_id 字段
        searchRequest.source().query(QueryBuilders.multiMatchQuery(keyword, UserIndex.FIELD_USER_NICKNAME, UserIndex.FIELD_USER_LUMENOTE_ID))
                .sort(UserIndex.FIELD_USER_FANS_TOTAL, SortOrder.DESC)  // 排序，按 fans_total 降序
                .from(from)
                .size(pageSize)
                .highlighter(new org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder()
                        .field(UserIndex.FIELD_USER_NICKNAME).preTags("<strong>").postTags("</strong>"));

        // 返参 VO 集合（异常时返回空列表，而不是 null）
        List<SearchUserRspVO> searchUserRspVOS = Lists.newArrayList();
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);

            // 执行查询请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            SearchHit[] hitsArray = searchResponse.getHits().getHits();
            for (SearchHit hit : hitsArray) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 获取高亮字段
                String highlightNickname = null;

                Long userId = Long.valueOf(sourceAsMap.get(UserIndex.FIELD_USER_ID).toString());
                String nickname = (String) sourceAsMap.get(UserIndex.FIELD_USER_NICKNAME);
                String avatar = (String) sourceAsMap.get(UserIndex.FIELD_USER_AVATAR);
                String lumenoteId = (String) sourceAsMap.get(UserIndex.FIELD_USER_LUMENOTE_ID);

                // ES 返回的数字可能是 Integer / Long，统一按 Number 处理，避免类型转换异常
                Number noteTotal = (Number) sourceAsMap.get(UserIndex.FIELD_USER_NOTE_TOTAL);
                Number fansTotal = (Number) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);

                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(UserIndex.FIELD_USER_NICKNAME)) {
                    highlightNickname = hit.getHighlightFields().get(UserIndex.FIELD_USER_NICKNAME).fragments()[0].string();
                }

                // 构建 VO 实体类
                SearchUserRspVO searchUserRspVO = SearchUserRspVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .lumenoteId(lumenoteId)
                        .noteTotal(Objects.isNull(noteTotal) ? 0 : noteTotal.intValue())
                        .fansTotal(NumberUtils.formatNumberString(Objects.isNull(fansTotal) ? 0L : fansTotal.longValue()))
                        .highlightNickname(highlightNickname)
                        .build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticsearch 异常: ", e);
        }

        return PageResponse.success(searchUserRspVOS, pageNo, total, pageSize);
    }

    /**
     * 重建用户索引（全量同步 + 清理 ES 中数据库已不存在的孤儿文档）
     */
    @Override
    public Response<Integer> rebuildUserIndex() {
        List<Map<String, Object>> records = selectMapper.selectEsUserIndexData(null);

        Set<String> existingUserIds = new HashSet<>();
        int count = 0;

        for (Map<String, Object> record : records) {
            try {
                String userId = String.valueOf(record.get(UserIndex.FIELD_USER_ID));
                IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
                indexRequest.id(userId);
                indexRequest.source(record);
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);

                existingUserIds.add(userId);
                count++;
            } catch (Exception e) {
                log.error("==> 重建用户索引失败, userId: {}", record.get(UserIndex.FIELD_USER_ID), e);
            }
        }

        int deleted = deleteOrphanUserDocuments(existingUserIds);

        // 刷新索引，让结果立即可见
        try {
            Request refreshRequest = new Request("POST", "/" + UserIndex.NAME + "/_refresh");
            restHighLevelClient.getLowLevelClient().performRequest(refreshRequest);
        } catch (Exception e) {
            log.warn("==> 刷新用户索引失败", e);
        }

        log.info("==> 重建用户索引完成, 同步 {} 条, 清理 {} 条", count, deleted);
        return Response.success(count);
    }

    /**
     * 删除 ES 中数据库已不存在的用户文档
     */
    private int deleteOrphanUserDocuments(Set<String> existingUserIds) {
        int deleted = 0;
        try {
            SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);
            searchRequest.source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .fetchSource(false)
                    .size(10000));

            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            for (SearchHit hit : searchResponse.getHits().getHits()) {
                if (!existingUserIds.contains(hit.getId())) {
                    restHighLevelClient.delete(new DeleteRequest(UserIndex.NAME, hit.getId()), RequestOptions.DEFAULT);
                    log.info("==> 清理 ES 孤儿用户文档, userId: {}", hit.getId());
                    deleted++;
                }
            }
        } catch (Exception e) {
            log.error("==> 清理 ES 孤儿用户文档失败", e);
        }
        return deleted;
    }
}
