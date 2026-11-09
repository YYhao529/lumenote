package com.deepsea.lumenote.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.alibaba.otter.canal.protocol.exception.CanalClientException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.deepsea.lumenote.search.domain.mapper.SelectMapper;
import com.deepsea.lumenote.search.enums.NoteStatusEnum;
import com.deepsea.lumenote.search.enums.NoteVisibleEnum;
import com.deepsea.lumenote.search.index.NoteIndex;
import com.deepsea.lumenote.search.index.NoteIndexDocBuilder;
import com.deepsea.lumenote.search.index.UserIndex;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CanalSchedule implements Runnable {

    @Autowired
    private CanalConnector canalConnector;
    @Autowired
    private CanalProperties canalProperties;
    @Autowired
    private SelectMapper selectMapper;
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    @Scheduled(fixedDelay = 100)    // 每隔100ms执行一次
    public void run() {
        // 初始化批次 ID，-1 表示未开始或未获取到数据
        long batchId = -1;
        try {
            // 从 canalConnector 获取批量消息，返回的数据量由 batchSize 控制，若不足，则拉取已有的
            Message message = canalConnector.getWithoutAck(canalProperties.getBatchSize());

            // 获取当前拉取消息的批次 ID
            batchId = message.getId();

            // 获取数据条目
            List<CanalEntry.Entry> entries = message.getEntries();
            // 获取当前批次中的数据条数
            long size = entries.size();
            if (batchId == -1 || size == 0) {
                try {
                    // 若拉取数据为空，等待1000ms后重试
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            } else {
                // 如果当前批次有数据，处理这批次数据
                processEntry(message.getEntries());
            }

            // 对当前批次的消息进行 ack 确认，表示该批次的数据已经被成功消费
            canalConnector.ack(batchId);
        } catch (Exception e) {
            log.error("消费 Canal 批次数据异常", e);
            // 如果出现异常，需要进行数据回滚，以便重新消费这批次的数据
            canalConnector.rollback(batchId);
        }
    }

    /**
     * 处理这一批次数据
     */
    private void processEntry(List<CanalEntry.Entry> entries) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            // 处理 ROWDATA 类型的数据，忽略其他类型数据
            if (entry.getEntryType() == CanalEntry.EntryType.ROWDATA) {
                // 获取当前数据条目的数据库名称
                String database = entry.getHeader().getSchemaName();
                // 获取当前数据条目的表名称
                String table = entry.getHeader().getTableName();
                // 获取当前数据条目的事件类型，如 INSERT、UPDATE、DELETE 等
                CanalEntry.EventType eventType = entry.getHeader().getEventType();

                if (eventType==CanalEntry.EventType.DELETE) continue;

                // 解析出 RowChange 对象，包含 RowData 和事件相关信息
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());

                // 遍历所有行数据
                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    // 获取行中所有列的最新值
                    List<CanalEntry.Column> columns = rowData.getAfterColumnsList();

                    // 将列数据解析为 Map，方便后续处理
                    Map<String, Object> columnMap = parseColumns2Map(columns);

                    log.info("EventType: {}, Database: {}, Table: {}, Columns: {}", eventType, database, table, columnMap);

                    // 逐条处理并隔离异常：单条坏事件不能把整批拖回滚。
                    // 之前 t_user_count 的 ES update 因文档不存在抛 document_missing_exception，
                    // 整批 rollback 后无限重试，导致后面所有笔记永远进不了 ES。
                    try {
                        processEvent(columnMap, table, eventType);
                    } catch (Exception e) {
                        log.error("处理单条 Canal 事件失败，已跳过并继续, table: {}, eventType: {}, error: {}",
                                table, eventType, e.getMessage());
                    }
                }
            }
        }

    }

    /**
     * 处理事件
     */
    private void processEvent(Map<String, Object> columnMap, String table, CanalEntry.EventType eventType) throws IOException {
        switch (table) {
            case "t_user" -> handleUserEvent(columnMap, eventType); // 用户表
            case "t_user_count" -> handleUserCountEvent(columnMap, eventType); // 用户计数表
            case "t_note" -> handleNoteEvent(columnMap, eventType); // 笔记表
            case "t_note_count" -> handleNoteCountEvent(columnMap, eventType); // 笔记计数表
            default -> log.warn("Table: {} not support", table);
        }
    }

    /**
     * 处理笔记表事件
     */
    private void handleNoteEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws IOException {
        // 获取笔记 ID
        Long noteId = Long.valueOf((String) columnMap.get("id"));

        // 不同的事件，处理逻辑不同
        switch (eventType) {
            case INSERT -> syncNoteIndex(noteId); // 记录新增事件
            case UPDATE -> { // 记录更新事件
                // 笔记可见范围
                Integer visible = Integer.valueOf(columnMap.get("visible").toString());
                // 笔记变更后的状态
                Integer status = Integer.valueOf(columnMap.get("status").toString());

                // 如果可见性为私有，或状态为已删除，直接删除索引
                if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                        || Objects.equals(status, NoteStatusEnum.DELETED.getCode())
                        || Objects.equals(status, NoteStatusEnum.DOWNED.getCode())) {
                    // 删除指定ID的文档
                    deleteNoteDocument(String.valueOf(noteId));
                } else if (Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode())
                        && Objects.equals(status, NoteStatusEnum.NORMAL.getCode())) {   // 正常展示，并且可见性为公开
                    // 对索引进行覆盖更新
                    syncNoteIndex(noteId);
                }
            }
            default -> log.warn("Unhandled event type for t_note: {}", eventType);
        }
    }

    /**
     * 删除指定ID的文档
     */
    private void deleteNoteDocument(String documentId) throws IOException {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(NoteIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /**
     * 处理笔记计数表事件：局部更新 ES 文档中的点赞 / 收藏 / 评论数
     *
     * 说明：点赞、收藏只变更 t_note_count，不会触发 t_note 的变更，
     * 因此必须单独订阅这张表，否则首页/搜索里的计数会一直是旧值。
     */
    private void handleNoteCountEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws IOException {
        if (eventType == CanalEntry.EventType.DELETE) return;

        Object noteIdValue = columnMap.get("note_id");
        if (Objects.isNull(noteIdValue)) return;

        String noteId = String.valueOf(noteIdValue);

        Map<String, Object> doc = new HashMap<>();
        putNumberIfPresent(doc, NoteIndex.FIELD_NOTE_LIKE_TOTAL, columnMap.get("like_total"));
        putNumberIfPresent(doc, NoteIndex.FIELD_NOTE_COLLECT_TOTAL, columnMap.get("collect_total"));
        putNumberIfPresent(doc, NoteIndex.FIELD_NOTE_COMMENT_TOTAL, columnMap.get("comment_total"));

        if (doc.isEmpty()) return;

        UpdateRequest updateRequest = new UpdateRequest(NoteIndex.NAME, noteId).doc(doc);
        try {
            restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT);
        } catch (DocumentMissingException | ElasticsearchStatusException e) {
            // 索引中还没有该笔记文档（可能尚未同步过），退化为全量同步
            log.info("==> ES 中不存在笔记文档，转为全量同步, noteId: {}, error: {}", noteId, e.getMessage());
            syncNoteIndex(Long.valueOf(noteId));
        }
    }

    /**
     * 把计数写入待更新的文档字段（Canal 取出的是字符串，需要转成数字）
     */
    private void putNumberIfPresent(Map<String, Object> doc, String field, Object value) {
        if (Objects.isNull(value)) return;

        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return;

        try {
            doc.put(field, Integer.valueOf(text));
        } catch (NumberFormatException e) {
            log.warn("==> 计数字段转换数字失败, field: {}, value: {}", field, value);
        }
    }

    /**
     * 处理用户表事件：新增 / 更新时全量刷新该用户的 ES 文档
     */
    private void handleUserEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws IOException {
        if (eventType == CanalEntry.EventType.DELETE) {
            return;
        }

        Object userIdValue = columnMap.get("id");
        if (Objects.isNull(userIdValue)) {
            return;
        }

        syncUserIndex(Long.valueOf(String.valueOf(userIdValue)));
    }

    /**
     * 处理用户计数表事件：局部更新 ES 中的粉丝数 / 笔记数
     */
    private void handleUserCountEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws IOException {
        if (eventType == CanalEntry.EventType.DELETE) {
            return;
        }

        Object userIdValue = columnMap.get("user_id");
        if (Objects.isNull(userIdValue)) {
            return;
        }

        String userId = String.valueOf(userIdValue);

        Map<String, Object> doc = new HashMap<>();
        putNumberIfPresent(doc, UserIndex.FIELD_USER_NOTE_TOTAL, columnMap.get("note_total"));
        putNumberIfPresent(doc, UserIndex.FIELD_USER_FANS_TOTAL, columnMap.get("fans_total"));

        if (doc.isEmpty()) {
            return;
        }

        UpdateRequest updateRequest = new UpdateRequest(UserIndex.NAME, userId).doc(doc);
        try {
            restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT);
        } catch (DocumentMissingException | ElasticsearchStatusException e) {
            // 索引中还没有该用户文档（可能尚未同步过），退化为全量同步
            log.info("==> ES 中不存在用户文档，转为全量同步, userId: {}, error: {}", userId, e.getMessage());
            syncUserIndex(Long.valueOf(userId));
        }
    }

    /**
     * 同步用户索引
     */
    private void syncUserIndex(Long userId) throws IOException {
        List<Map<String, Object>> result = selectMapper.selectEsUserIndexData(userId);

        for (Map<String, Object> record : result) {
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            indexRequest.id(String.valueOf(record.get(UserIndex.FIELD_USER_ID)));
            indexRequest.source(record);
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 同步笔记索引
     */
    private void syncNoteIndex(Long noteId) throws IOException {
        // 从数据库查询 Elasticsearch 索引数据
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId);

        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : result) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID))));
            // 设置文档的内容：由数据库记录构建（含 cover 封面字段）
            indexRequest.source(NoteIndexDocBuilder.build(recordMap));
            // 将数据写入 Elasticsearch 索引
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 将列数据解析为 Map
     */
    private Map<String, Object> parseColumns2Map(List<CanalEntry.Column> columns) {
        // 初始化一个空的 Map，用于存储列数据
        Map<String, Object> map = new HashMap<>();
        columns.forEach(column -> {
            if (Objects.isNull(column)) return;
            map.put(column.getName(), column.getValue());
        });
        return map;
    }

}
