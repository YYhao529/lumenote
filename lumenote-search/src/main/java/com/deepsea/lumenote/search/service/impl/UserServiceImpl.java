package com.deepsea.lumenote.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.util.NumberUtils;
import com.deepsea.lumenote.search.index.UserIndex;
import com.deepsea.lumenote.search.model.vo.SearchUserReqVO;
import com.deepsea.lumenote.search.model.vo.SearchUserRspVO;
import com.deepsea.lumenote.search.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 用户搜索业务
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

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
                .highlighter(new HighlightBuilder().field(UserIndex.FIELD_USER_NICKNAME).preTags("<strong>").postTags("</strong>"));

        // 返参 VO 集合
        List<SearchUserRspVO> searchUserRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);

            // 执行查询请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchUserRspVOS = Lists.newArrayList();

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
                Integer noteTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_NOTE_TOTAL);
                Integer fansTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);
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
                        .noteTotal(noteTotal)
                        .fansTotal(NumberUtils.formatNumberString(fansTotal))
                        .highlightNickname(highlightNickname)
                        .build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticsearch 异常: ", e);
        }

        return PageResponse.success(searchUserRspVOS, pageNo, total);
    }
}
