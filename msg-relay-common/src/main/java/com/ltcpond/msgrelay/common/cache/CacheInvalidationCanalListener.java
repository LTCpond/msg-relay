package com.ltcpond.msgrelay.common.cache;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.ltcpond.msgrelay.common.config.CanalProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Canal Binlog 订阅 → 自动失效缓存
 *
 * 监听 MySQL binlog，当被缓存的表发生 INSERT/UPDATE/DELETE 时，
 * 从 binlog 中提取 key 列，调用 MultiLevelCache.evict() 失效缓存。
 *
 * 解决 Cache Aside 模式的竞态窗口：Canal 读到 binlog 时 DB 事务已提交，
 * 不存在"更新 DB 后、删缓存前读到旧数据"的时机。
 */
@Slf4j
@Component
public class CacheInvalidationCanalListener {

    @Resource
    private MultiLevelCache multiLevelCache;

    @Resource
    private CanalProperties canalProperties;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private CanalConnector connector;

    @PostConstruct
    public void init() {
        executor.submit(this::startListener);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        executor.shutdownNow();
        if (connector != null) {
            connector.disconnect();
        }
        log.info("CacheInvalidationCanalListener stopped.");
    }

    private void startListener() {
        while (running) {
            try {
                connector = CanalConnectors.newSingleConnector(
                        new InetSocketAddress(canalProperties.getHost(), canalProperties.getPort()),
                        canalProperties.getDestination(), "", "");
                connector.connect();
                connector.subscribe(canalProperties.getSubscribe());
                connector.rollback();
                log.info("CacheInvalidationCanalListener started, subscribe={}", canalProperties.getSubscribe());

                while (running) {
                    // 拉取 100 条数据，但不进行 ACK 确认，等处理完再 ACK
                    Message message = connector.getWithoutAck(100);
                    // 拿到批次 ID
                    long batchId = message.getId();
                    if (batchId == -1 || message.getEntries().isEmpty()) {
                        Thread.sleep(1000);
                        continue;
                    }

                    try {
                        processEntries(message.getEntries());
                        // 处理完后 ACK，告诉 Canal 这批数据已处理，可以丢弃
                        connector.ack(batchId);
                    } catch (Exception e) {
                        log.error("Process entries failed, batchId={}, will rollback and retry", batchId, e);
                        // 发生异常时回滚当前批次，供后续重试
                        connector.rollback(batchId);
                        Thread.sleep(1000); // 稍微休眠，避免死循环狂抛异常
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("CacheInvalidationCanalListener connection error, will retry in 5 seconds", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                if (connector != null) {
                    connector.disconnect();
                }
            }
        }
    }

    private void processEntries(List<CanalEntry.Entry> entries) throws Exception {
        // 遍历每一条事件
        // entry: header（表名等元信息） + storeValue（事件类型和列数据）
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }

            // 解析 binlog 事件，获取表名、事件类型和列数据
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            String table = entry.getHeader().getTableName();
            // eventType: INSERT/UPDATE/DELETE/QUERY
            CanalEntry.EventType eventType = rowChange.getEventType();
            // 查询不需要同步
            if (eventType == CanalEntry.EventType.QUERY) {
                continue;
            }

            // rowDatasList: 一条 binlog 事件可能包含多行数据变动（批量更新），每行数据包含变动前后的列数据
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                if (eventType == CanalEntry.EventType.DELETE) {
                    evictKeys(table, rowData.getBeforeColumnsList());
                } else if (eventType == CanalEntry.EventType.INSERT) {
                    evictKeys(table, rowData.getAfterColumnsList());
                } else if (eventType == CanalEntry.EventType.UPDATE) {
                    // UPDATE 事件必须同时处理旧数据和新数据的列，确保关联缓存（如修改外键时）均被正确失效
                    evictKeys(table, rowData.getBeforeColumnsList());
                    evictKeys(table, rowData.getAfterColumnsList());
                }
            }
        }
    }

    /** 根据表名和列值生成并失效对应的缓存 key */
    private void evictKeys(String table, List<CanalEntry.Column> columns) {
        Set<String> keys = new HashSet<>();

        switch (table) {
            case "t_user" -> {
                String userId = null;
                String teamId = null;
                for (CanalEntry.Column col : columns) {
                    if ("id".equals(col.getName())) userId = col.getValue();
                    if ("team_id".equals(col.getName())) teamId = col.getValue();
                }
                if (userId != null) keys.add("user:id:" + userId);
                if (teamId != null) keys.add("team:members:" + teamId);
            }
            case "t_department" ->
                columns.stream()
                        .filter(c -> "team_id".equals(c.getName()))
                        .findFirst()
                        .ifPresent(col -> keys.add("dept:list:" + col.getValue()));
            case "t_message" ->
                columns.stream()
                        .filter(c -> "msg_id".equals(c.getName()))
                        .findFirst()
                        .ifPresent(col -> keys.add("msg:" + col.getValue()));
            case "t_group_member" ->
                columns.stream()
                        .filter(c -> "group_id".equals(c.getName()))
                        .findFirst()
                        .ifPresent(col -> keys.add("group:members:list:" + col.getValue()));
            case "t_conversation" -> {
                columns.stream()
                        .filter(c -> "user_id".equals(c.getName()))
                        .findFirst()
                        .ifPresent(col -> keys.add("conv:list:" + col.getValue()));
                columns.stream()
                        .filter(c -> "target_id".equals(c.getName()))
                        .findFirst()
                        .ifPresent(col -> keys.add("conv:list:" + col.getValue()));
            }
        }

        for (String key : keys) {
            multiLevelCache.evict(key);
            log.debug("Canal evicted cache: table={}, key={}", table, key);
        }
    }
}