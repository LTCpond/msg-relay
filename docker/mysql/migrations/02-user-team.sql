-- 旧库升级专用；新库 01-schema.sql 已包含最终结构。
-- 仅适用于仍具有 uk_team_username、head_id、t_join_request 的旧版本库。
ALTER TABLE t_user
    ADD COLUMN job_number VARCHAR(32) COMMENT '工号(企业内唯一)' AFTER password,
    MODIFY COLUMN team_id BIGINT COMMENT '所属团队ID',
    MODIFY COLUMN status TINYINT DEFAULT 1 COMMENT '状态: 1正常, 2封禁',
    DROP INDEX uk_team_username,
    ADD UNIQUE KEY uk_username (username),
    ADD UNIQUE KEY uk_team_job_number (team_id, job_number);

ALTER TABLE t_department DROP COLUMN IF EXISTS head_id;
DROP TABLE IF EXISTS t_join_request;

CREATE TABLE IF NOT EXISTS t_team_admin (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    team_id BIGINT NOT NULL COMMENT '团队ID',
    user_id BIGINT NOT NULL COMMENT '管理员ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_team_user (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队管理员表';
