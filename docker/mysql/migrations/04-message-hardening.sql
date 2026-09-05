-- 旧库升级专用：执行前请备份；新库 01-schema.sql 已包含最终结构。
-- client_msg_id 对历史行保持 NULL，新写入由应用校验为必填；MySQL UNIQUE 允许多个 NULL。
ALTER TABLE t_message
    ADD COLUMN client_msg_id VARCHAR(64) NULL COMMENT '客户端幂等消息ID' AFTER msg_id,
    ADD COLUMN media_meta_json TEXT NULL COMMENT '媒体元数据JSON' AFTER extra_json,
    ADD UNIQUE KEY uk_msg_id (msg_id),
    ADD UNIQUE KEY uk_sender_client_msg (sender_id, client_msg_id);

CREATE TABLE IF NOT EXISTS t_msg_read_bitmap (
    msg_id BIGINT NOT NULL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    delivered_bitmap VARBINARY(512), delivered_count INT NOT NULL DEFAULT 0,
    read_bitmap VARBINARY(512), read_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息ACK Bitmap快照';

CREATE TABLE IF NOT EXISTS t_group_member_index (
    id BIGINT NOT NULL PRIMARY KEY, group_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    member_index INT NOT NULL, status TINYINT NOT NULL DEFAULT 1,
    joined_at DATETIME, left_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id),
    UNIQUE KEY uk_group_member_index (group_id, member_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员Bitmap位号';

CREATE TABLE IF NOT EXISTS t_group_member_index_seq (
    group_id BIGINT NOT NULL PRIMARY KEY, next_index INT NOT NULL DEFAULT -1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员Bitmap位号序列';

-- 为升级前已经存在的活跃群成员补齐位号；每个群从当前最大位号后继续分配。
INSERT INTO t_group_member_index
    (id, group_id, user_id, member_index, status, joined_at, created_at, updated_at)
SELECT UUID_SHORT(), gm.group_id, gm.user_id,
       COALESCE((SELECT MAX(existing.member_index)
                 FROM t_group_member_index existing
                 WHERE existing.group_id = gm.group_id), -1)
       + ROW_NUMBER() OVER (PARTITION BY gm.group_id ORDER BY gm.created_at, gm.id),
       1, gm.created_at, NOW(), NOW()
FROM t_group_member gm
WHERE gm.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM t_group_member_index mapped
      WHERE mapped.group_id = gm.group_id AND mapped.user_id = gm.user_id
  );

INSERT INTO t_group_member_index_seq (group_id, next_index)
SELECT group_id, MAX(member_index) FROM t_group_member_index GROUP BY group_id
ON DUPLICATE KEY UPDATE next_index = GREATEST(next_index, VALUES(next_index));

CREATE TABLE IF NOT EXISTS t_search_index_failure (
    msg_id BIGINT NOT NULL PRIMARY KEY, document_json JSON NOT NULL,
    retry_count INT NOT NULL DEFAULT 0, next_retry_at DATETIME NOT NULL,
    last_error VARCHAR(1000), created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_next_retry (next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ES索引失败补偿表';
