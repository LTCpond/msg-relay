package com.ltcpond.msgrelay.im.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息策略工厂 — 根据消息类型路由到对应 Handler
 *
 * Spring 自动注入所有 MessageHandlerStrategy 实现类，按 type 注册到 Map。
 * 扩展新消息类型只需新增 Handler，无需改动工厂或发送逻辑。
 */
@Component
public class MessageStrategyFactory {

    private final Map<Integer, MessageHandlerStrategy> strategyMap;

    public MessageStrategyFactory(List<MessageHandlerStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(MessageHandlerStrategy::getType, Function.identity()));
    }

    /** 根据类型获取策略，未匹配时 fallback 到 TEXT(1) */
    public MessageHandlerStrategy getStrategy(Integer type) {
        return strategyMap.getOrDefault(type, strategyMap.get(1));
    }
}
