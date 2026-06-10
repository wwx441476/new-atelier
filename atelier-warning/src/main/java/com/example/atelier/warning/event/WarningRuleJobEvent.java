package com.example.atelier.warning.event;

import com.example.atelier.domain.warning.WarningRuleJob;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 预警任务状态变更事件 — 供 SSE 等推送层订阅。
 */
@Getter
public class WarningRuleJobEvent extends ApplicationEvent {

    private final WarningRuleJob job;
    private final String eventName;

    public WarningRuleJobEvent(Object source, WarningRuleJob job, String eventName) {
        super(source);
        this.job = job;
        this.eventName = eventName;
    }
}
