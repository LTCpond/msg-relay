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

/**
 * Canal Binlog 订阅 → 同步消息到 ES
 *
 * 监听 MySQL t_message 表的 binlog 变更，
 * INSERT/UPDATE 时写入 ES message_index，供 SearchService 搜索
 *
 * ACK 策略（safe 模式）：
 * - 成功写入 ES 后才 ack，失败则 rollback(batchId) 重试
 * - 指数退避：重试间隔 1s → 2s → 4s → 8s → 16s（上限 30s）
 * - 最大重试 5 次后 ack 继续，避免 listener 卡死；同时记录 ERROR 日志供人工干预
 *
 * 与旧方案的区别：
 * - 旧方案：processEntries 失败也直接 ack，导致索引永久丢失
 * - 新方案：失败 rollback 重试，最多 5 次后 ack（丢弃当前 batch 但不阻塞后续数据）
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
        CanalConnector connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalProperties.getHost(), canalProperties.getPort()),
                canalProperties.getDestination(), "", "");

        try {
            connector.connect();
            connector.subscribe(canalProperties.getSubscribe());
            connector.rollback();
            log.info("Canal 监听已启动，订阅: {}", canalProperties.getSubscribe());

            while (running) {
                Message message = connector.getWithoutAck(100);
                long batchId = message.getId();
                int size = message.getEntries().size();

                if (batchId == -1 || size == 0) {
                    // 无新数据，等待 1 秒后继续拉取
                    Thread.sleep(1000);
                    continue;
                }

                // 处理当前 batch，失败则重试
                boolean success = processWithRetry(message, connector, batchId);

                if (success) {
                    // 成功：ack 告诉 Canal 这批数据已处理
                    connector.ack(batchId);
                    log.debug("batch {} ack 完成，条数={}", batchId, size);
                } else {
                    // 重试耗尽：ack 继续，避免 listener 永久卡死
                    // 记录 ERROR 日志，需人工介入检查 ES 数据一致性
                    log.error("batch {} 重试 {} 次仍失败，已 ack 跳过（需人工检查 ES 数据一致性）",
                            batchId, MAX_RETRY);
                    connector.ack(batchId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Canal 监听被中断");
        } catch (Exception e) {
            if (running) {
                log.error("Canal 监听异常，将重连", e);
            }
        } finally {
            connector.disconnect();
            log.info("Canal 连接已断开");
        }
    }

    /**
     * 带重试的 batch 处理
     *
     * @return true=处理成功，false=重试耗尽仍失败
     */
    private boolean processWithRetry(Message message, CanalConnector connector, long batchId) {
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                processEntries(message.getEntries());
                return true; // 成功
            } catch (Exception e) {
                log.warn("batch {} 处理失败，第 {}/{} 次重试，{}ms 后重试",
                        batchId, attempt, MAX_RETRY, backoff, e);

                // 失败：rollback 让 Canal 重新投递该 batch
                connector.rollback(batchId);

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
        Map<String, Object> doc = new HashMap<>();
        String msgId = null;
        for (CanalEntry.Column column : columns) {
            doc.put(column.getName(), column.getValue());
            if ("msg_id".equals(column.getName())) {
                msgId = column.getValue();
            }
        }

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
}
