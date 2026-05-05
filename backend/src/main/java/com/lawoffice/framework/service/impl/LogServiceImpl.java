package com.lawoffice.framework.service.impl;

import com.lawoffice.framework.entity.SysLog;
import com.lawoffice.framework.mapper.LogMapper;
import com.lawoffice.framework.service.ILogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogServiceImpl extends BaseServiceImpl<SysLog> implements ILogService {

    @Autowired
    private LogMapper logMapper;

    @Autowired
    public LogServiceImpl(LogMapper logMapper) {
        super(logMapper, SysLog.class);
        this.logMapper = logMapper;
    }

    @Override
    @Async("taskExecutor")
    public void saveLogAsync(SysLog sysLog) {
        try {
            logMapper.insert(sysLog);
        } catch (Exception e) {
            log.error("异步保存日志失败", e);
        }
    }
}
