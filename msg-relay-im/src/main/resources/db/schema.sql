-- 消息表
CREATE TABLE IF NOT EXISTS t_message (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID(雪花算法)',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT NOT NULL COMMENT '接收者ID',
    receiver_type TINYINT NOT NULL COMMENT '接收者类型: 1单聊, 2群聊',
    msg_type TINYINT NOT NULL COMMENT '消息类型: 1文本, 2图片, 3文件, 4语音, 5系统',
    content TEXT COMMENT '消息内容',
    extra_json TEXT COMMENT '扩展信息JSON',
    status TINYINT DEFAULT 0 COMMENT '状态: 0发送中, 1已发送, 2已投递, 3已读, 4已撤回',
    read_count INT DEFAULT 0 COMMENT '已读人数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_msg_id (msg_id),
    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id, receiver_type),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 会话表
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    target_id BIGINT NOT NULL COMMENT '目标ID(用户/群)',
    target_type TINYINT NOT NULL COMMENT '目标类型: 1单聊, 2群聊',
    last_msg_id BIGINT COMMENT '最后一条消息ID',
    unread_count INT DEFAULT 0 COMMENT '未读数',
    last_read_time DATETIME COMMENT '最后已读时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_user_target (user_id, target_id, target_type),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 用户消息隐藏记录表
CREATE TABLE IF NOT EXISTS t_user_message_hide (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    msg_id BIGINT NOT NULL COMMENT '消息ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_msg (user_id, msg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息隐藏记录表';

