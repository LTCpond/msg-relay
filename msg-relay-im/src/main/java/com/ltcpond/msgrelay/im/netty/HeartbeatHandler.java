package com.ltcpond.msgrelay.im.netty;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ltcpond.msgrelay.im.config.HeartbeatConfig;
import com.ltcpond.msgrelay.im.service.OnlineStatusService;
import com.ltcpond.msgrelay.user.repository.LoginDeviceMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 控制帧心跳处理器 — 服务端主动发 Ping，用 Pong 超时推进 miss 计数
 *
 * 状态机（每个 channel 独立维护）：
 * 1. 握手完成 → 等待认证成功（认证前不发心跳）
 * 2. 认证成功 → 进入 warm-up，立刻发首跳 Ping
 * 3. 收到任意 Pong → miss=0，续租 presence
 * 4. Pong 超时 → miss++，达到 maxMisses 则断连
 * 5. 写空闲触发 → 发下一个 Ping
 *
 * 设计要点：
 * - 使用 Netty 控制帧（PingWebSocketFrame/PongWebSocketFrame），不再依赖文本协议
 * - 每个 Ping 挂一个 pongTimeout 定时器，用它判定超时，不依赖 readerIdle
 * - readerIdle 仅作为兜底保险丝（EventLoop 完全无入站时兜底断连）
 * - Pong 不做 payload/seq 匹配，任何 Pong 都视为对当前 pending ping 的确认（策略 A）
 * - 认证状态由 WebSocketHandler 通过 fireUserEventTriggered 触发，不在本 handler 解析 AUTH
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    @Resource
    private HeartbeatConfig heartbeatConfig;

    @Resource
    private OnlineStatusService onlineStatusService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private LoginDeviceMapper loginDeviceMapper;

    /** 设备踢出状态缓存 — 5s TTL 避免每次心跳都查 DB */
    private final Cache<String, Boolean> deviceKickCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(50000)
            .build();

    /** 认证状态：由 WebSocketHandler 认证成功后通过事件触发 */
    private static final AttributeKey<Boolean> AUTHENTICATED =
            AttributeKey.valueOf("heartbeat.authenticated");

    /** 连续未收到 Pong 的次数 */
    private static final AttributeKey<Integer> MISS_COUNT =
            AttributeKey.valueOf("heartbeat.missCount");

    /** Pong 超时定时器（ScheduledFuture），channel 关闭时需 cancel 避免泄漏 */
    private static final AttributeKey<ScheduledFuture<?>> PONG_TIMEOUT_FUTURE =
            AttributeKey.valueOf("heartbeat.pongTimeoutFuture");

    /** 是否有待确认的 ping（避免重复发送） */
    private static final AttributeKey<Boolean> PING_IN_FLIGHT =
            AttributeKey.valueOf("heartbeat.pingInFlight");

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 认证成功事件：由 WebSocketHandler 在认证成功后触发
        if (evt instanceof AuthenticationSuccessEvent) {
            ctx.channel().attr(AUTHENTICATED).set(true);
            ctx.channel().attr(MISS_COUNT).set(0);
            ctx.channel().attr(PING_IN_FLIGHT).set(false);
            log.info("认证成功，进入心跳状态机: channel={}", ctx.channel().id().asShortText());
            // 立即发首跳 Ping（warm-up：验证客户端能收到控制帧）
            sendPing(ctx);
        }

        // 写空闲触发：发 Ping 控制帧（仅认证后）
        if (evt instanceof IdleStateEvent idleEvt && idleEvt.state() == IdleState.WRITER_IDLE) {
            if (Boolean.TRUE.equals(ctx.channel().attr(AUTHENTICATED).get())) {
                sendPing(ctx);
            }
        }

        // 读空闲触发：兜底保险丝，极端情况（EventLoop 完全无入站）才触发断连
        if (evt instanceof IdleStateEvent idleEvt && idleEvt.state() == IdleState.READER_IDLE) {
            log.warn("读空闲兜底断连（不应经常触发，检查 EventLoop 是否阻塞）: channel={}",
                    ctx.channel().id().asShortText());
            ctx.close();
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PongWebSocketFrame) {
            // 收到任意 Pong：直接视为当前 pending ping 的确认（策略 A：不校验 seq）
            handlePong(ctx);
            // 释放 Pong 帧，避免内存泄漏（Netty 的引用计数）
            // 注意：SimpleChannelInboundHandler 会自动释放，但这里我们手动 return，所以需要释放
            // 但是 HeartbeatHandler 是 ChannelInboundHandlerAdapter，所以必须手动释放
            if (msg instanceof io.netty.util.ReferenceCounted refCnt) {
                refCnt.release();
            }
            return; // 不向下传递 Pong，业务 handler 不需要处理
        }

        // 其他消息继续向 pipeline 下游传递
        super.channelRead(ctx, msg);
    }

    /**
     * 发送 Ping 控制帧
     *
     * 逻辑：
     * 1. 如果已有 pending ping 且未超时，不再重复发（避免洪泛）
     * 2. 标记 pingInFlight
     * 3. 发送 PingWebSocketFrame（payload 可选，这里用空 payload 简化）
     * 4. 调度 pongTimeout 定时器
     */
    private void sendPing(ChannelHandlerContext ctx) {
        Boolean pingInFlight = ctx.channel().attr(PING_IN_FLIGHT).get();
        ScheduledFuture<?> existingFuture = ctx.channel().attr(PONG_TIMEOUT_FUTURE).get();

        // 如果已有 pending 且定时器还在跑，不重复发
        if (Boolean.TRUE.equals(pingInFlight) && existingFuture != null && !existingFuture.isDone()) {
            return;
        }

        // 标记有 pending ping
        ctx.channel().attr(PING_IN_FLIGHT).set(true);

        // 发送 Ping 控制帧（payload 可选，这里用空 payload）
        ctx.writeAndFlush(new PingWebSocketFrame());

        // 调度 Pong 超时检测
        int pongTimeoutSec = heartbeatConfig.getPongTimeoutSeconds();
        ScheduledFuture<?> future = ctx.channel().eventLoop().schedule(
                () -> handlePongTimeout(ctx),
                pongTimeoutSec, TimeUnit.SECONDS
        );
        ctx.channel().attr(PONG_TIMEOUT_FUTURE).set(future);

        log.debug("发送 Ping: channel={}, pongTimeout={}s",
                ctx.channel().id().asShortText(), pongTimeoutSec);
    }

    /**
     * 处理 Pong 回复（策略 A：任何 Pong 都视为有效）
     *
     * 逻辑：
     * 1. 如果没有 pending ping（PING_IN_FLIGHT=false 或无），忽略（可能是迟到的重复 Pong）
     * 2. 有效 Pong：清 pending，miss=0，取消定时器，续租 presence
     */
    private void handlePong(ChannelHandlerContext ctx) {
        Boolean pingInFlight = ctx.channel().attr(PING_IN_FLIGHT).get();
        if (!Boolean.TRUE.equals(pingInFlight)) {
            log.debug("收到 Pong 但无 pendingPing，忽略: channel={}", ctx.channel().id().asShortText());
            return;
        }

        // 有效 Pong：清 pending，miss=0，取消定时器
        ctx.channel().attr(PING_IN_FLIGHT).set(false);
        ctx.channel().attr(MISS_COUNT).set(0);
        cancelPongTimeout(ctx);

        // 校验设备是否已被踢出（DB 查询 + Caffeine 缓存降级）
        Long userId = sessionManager.getUserId(ctx.channel());
        String deviceId = ctx.channel().attr(SessionAttributes.DEVICE_ID).get();
        if (userId != null && deviceId != null) {
            String cacheKey = userId + ":" + deviceId;
            Boolean stillValid = deviceKickCache.get(cacheKey,
                    k -> loginDeviceMapper.existsByUserIdAndDeviceId(userId, deviceId));
            if (Boolean.FALSE.equals(stillValid)) {
                log.info("心跳时发现设备已被踢出: userId={}, deviceId={}", userId, deviceId);
                ctx.channel().writeAndFlush(
                        new io.netty.handler.codec.http.websocketx.TextWebSocketFrame("KICKED"));
                ctx.close();
                return;
            }

            // 续租 presence（通过 deviceId）
            onlineStatusService.heartbeat(userId, deviceId);
        }

        log.debug("收到有效 Pong: miss=0, channel={}",
                ctx.channel().id().asShortText());
    }

    /**
     * 处理 Pong 超时
     *
     * 逻辑：
     * 1. 校验是否仍有 pending ping（防止迟到 Pong 已清 pending 后仍触发）
     * 2. miss++，判断是否达到阈值
     * 3. 达到阈值：断连（channelInactive 会触发 presence offline）
     * 4. 未达到：清 pending，等下一次写空闲触发新 Ping
     */
    private void handlePongTimeout(ChannelHandlerContext ctx) {
        // channel 已关闭，跳过
        if (!ctx.channel().isActive()) {
            return;
        }

        Boolean pingInFlight = ctx.channel().attr(PING_IN_FLIGHT).get();
        // 如果 ping 已被确认（pong 到达），跳过
        if (!Boolean.TRUE.equals(pingInFlight)) {
            return;
        }

        // 超时：清 pending，miss++
        ctx.channel().attr(PING_IN_FLIGHT).set(false);
        int miss = ctx.channel().attr(MISS_COUNT).get() + 1;
        ctx.channel().attr(MISS_COUNT).set(miss);

        log.info("Pong 超时: miss={}, maxMisses={}, channel={}",
                miss, heartbeatConfig.getMaxMisses(), ctx.channel().id().asShortText());

        if (miss >= heartbeatConfig.getMaxMisses()) {
            // 连续 miss 达阈值，断连
            log.info("连续 {} 次未收到 Pong，断连: channel={}", miss, ctx.channel().id().asShortText());
            ctx.close();
        }
        // 未达阈值：等下一次写空闲触发新 Ping（不立即重发，避免在无入站期间快速循环）
    }

    /** 取消 Pong 超时定时器，避免 channel 关闭后定时任务泄漏 */
    private void cancelPongTimeout(ChannelHandlerContext ctx) {
        ScheduledFuture<?> future = ctx.channel().attr(PONG_TIMEOUT_FUTURE).get();
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
        ctx.channel().attr(PONG_TIMEOUT_FUTURE).set(null);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接关闭时清理定时器，避免泄漏
        cancelPongTimeout(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("HeartbeatHandler 异常: channel={}", ctx.channel().id().asShortText(), cause);
        cancelPongTimeout(ctx);
        ctx.close();
    }
}