package com.ltcpond.msgrelay.im.observer;

import com.ltcpond.msgrelay.im.model.entity.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息观察者管理器 — 消息推送的调度中心
 *
 * 设计模式: 观察者模式（Observer Pattern）
 * 当消息被消费者处理时，通知所有注册的观察者：
 * - WebSocketPushObserver: 推送给在线用户
 * - AppPushObserver (预留): 推送给离线用户（APNs/FCM）
 *
 * 扩展点: 新增推送渠道（如桌面通知）只需新增 Observer 并注册到此
 */
@Component
public class MessageObserverManager {

    @Resource
    private WebSocketPushObserver webSocketPushObserver;

    private final List<MessagePushObserver> observers = new ArrayList<>();

    /** 注册观察者 — 应用启动时将 WebSocket 推送观察者加入列表 */
    @PostConstruct
    public void registerObservers() {
        observers.add(webSocketPushObserver);
    }

    /** 通知所有观察者处理消息推送 */
    public void notifyObservers(Message message) {
        for (MessagePushObserver observer : observers) {
            observer.onMessage(message);
        }
    }
}
