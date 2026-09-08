-- 消息表
CREATE TABLE IF NOT EXISTS t_message (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID(雪花算法)',
    client_msg_id VARCHAR(64) NOT NULL COMMENT '客户端幂等消息ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    conversation_id BIGINT NOT NULL COMMENT '全局会话ID',
    msg_type TINYINT NOT NULL COMMENT '消息类型: 1文本, 2图片, 3文件, 4语音, 5系统',
    content TEXT COMMENT '消息内容',
    extra_json TEXT COMMENT '扩展信息JSON',
    media_meta_json TEXT COMMENT '媒体元数据JSON',
    status TINYINT DEFAULT 0 COMMENT '状态: 0发送中, 1已发送, 2已投递, 3已读, 4已撤回',
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

-- 会话表
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    type TINYINT NOT NULL COMMENT '会话类型: 1单聊, 2群聊',
    direct_user_low BIGINT COMMENT '单聊双方中较小的用户ID',
    direct_user_high BIGINT COMMENT '单聊双方中较大的用户ID',
    group_id BIGINT COMMENT '群聊对应的群ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_direct_users (type, direct_user_low, direct_user_high),
    UNIQUE KEY uk_group_conversation (type, group_id)
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

-- 用户消息隐藏记录表
CREATE TABLE IF NOT EXISTS t_user_message_hide (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_msg (user_id, msg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息隐藏记录表';
