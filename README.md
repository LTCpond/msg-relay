# Msg Relay

Msg Relay 是一个基于 Spring Boot 的即时通信后端，采用 Maven 多模块组织，包含用户与组织、单聊/群聊、WebSocket 实时推送、消息可靠性、文件上传和全文检索等能力。

## 功能特性

- 用户登录、双 Token 刷新与多设备管理
- 单聊、群聊、会话列表、消息撤回与用户侧隐藏
- Netty WebSocket 长连接、心跳检测与在线状态维护
- Redis Pub/Sub 跨节点推送与 Caffeine + Redis 多级缓存
- RocketMQ 事务消息、消费幂等、ACK 与异常消息补偿
- 大群推拉结合、群消息已读位图
- Canal 订阅 MySQL Binlog，并同步消息到 Elasticsearch
- 阿里云 OSS 文件上传与下载

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 基础框架 | Java 17、Spring Boot 3.4.5、Maven |
| 数据存储 | MySQL 8.0、Redis 7、Elasticsearch 8.14.1 |
| 数据访问 | MyBatis 3.0.4 |
| 消息与同步 | RocketMQ 5.2.0、Canal 1.1.7 |
| 长连接 | Netty WebSocket |
| 缓存与并发 | Caffeine、Redisson |
| 认证与存储 | JJWT、阿里云 OSS |

## 系统架构

```mermaid
flowchart LR
    Client[Web / 桌面端 / 移动端]
    API[Spring Boot REST API]
    WS[Netty WebSocket]
    MQ[RocketMQ]
    Redis[(Redis)]
    MySQL[(MySQL)]
    Canal[Canal]
    ES[(Elasticsearch)]
    OSS[阿里云 OSS]

    Client -->|HTTP| API
    Client <-->|WebSocket| WS
    API --> MySQL
    API --> Redis
    API --> MQ
    API --> OSS
    MQ --> WS
    WS --> Redis
    MySQL --> Canal --> ES
    API --> ES
```

## 模块说明

| 模块 | 说明 |
| --- | --- |
| `msg-relay-common` | 通用返回、异常、JWT、缓存、分布式锁和基础配置 |
| `msg-relay-user` | 用户、团队、部门、认证和设备管理 |
| `msg-relay-im` | 消息、会话、WebSocket、在线状态和消息投递 |
| `msg-relay-reliability` | 消息 ACK、已读记录与可靠性相关模型 |
| `msg-relay-group` | 群组、成员、权限和大群索引 |
| `msg-relay-file` | 阿里云 OSS 文件服务 |
| `msg-relay-search` | Elasticsearch 全文检索与 Canal 同步 |
| `msg-relay-starter` | 应用入口与运行时配置 |

## 快速开始

### 1. 环境要求

- JDK 17
- Maven 3.9+
- Docker Desktop，支持 Docker Compose v2
- Windows PowerShell（仅使用一键启动脚本时需要）

### 2. 获取项目

```bash
git clone <你的仓库地址>
cd msg-relay
```

### 3. 准备环境变量

复制配置模板并修改其中的示例值：

```powershell
Copy-Item .env.example .env
```

```bash
cp .env.example .env
```

Docker Compose 会自动读取根目录的 `.env`。直接通过 Maven 启动应用时，Spring Boot 不会自动加载该文件，需要在终端或 IDE 中设置同名环境变量；未设置时会采用 `application.yml` 中仅供本地开发使用的默认值。

关键配置如下：

| 变量 | 是否必需 | 说明 |
| --- | --- | --- |
| `DB_PASSWORD` | 建议 | MySQL root 密码，本地默认 `root` |
| `JWT_SECRET` | 生产必需 | JWT 签名密钥，至少 32 字节的高强度随机值 |
| `OSS_ACCESS_KEY_ID` | 文件功能必需 | 阿里云 OSS AccessKey ID |
| `OSS_ACCESS_KEY_SECRET` | 文件功能必需 | 阿里云 OSS AccessKey Secret |
| `OSS_ENDPOINT` | 否 | OSS Endpoint |
| `OSS_BUCKET_NAME` | 文件功能必需 | OSS Bucket 名称 |
| `NODE_ID` | 多实例必需 | WebSocket 节点标识 |
| `SNOWFLAKE_WORKER_ID` | 多实例必需 | 雪花算法工作节点 ID |
| `SNOWFLAKE_DATACENTER_ID` | 多实例必需 | 雪花算法数据中心 ID |

### 4. 启动中间件

推荐使用 Compose：

```bash
docker compose up -d
docker compose ps
```

Windows 也可以执行项目提供的脚本：

```powershell
.\docker\start.ps1
```

> 首次构建 Elasticsearch 镜像需要下载 IK 分词插件，请确保 Docker 可以访问插件地址。

### 5. 构建并启动应用

```bash
mvn clean package
java -jar msg-relay-starter/target/msg-relay-starter-0.0.1-SNAPSHOT.jar
```

开发时也可以直接运行：

```bash
mvn spring-boot:run -pl msg-relay-starter -am
```

应用启动后可访问：

| 服务 | 地址 |
| --- | --- |
| REST API | `http://localhost:8080` |
| WebSocket | `ws://localhost:8090/ws` |
| MySQL | `localhost:3307` |
| Redis | `localhost:6379` |
| Elasticsearch | `http://localhost:9200` |
| RocketMQ Dashboard | `http://localhost:9009` |

## 接口与认证

REST 接口统一使用 `/api` 前缀，主要分组如下：

- `/api/user`：登录、用户、团队、部门和设备
- `/api/im`：消息、会话和在线状态
- `/api/group`：群组及成员管理
- `/api/reliability`：消息 ACK
- `/api/file`：文件上传与下载
- `/api/search`：消息全文检索

除登录和刷新 Token 外，请求需要携带访问令牌：

```http
Authorization: Bearer <access-token>
```

WebSocket 连接建立后，客户端需要先发送以下文本帧完成认证：

```text
AUTH <access-token> <device-id>
```

认证成功后服务端返回 `AUTH_OK`，后续心跳使用 WebSocket Ping/Pong 控制帧。

## 常用命令

```bash
# 编译并运行测试
mvn test

# 完整校验
mvn verify

# 停止中间件（保留数据卷）
docker compose down

# 停止并删除本地数据卷，请谨慎执行
docker compose down -v
```

## 开源协议

使用 [Apache License 2.0](LICENSE) 开源。
