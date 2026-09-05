-- 用户表
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
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删除, 1已删除',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_team_job_number (team_id, job_number),
    INDEX idx_team (team_id),
    INDEX idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 团队表
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

-- 部门表
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

CREATE TABLE IF NOT EXISTS t_team_admin (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    team_id BIGINT NOT NULL COMMENT '团队ID',
    user_id BIGINT NOT NULL COMMENT '管理员ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_team_user (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队管理员表';

-- 登录设备表
CREATE TABLE IF NOT EXISTS t_login_device (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    device_id VARCHAR(128) NOT NULL COMMENT '设备ID',
    device_type VARCHAR(32) COMMENT '设备类型: PC/ANDROID/IOS/WEB',
    ip VARCHAR(64) COMMENT '登录IP',
    last_active_at DATETIME COMMENT '最后活跃时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user (user_id),
    UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录设备表';
