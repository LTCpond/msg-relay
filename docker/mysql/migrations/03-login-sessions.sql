-- 旧库升级专用；新库 schema 已包含最终结构，无需执行本文件。
-- 停止应用写入并备份 t_login_device 后，在目标数据库中执行本脚本。
-- 不指定 USE，沿用调用方选定的数据库。MySQL 8.0。
-- 无 deviceId 的旧记录无法用于新认证；重复记录保留有效且最近活跃的一条。
DELETE FROM t_login_device WHERE device_id IS NULL OR TRIM(device_id) = '';
DELETE old_device FROM t_login_device old_device
JOIN t_login_device newer ON old_device.user_id = newer.user_id
    AND old_device.device_id = newer.device_id
    AND (
        COALESCE(newer.deleted, 0) < COALESCE(old_device.deleted, 0)
        OR (COALESCE(newer.deleted, 0) = COALESCE(old_device.deleted, 0)
            AND (COALESCE(newer.last_active_at, '1000-01-01') > COALESCE(old_device.last_active_at, '1000-01-01')
                OR (COALESCE(newer.last_active_at, '1000-01-01') = COALESCE(old_device.last_active_at, '1000-01-01')
                    AND newer.id > old_device.id)))
    );

SET @login_session_drop_token = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 't_login_device' AND column_name = 'refresh_token'),
    'ALTER TABLE t_login_device DROP COLUMN refresh_token', 'SELECT 1');
PREPARE login_session_stmt FROM @login_session_drop_token;
EXECUTE login_session_stmt;
DEALLOCATE PREPARE login_session_stmt;

ALTER TABLE t_login_device MODIFY device_id VARCHAR(128) NOT NULL COMMENT '设备ID';
SET @login_session_add_index = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 't_login_device' AND index_name = 'uk_user_device'),
    'SELECT 1', 'ALTER TABLE t_login_device ADD UNIQUE KEY uk_user_device (user_id, device_id)');
PREPARE login_session_stmt FROM @login_session_add_index;
EXECUTE login_session_stmt;
DEALLOCATE PREPARE login_session_stmt;
