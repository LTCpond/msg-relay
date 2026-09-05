package com.ltcpond.msgrelay.search.listener;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** ES 写入失败表，相当于可查询、可重放的本地 DLQ。 */
@Repository
public class SearchIndexFailureRepository {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public void save(Long msgId, String documentJson, String error) {
        jdbcTemplate.update("""
                INSERT INTO t_search_index_failure
                    (msg_id, document_json, retry_count, next_retry_at, last_error, created_at, updated_at)
                VALUES (?, ?, 0, NOW(), ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE document_json=VALUES(document_json),
                    next_retry_at=NOW(), last_error=VALUES(last_error), updated_at=NOW()
                """, msgId, documentJson, abbreviate(error));
    }

    public List<FailureRecord> findDue(int limit) {
        return jdbcTemplate.query("""
                SELECT msg_id, CAST(document_json AS CHAR), retry_count
                FROM t_search_index_failure
                WHERE next_retry_at <= NOW()
                ORDER BY next_retry_at ASC LIMIT ?
                """, (rs, row) -> new FailureRecord(rs.getLong(1), rs.getString(2), rs.getInt(3)), limit);
    }

    public void markRetry(Long msgId, int retryCount, String error) {
        long delaySeconds = Math.min(3600, 1L << Math.min(retryCount, 11));
        jdbcTemplate.update("""
                UPDATE t_search_index_failure
                SET retry_count=?, next_retry_at=DATE_ADD(NOW(), INTERVAL ? SECOND),
                    last_error=?, updated_at=NOW() WHERE msg_id=?
                """, retryCount, delaySeconds, abbreviate(error), msgId);
    }

    public void delete(Long msgId) {
        jdbcTemplate.update("DELETE FROM t_search_index_failure WHERE msg_id=?", msgId);
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public record FailureRecord(Long msgId, String documentJson, int retryCount) {}
}
