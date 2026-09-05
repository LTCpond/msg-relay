package com.ltcpond.msgrelay.search.listener;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.ltcpond.msgrelay.common.config.CanalProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;

/**
 * Canal Binlog 订阅 → 同步消息到 ES
 *
 * 监听 MySQL t_message 表的 binlog 变更，
 * INSERT/UPDATE 时写入 ES message_index，供 SearchService 搜索
 *
 * ACK 策略（safe 模式）：
 * - 成功写入 ES 后才 ack，失败则 rollback(batchId) 重试
 * - 指数退避：重试间隔 1s → 2s → 4s → 8s → 16s（上限 30s）
 * - 最大重试 5 次后写入失败表再 ack，由定时 reconciliation 持续重放
 *
 * 与旧方案的区别：
 * - 旧方案：processEntries 失败也直接 ack，导致索引永久丢失
 * - 新方案：连接外层持续重连；失败 batch 进入可重放失败表，不留下永久索引缺口
 */
@Slf4j
@Component
public class CanalListener {

    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Resource
    private CanalProperties canalProperties;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private volatile CanalConnector connector;

    @Resource
    private SearchIndexFailureRepository failureRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INDEX_NAME = "message_index";

    /** 单 batch 最大重试次数，超过后 ack 继续（避免 listener 卡死） */
    private static final int MAX_RETRY = 5;

    /** 初始重试间隔（毫秒），每次翻倍，上限 30s */
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    @PostConstruct
    public void init() {
        ensureIndex();
        executor.submit(this::startListener);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        executor.shutdownNow();
        if (connector != null) {
            connector.disconnect();
        }
        log.info("CanalListener 已停止");
    }

    /** 启动时确保 ES 索引存在（IK 分词器配置） */
    private void ensureIndex() {
        try {
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(INDEX_NAME));
            if (elasticsearchClient.indices().exists(existsRequest).value()) {
                log.info("索引 {} 已存在，跳过创建", INDEX_NAME);
                return;
            }
            CreateIndexRequest request = CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .settings(s -> s
                            .analysis(a -> a
                                    .analyzer("ik_smart_analyzer", ik -> ik
                                            .custom(ikc -> ikc.tokenizer("ik_smart")))
                                    .analyzer("ik_max_word_analyzer", ik -> ik
                                            .custom(ikc -> ikc.tokenizer("ik_max_word")))
                            )
                    )
                    .mappings(m -> m
                            .properties("content", p -> p
                                    .text(t -> t
                                            .analyzer("ik_max_word")
                                            .searchAnalyzer("ik_smart")))
                    )
            );
            elasticsearchClient.indices().create(request);
            log.info("创建索引 {} 完成（IK 分词器）", INDEX_NAME);
        } catch (Exception e) {
            log.error("创建索引 {} 失败", INDEX_NAME, e);
        }
    }

    /**
     * 连接 Canal Server，订阅配置表，循环消费 binlog
     *
     * 关键变更：ack 策略从"无条件 ack"改为"成功才 ack，失败 rollback 重试"
     */
    private void startListener() {
        while (running) {
            try {
                connectAndConsume();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) log.error("Canal 监听异常，5 秒后重连", e);
            } finally {
                if (connector != null) connector.disconnect();
                connector = null;
            }
            if (running) {
                try { Thread.sleep(5000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private void connectAndConsume() throws Exception {
        connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalProperties.getHost(), canalProperties.getPort()),
                canalProperties.getDestination(), "", "");
        connector.connect();
        connector.subscribe(canalProperties.getSubscribe());
        connector.rollback();
        log.info("Canal 监听已启动，订阅: {}", canalProperties.getSubscribe());
        while (running) {
            Message message = connector.getWithoutAck(100);
            long batchId = message.getId();
            if (batchId == -1 || message.getEntries().isEmpty()) {
                Thread.sleep(1000);
                continue;
            }
            if (!processWithRetry(message, batchId)) {
                persistFailures(message.getEntries(), "ES write failed after " + MAX_RETRY + " attempts");
                log.error("batch {} 重试耗尽，已写入失败表等待 reconciliation", batchId);
            }
            connector.ack(batchId);
        }
    }

    /**
     * 带重试的 batch 处理
     *
     * @return true=处理成功，false=重试耗尽仍失败
     */
    private boolean processWithRetry(Message message, long batchId) {
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                processEntries(message.getEntries());
                return true; // 成功
            } catch (Exception e) {
                log.warn("batch {} 处理失败，第 {}/{} 次重试，{}ms 后重试",
                        batchId, attempt, MAX_RETRY, backoff, e);

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                // 指数退避，上限 MAX_BACKOFF_MS
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }

        return false; // 重试耗尽
    }

    private void persistFailures(List<CanalEntry.Entry> entries, String error) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA
                    || !"t_message".equals(entry.getHeader().getTableName())) continue;
            CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            if (change.getEventType() != CanalEntry.EventType.INSERT
                    && change.getEventType() != CanalEntry.EventType.UPDATE) continue;
            for (CanalEntry.RowData row : change.getRowDatasList()) {
                Map<String, Object> doc = toDocument(row.getAfterColumnsList());
                Object msgId = doc.get("msg_id");
                if (msgId != null) {
                    failureRepository.save(Long.valueOf(msgId.toString()),
                            objectMapper.writeValueAsString(doc), error);
                }
            }
        }
    }

    /**
     * 解析 binlog 事件，INSERT/UPDATE 触发同步
     * 逻辑删除通过 deleted 字段过滤
     *
     * 注意：CanalProperties.subscribe 可能包含多个表的正则，
     * 这里只处理 t_message 表，其他表事件直接跳过。
     */
    private void processEntries(List<CanalEntry.Entry> entries) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() == CanalEntry.EntryType.ROWDATA) {
                // 只处理 t_message 表，其他表（如 t_user, t_department 等）跳过
                if (!"t_message".equals(entry.getHeader().getTableName())) {
                    continue;
                }
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                CanalEntry.EventType eventType = rowChange.getEventType();

                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    if (eventType == CanalEntry.EventType.INSERT || eventType == CanalEntry.EventType.UPDATE) {
                        syncToES(rowData.getAfterColumnsList());
                    }
                }
            }
        }
    }

    /**
     * 将 Canal 列数据转为 Map 写入 ES，以 msg_id 作为文档 ID
     *
     * 注意：写入失败时抛出异常，由 processWithRetry 捕获并重试
     */
    private void syncToES(List<CanalEntry.Column> columns) throws Exception {
        Map<String, Object> doc = toDocument(columns);
        String msgId = doc.get("msg_id") != null ? doc.get("msg_id").toString() : null;

        if (msgId != null && !doc.isEmpty()) {
            final String finalMsgId = msgId;
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(finalMsgId)
                    .document(doc)
            );
            // 写入 ES（失败时抛异常，由上层重试）
            elasticsearchClient.index(request);
            log.debug("同步消息到 ES: msgId={}", finalMsgId);
        }
    }

    private Map<String, Object> toDocument(List<CanalEntry.Column> columns) {
        Map<String, Object> doc = new HashMap<>();
        for (CanalEntry.Column column : columns) {
            String value = column.getValue();
            Object typedValue = value;
            if (!column.getIsNull()) {
                try {
                    typedValue = switch (column.getSqlType()) {
                        case Types.BIGINT -> Long.valueOf(value);
                        case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Integer.valueOf(value);
                        case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC ->
                                Double.valueOf(value);
                        default -> value;
                    };
                } catch (NumberFormatException ignored) {
                    typedValue = value;
                }
            } else {
                typedValue = null;
            }
            doc.put(column.getName(), typedValue);
        }
        return doc;
    }
}
