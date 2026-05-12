package com.lawoffice.framework.service;

import com.lawoffice.framework.entity.SysLog;
import com.lawoffice.framework.vo.SysLogVO;

public interface ILogService extends IBaseService<SysLog, SysLogVO> {
    
    /**
     * 异步保存日志
     * @param log 日志对象
     */
    void saveLogAsync(SysLog log);
}
