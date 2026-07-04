package com.lawoffice.home.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchRecentRecord;
import com.lawoffice.home.mapper.WorkbenchRecentRecordMapper;
import com.lawoffice.home.req.WorkbenchRecentClearReq;
import com.lawoffice.home.req.WorkbenchRecentPageReq;
import com.lawoffice.home.req.WorkbenchRecentRecordReq;
import com.lawoffice.home.service.IWorkbenchRecentRecordService;
import com.lawoffice.home.vo.WorkbenchRecentRecordVO;
import com.lawoffice.system.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class WorkbenchRecentRecordServiceImpl
        extends AbstractHomeWorkbenchServiceImpl<WorkbenchRecentRecordMapper, WorkbenchRecentRecord, WorkbenchRecentRecordVO>
        implements IWorkbenchRecentRecordService {

    public WorkbenchRecentRecordServiceImpl(PermissionMapper permissionMapper) {
        super(permissionMapper);
    }

    @Override
    public PageVO<WorkbenchRecentRecordVO> pageCurrentUserRecords(WorkbenchRecentPageReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        WorkbenchRecentPageReq safeReq = req == null ? new WorkbenchRecentPageReq() : req;
        QueryWrapper<WorkbenchRecentRecord> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("user_id", userId);
        if (StringUtils.hasText(safeReq.getRecordType())) {
            String recordType = safeReq.getRecordType().trim();
            validateIn(recordType, "近期工作类型不合法", HomeWorkbenchConstants.RECORD_TYPES);
            wrapper.eq("record_type", recordType);
        }
        wrapper.orderByDesc("last_visit_time");

        Page<WorkbenchRecentRecord> page = new Page<>(pageNum(safeReq), pageSize(safeReq));
        Page<WorkbenchRecentRecord> result = this.page(page, wrapper);
        return new PageVO<>(
                BeanUtil.copyToList(result.getRecords(), WorkbenchRecentRecordVO.class),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkbenchRecentRecordVO recordCurrentUserVisit(WorkbenchRecentRecordReq req, RequestContext context) {
        if (req == null) {
            throw new IllegalArgumentException("近期工作记录不能为空");
        }
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        String recordType = trimToNull(req.getRecordType());
        validateIn(recordType, "近期工作类型不合法", HomeWorkbenchConstants.RECORD_TYPES);
        String targetType = StringUtils.hasText(req.getTargetType())
                ? req.getTargetType().trim()
                : HomeWorkbenchConstants.TARGET_TYPE_ROUTE;
        validateIn(targetType, "跳转目标类型不合法", HomeWorkbenchConstants.TARGET_TYPES);
        String title = trimToNull(req.getTitle());
        requireText(title, "标题不能为空");
        String targetParamsJson = trimToNull(req.getTargetParamsJson());
        validateJson(targetParamsJson, "跳转参数");
        String recordKey = buildRecordKey(recordType, req);

        WorkbenchRecentRecord record = findCurrentRecord(tenantId, userId, recordType, recordKey);
        boolean create = record == null;
        if (create) {
            record = new WorkbenchRecentRecord();
            record.setId(newId());
            record.setTenantId(tenantId);
            record.setUserId(userId);
            record.setRecordType(recordType);
            record.setRecordKey(recordKey);
            record.setVisitCount(0);
        }
        record.setModuleCode(trimToNull(req.getModuleCode()));
        record.setBizId(trimToNull(req.getBizId()));
        record.setTitle(title);
        record.setTargetType(targetType);
        record.setTargetPath(trimToNull(req.getTargetPath()));
        record.setTargetParamsJson(targetParamsJson);
        record.setSourceTime(req.getSourceTime());
        record.setLastVisitTime(LocalDateTime.now());
        record.setVisitCount(record.getVisitCount() == null ? 1 : record.getVisitCount() + 1);
        fillCreateOrUpdate(record, context, create);
        if (create) {
            baseMapper.insert(record);
        } else {
            baseMapper.updateById(record);
        }
        return BeanUtil.toBean(record, WorkbenchRecentRecordVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCurrentUserRecords(WorkbenchRecentClearReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        QueryWrapper<WorkbenchRecentRecord> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("user_id", userId);
        if (req != null && StringUtils.hasText(req.getRecordType())) {
            String recordType = req.getRecordType().trim();
            validateIn(recordType, "近期工作类型不合法", HomeWorkbenchConstants.RECORD_TYPES);
            wrapper.eq("record_type", recordType);
        }
        // 近期工作是可再生成的个人访问摘要。物理清理可避免 delete_flag 唯一索引影响后续重新记录。
        baseMapper.delete(wrapper);
    }

    private WorkbenchRecentRecord findCurrentRecord(String tenantId, String userId, String recordType, String recordKey) {
        QueryWrapper<WorkbenchRecentRecord> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("user_id", userId)
                .eq("record_type", recordType)
                .eq("record_key", recordKey);
        return baseMapper.selectOne(wrapper);
    }

    /**
     * 生成稳定合并键，避免直接把过长路由参数放进唯一索引。
     */
    private String buildRecordKey(String recordType, WorkbenchRecentRecordReq req) {
        String moduleCode = trimToNull(req.getModuleCode());
        String bizId = trimToNull(req.getBizId());
        String targetPath = trimToNull(req.getTargetPath());
        if (StringUtils.hasText(moduleCode) && StringUtils.hasText(bizId)) {
            return moduleCode + ":" + bizId;
        }
        if (StringUtils.hasText(bizId)) {
            return recordType + ":" + bizId;
        }
        if (StringUtils.hasText(targetPath)) {
            return "path:" + targetPath;
        }
        return "title:" + req.getTitle().trim();
    }
}
