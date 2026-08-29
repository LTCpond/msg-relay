package com.ltcpond.msgrelay.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.ltcpond.msgrelay.common.cache.MultiLevelCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 搜索服务 — ES 全文检索
 *
 * 查询流程: ES bool 查询 → 过滤当前用户隐藏的消息 → 按时间倒序
 * 搜索不缓存（低频、参数多变、命中率极低），隐藏集合走三级缓存
 * 数据来源: Canal 监听 MySQL binlog → 同步到 message_index
 */
@Slf4j
@Service
public class SearchService {

    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Resource
    private MultiLevelCache cache;

    private static final String INDEX_NAME = "message_index";

    /** 在指定会话内搜索消息，过滤当前用户隐藏的消息 */
    public List<Map<String, Object>> search(String keyword, Long receiverId, int receiverType,
                                            Long userId, int page, int size) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m.match(mt -> mt
                                                    .field("content")
                                                    .query(keyword)
                                                    .analyzer("ik_smart")))
                                            .filter(f -> f.term(t -> t
                                                    .field("receiver_id")
                                                    .value(receiverId)))
                                            .filter(f -> f.term(t -> t
                                                    .field("receiver_type")
                                                    .value(receiverType)))
                                            .filter(f -> f.term(t -> t
                                                    .field("deleted")
                                                    .value("0")))
                                    )
                            )
                            .sort(srt -> srt.field(f -> f
                                    .field("created_at")
                                    .order(SortOrder.Desc)))
                            .from((page - 1) * size)
                            .size(size),
                    Map.class
            );

            Set<String> hiddenSet = getHiddenSet(userId);
            List<Map<String, Object>> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    Object msgId = source.get("msg_id");
                    if (hiddenSet.contains(String.valueOf(msgId))) {
                        continue;
                    }
                    results.add(source);
                }
            }
            return results;
        } catch (IOException e) {
            log.error("Search failed: keyword={}, receiverId={}", keyword, receiverId, e);
            return List.of();
        }
    }

    /** 用户隐藏消息集合 — 三级缓存，隐藏操作时主动失效 */
    @SuppressWarnings("unchecked")
    private Set<String> getHiddenSet(Long userId) {
        String key = "user:hidden:msgs:" + userId;
        Set<String> result = cache.get(key, Set.class,
                k -> Collections.emptySet(), 300);
        return result != null ? result : Collections.emptySet();
    }
}
