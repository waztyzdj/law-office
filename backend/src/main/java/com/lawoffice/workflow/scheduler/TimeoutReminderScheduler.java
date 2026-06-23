package com.lawoffice.workflow.scheduler;

import com.lawoffice.workflow.service.ITimeoutReminderRuntimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TimeoutReminderScheduler {

    private final ITimeoutReminderRuntimeService timeoutReminderRuntimeService;

    public TimeoutReminderScheduler(ITimeoutReminderRuntimeService timeoutReminderRuntimeService) {
        this.timeoutReminderRuntimeService = timeoutReminderRuntimeService;
    }

    @Scheduled(fixedDelayString = "${workflow.timeout-reminder.scan-interval-ms:300000}")
    public void scanTimeoutTasks() {
        int count = timeoutReminderRuntimeService.scanAndRemind();
        if (count > 0) {
            log.info("Workflow timeout reminder scan completed, reminderCount={}", count);
        }
    }
}
