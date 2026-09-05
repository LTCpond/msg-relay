package com.ltcpond.msgrelay.search.listener;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 定时重放 ES 失败表，直到索引写入成功。 */
@Slf4j
@Component
public class SearchIndexReconciliationTask {
    private static final String INDEX_NAME = "message_index";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource private ElasticsearchClient elasticsearchClient;
    @Resource private SearchIndexFailureRepository repository;

    @Scheduled(fixedDelayString = "${msg-relay.search.reconcile-delay-ms:60000}")
    public void reconcile() {
        for (SearchIndexFailureRepository.FailureRecord failure : repository.findDue(100)) {
            try {
                Map<String, Object> document = objectMapper.readValue(
                        failure.documentJson(), new TypeReference<>() {});
                elasticsearchClient.index(i -> i.index(INDEX_NAME)
                        .id(String.valueOf(failure.msgId())).document(document));
                repository.delete(failure.msgId());
            } catch (Exception e) {
                repository.markRetry(failure.msgId(), failure.retryCount() + 1, e.getMessage());
                log.warn("ES reconciliation failed: msgId={}, retry={}",
                        failure.msgId(), failure.retryCount() + 1, e);
            }
        }
    }
}
