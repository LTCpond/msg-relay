-- ============================================================
-- Msg Relay 企业 IM 系统 - 数据库初始化脚本
-- ============================================================

USE msg_relay;

-- ==================== 用户服务 (user-service) ====================

CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(32) NOT NULL COMMENT '用户名(全局唯一)',
    password VARCHAR(256) NOT NULL COMMENT 'BCrypt密码',
    job_number VARCHAR(32) COMMENT '工号(企业内唯一)',
    nickname VARCHAR(64) COMMENT '昵称',
    avatar VARCHAR(512) COMMENT '头像URL',
    email VARCHAR(128) COMMENT '邮箱',
    phone VARCHAR(32) COMMENT '手机号',
    team_id BIGINT COMMENT '所属团队ID',
    dept_id BIGINT COMMENT '所属部门ID',
    status TINYINT DEFAULT 1 COMMENT '状态: 1正常, 2封禁',
    last_login_at DATETIME COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_team_job_number (team_id, job_number),
    INDEX idx_team (team_id),
    INDEX idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS t_team (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '团队ID',
    name VARCHAR(128) NOT NULL COMMENT '团队名称',
    logo VARCHAR(512) COMMENT '团队Logo',
    owner_id BIGINT NOT NULL COMMENT '所有者用户ID',
    max_members INT DEFAULT 200 COMMENT '最大成员数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队表';

CREATE TABLE IF NOT EXISTS t_department (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '部门ID',
    team_id BIGINT NOT NULL COMMENT '所属团队ID',
    name VARCHAR(64) NOT NULL COMMENT '部门名称',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE IF NOT EXISTS t_login_device (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    device_id VARCHAR(128) NOT NULL COMMENT '设备ID',
    device_type VARCHAR(32) COMMENT '设备类型',
    ip VARCHAR(64) COMMENT '登录IP',
    last_active_at DATETIME COMMENT '最后活跃时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user (user_id),
    UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录设备表';

CREATE TABLE IF NOT EXISTS t_team_admin (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    team_id BIGINT NOT NULL COMMENT '团队ID',
    user_id BIGINT NOT NULL COMMENT '管理员ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_team_user (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队管理员表';

-- ==================== IM服务 (im-service) ====================

CREATE TABLE IF NOT EXISTS t_message (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID(雪花算法)',
    client_msg_id VARCHAR(64) NOT NULL COMMENT '客户端幂等消息ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    conversation_id BIGINT NOT NULL COMMENT '全局会话ID',
    msg_type TINYINT NOT NULL COMMENT '消息类型: 1文本 2图片 3文件 4语音 5系统',
    content TEXT COMMENT '消息内容',
    extra_json TEXT COMMENT '扩展信息JSON',
    media_meta_json TEXT COMMENT '媒体元数据JSON',
    status TINYINT DEFAULT 0 COMMENT '状态:0发送中 1已发送 2已投递 3已读 4已撤回',
    read_count INT DEFAULT 0 COMMENT '已读人数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_msg_id (msg_id),
    UNIQUE KEY uk_sender_client_msg (sender_id, client_msg_id),
    INDEX idx_sender (sender_id),
    INDEX idx_conversation_msg (conversation_id, msg_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    type TINYINT NOT NULL COMMENT '会话类型: 1单聊 2群聊',
    direct_user_low BIGINT COMMENT '单聊双方中较小的用户ID',
    direct_user_high BIGINT COMMENT '单聊双方中较大的用户ID',
    group_id BIGINT COMMENT '群聊对应的群ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_direct_users (type, direct_user_low, direct_user_high),
    UNIQUE KEY uk_group_conversation (type, group_id),
    CONSTRAINT chk_conversation_target CHECK (
        (type = 1 AND direct_user_low IS NOT NULL AND direct_user_high IS NOT NULL AND group_id IS NULL)
        OR (type = 2 AND direct_user_low IS NULL AND direct_user_high IS NULL AND group_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局会话主体表';

CREATE TABLE IF NOT EXISTS t_user_conversation (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT NOT NULL COMMENT '全局会话ID',
    last_msg_id BIGINT COMMENT '最后一条消息ID',
    unread_count INT DEFAULT 0 COMMENT '未读数',
    last_read_time DATETIME COMMENT '最后已读时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_user_conversation (user_id, conversation_id),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话列表投影';

CREATE TABLE IF NOT EXISTS t_user_message_hide (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_msg (user_id, msg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息隐藏记录表';

-- ==================== 消息可靠性 (msg-reliability) ====================

CREATE TABLE IF NOT EXISTS t_msg_sync (
    user_id BIGINT NOT NULL PRIMARY KEY COMMENT '用户ID',
    version BIGINT DEFAULT 0 COMMENT '消息版本号',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息同步版本号';

CREATE TABLE IF NOT EXISTS t_msg_ack (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    status TINYINT COMMENT '状态:2已投递 3已读',
    ack_at DATETIME COMMENT '确认时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_msg_user (msg_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息ACK记录';

CREATE TABLE IF NOT EXISTS t_msg_read_bitmap (
    msg_id BIGINT NOT NULL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    delivered_bitmap VARBINARY(512),
    delivered_count INT NOT NULL DEFAULT 0,
    read_bitmap VARBINARY(512),
    read_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息ACK Bitmap快照';

-- ==================== 群组服务 (group-service) ====================

CREATE TABLE IF NOT EXISTS t_group (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '群组ID',
    conversation_id BIGINT NOT NULL COMMENT '全局会话ID',
    team_id BIGINT COMMENT '所属团队ID',
    name VARCHAR(128) NOT NULL COMMENT '群名称',
    avatar VARCHAR(512) COMMENT '群头像',
    owner_id BIGINT NOT NULL COMMENT '群主ID',
    announcement TEXT COMMENT '群公告',
    max_members INT DEFAULT 200 COMMENT '最大成员数',
    is_muted_all TINYINT DEFAULT 0 COMMENT '是否全员禁言',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_group_conversation (conversation_id),
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组表';

CREATE TABLE IF NOT EXISTS t_group_member (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    group_id BIGINT NOT NULL COMMENT '群组ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role TINYINT DEFAULT 3 COMMENT '角色: 1群主 2管理员 3普通成员',
    is_muted TINYINT DEFAULT 0 COMMENT '是否禁言',
    mute_expire_at DATETIME COMMENT '禁言过期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_group (group_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';

CREATE TABLE IF NOT EXISTS t_group_member_index (
    id BIGINT NOT NULL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_index INT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    joined_at DATETIME,
    left_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id),
    UNIQUE KEY uk_group_member_index (group_id, member_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员Bitmap位号';

CREATE TABLE IF NOT EXISTS t_group_member_index_seq (
    group_id BIGINT NOT NULL PRIMARY KEY,
    next_index INT NOT NULL DEFAULT -1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员Bitmap位号序列';

CREATE TABLE IF NOT EXISTS t_search_index_failure (
    msg_id BIGINT NOT NULL PRIMARY KEY,
    document_json JSON NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NOT NULL,
    last_error VARCHAR(1000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_next_retry (next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ES索引失败补偿表';
