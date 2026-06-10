package com.example.atelier.api.job;

import com.example.atelier.warning.event.WarningRuleJobEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WarningRuleJobSseListener {

    private final JobSseHub jobSseHub;

    public WarningRuleJobSseListener(JobSseHub jobSseHub) {
        this.jobSseHub = jobSseHub;
    }

    @EventListener
    public void onWarningRuleJobEvent(WarningRuleJobEvent event) {
        jobSseHub.publish(event.getJob(), event.getEventName());
    }
}
