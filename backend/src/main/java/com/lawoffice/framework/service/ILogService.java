package com.lawoffice.framework.service;

import com.lawoffice.framework.entity.SysLog;

public interface ILogService extends IBaseService<SysLog> {
    
    /**
     * 异步保存日志
     * @param log 日志对象
     */
    void saveLogAsync(SysLog log);
}
