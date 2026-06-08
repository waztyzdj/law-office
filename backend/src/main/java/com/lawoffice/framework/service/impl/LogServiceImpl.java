package com.lawoffice.framework.service.impl;

import com.lawoffice.framework.entity.SysLog;
import com.lawoffice.framework.mapper.LogMapper;
import com.lawoffice.framework.service.ILogService;
import com.lawoffice.framework.vo.SysLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogServiceImpl extends BaseServiceImpl<LogMapper, SysLog, SysLogVO> implements ILogService {

    @Override
    @Async("logTaskExecutor")
    public void saveLogAsync(SysLog sysLog) {
        try {
            // 使用 ServiceImpl 提供的 baseMapper
            baseMapper.insert(sysLog);
        } catch (Exception e) {
            log.error("异步保存日志失败", e);
        }
    }
}
