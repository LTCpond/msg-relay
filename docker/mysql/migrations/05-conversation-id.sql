-- ============================================================
-- 旧库升级：单聊/群聊统一使用 conversation_id
-- 执行前请备份数据库；本脚本仅面向旧版 receiver_id/receiver_type 结构。
-- ============================================================

USE msg_relay;

RENAME TABLE t_conversation TO t_user_conversation_legacy;

CREATE TABLE t_conversation (
    id BIGINT NOT NULL PRIMARY KEY,
    type TINYINT NOT NULL,
    direct_user_low BIGINT NULL,
    direct_user_high BIGINT NULL,
    group_id BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_direct_users (type, direct_user_low, direct_user_high),
    UNIQUE KEY uk_group_conversation (type, group_id),
    CONSTRAINT chk_conversation_target CHECK (
        (type = 1 AND direct_user_low IS NOT NULL AND direct_user_high IS NOT NULL AND group_id IS NULL)
        OR (type = 2 AND direct_user_low IS NULL AND direct_user_high IS NULL AND group_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局会话主体表';

-- 每个历史单聊用户对归并为一个会话。
INSERT INTO t_conversation
    (id, type, direct_user_low, direct_user_high, created_at, updated_at, deleted)
SELECT MIN(id), 1, LEAST(user_id, target_id), GREATEST(user_id, target_id),
       MIN(created_at), MAX(updated_at), 0
FROM t_user_conversation_legacy
WHERE target_type = 1
GROUP BY LEAST(user_id, target_id), GREATEST(user_id, target_id);

-- 旧群组 ID 由全局 Snowflake 生成，升级时可安全复用为该群的会话 ID。
INSERT INTO t_conversation
    (id, type, group_id, created_at, updated_at, deleted)
SELECT id, 2, id, created_at, updated_at, deleted
FROM t_group;

CREATE TABLE t_user_conversation (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    last_msg_id BIGINT NULL,
    unread_count INT DEFAULT 0,
    last_read_time DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_user_conversation (user_id, conversation_id),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话列表投影';

INSERT INTO t_user_conversation
    (id, user_id, conversation_id, last_msg_id, unread_count, last_read_time,
     created_at, updated_at, deleted)
SELECT legacy.id, legacy.user_id, conversation.id, legacy.last_msg_id,
       legacy.unread_count, legacy.last_read_time, legacy.created_at,
       legacy.updated_at, legacy.deleted
FROM t_user_conversation_legacy legacy
JOIN t_conversation conversation
  ON (legacy.target_type = 1
      AND conversation.type = 1
      AND conversation.direct_user_low = LEAST(legacy.user_id, legacy.target_id)
      AND conversation.direct_user_high = GREATEST(legacy.user_id, legacy.target_id))
  OR (legacy.target_type = 2
      AND conversation.type = 2
      AND conversation.group_id = legacy.target_id);

-- 即使尚未发过消息，群成员也应有会话列表项。
INSERT IGNORE INTO t_user_conversation
    (id, user_id, conversation_id, unread_count, created_at, updated_at, deleted)
SELECT member.id, member.user_id, conversation.id, 0,
       member.created_at, member.updated_at, member.deleted
FROM t_group_member member
JOIN t_conversation conversation
  ON conversation.type = 2 AND conversation.group_id = member.group_id;

ALTER TABLE t_message ADD COLUMN conversation_id BIGINT NULL AFTER sender_id;

UPDATE t_message message
JOIN t_conversation conversation
  ON conversation.type = 1
 AND conversation.direct_user_low = LEAST(message.sender_id, message.receiver_id)
 AND conversation.direct_user_high = GREATEST(message.sender_id, message.receiver_id)
SET message.conversation_id = conversation.id
WHERE message.receiver_type = 1;

UPDATE t_message message
JOIN t_conversation conversation
  ON conversation.type = 2 AND conversation.group_id = message.receiver_id
SET message.conversation_id = conversation.id
WHERE message.receiver_type = 2;

-- 如此查询有结果，说明存在无法归属会话的历史消息，应停止并人工处理。
SELECT msg_id, sender_id, receiver_id, receiver_type
FROM t_message
WHERE conversation_id IS NULL;

ALTER TABLE t_message
    MODIFY conversation_id BIGINT NOT NULL,
    DROP INDEX idx_receiver,
    DROP COLUMN receiver_id,
    DROP COLUMN receiver_type,
    ADD INDEX idx_conversation_msg (conversation_id, msg_id);

ALTER TABLE t_group ADD COLUMN conversation_id BIGINT NULL AFTER id;
UPDATE t_group SET conversation_id = id WHERE conversation_id IS NULL;
ALTER TABLE t_group
    MODIFY conversation_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_group_conversation (conversation_id);

DROP TABLE t_user_conversation_legacy;

-- 升级完成后需要重建 Elasticsearch message_index，以回填 conversation_id。
