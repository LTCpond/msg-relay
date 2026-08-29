package com.ltcpond.msgrelay.im.observer;

import com.ltcpond.msgrelay.im.model.entity.Message;

/** 消息推送观察者接口 — 观察者模式，消息消费后通知所有注册的观察者 */
public interface MessagePushObserver {

    /** 收到新消息时的回调 */
    void onMessage(Message message);
}
