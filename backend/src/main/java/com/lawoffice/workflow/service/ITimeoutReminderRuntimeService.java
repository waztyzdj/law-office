package com.lawoffice.workflow.service;

/**
 * Approval timeout reminder runtime capability.
 */
public interface ITimeoutReminderRuntimeService {

    /**
     * Scans all enabled tenants for overdue workflow todo tasks and sends timeout reminders.
     *
     * @return number of reminder records created in this scan
     */
    int scanAndRemind();
}
