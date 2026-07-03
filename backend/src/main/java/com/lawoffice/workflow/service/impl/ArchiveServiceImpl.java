package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;
import com.lawoffice.workflow.entity.ArchiveRecord;
import com.lawoffice.workflow.entity.Attachment;
import com.lawoffice.workflow.entity.ProcessCategory;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.ArchiveRecordMapper;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.req.AdminMonitorPageReq;
import com.lawoffice.workflow.req.ArchiveActionReq;
import com.lawoffice.workflow.req.ArchivePageReq;
import com.lawoffice.workflow.service.IAdminMonitorService;
import com.lawoffice.workflow.service.IArchiveService;
import com.lawoffice.workflow.service.IAttachmentRuntimeService;
import com.lawoffice.workflow.service.IDiagramService;
import com.lawoffice.workflow.service.IWorkflowDownloadService;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AttachmentVO;
import com.lawoffice.workflow.vo.ArchiveRecordVO;
import com.lawoffice.workflow.vo.ArchiveTreeNodeVO;
import com.lawoffice.workflow.vo.InstanceDiagramVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArchiveServiceImpl implements IArchiveService {

    private static final int MAX_BATCH_ARCHIVE_BY_QUERY_SIZE = 1000;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<String> MANUAL_ARCHIVABLE_STATUSES = Set.of(
            WorkflowConstants.Status.APPROVED,
            WorkflowConstants.Status.REJECTED,
            WorkflowConstants.Status.TERMINATED
    );

    private static final Map<String, String> ARCHIVE_SORT_FIELDS = Map.ofEntries(
            Map.entry("instanceTitle", "instance_title"),
            Map.entry("instanceNo", "instance_no"),
            Map.entry("processName", "process_name"),
            Map.entry("processVersion", "process_version"),
            Map.entry("starterRealname", "starter_realname"),
            Map.entry("instanceStatus", "instance_status"),
            Map.entry("processStartTime", "process_start_time"),
            Map.entry("processEndTime", "process_end_time"),
            Map.entry("archiveTime", "archive_time"),
            Map.entry("createTime", "create_time")
    );

    private static final Map<String, String> UNARCHIVED_SORT_FIELDS = Map.ofEntries(
            Map.entry("instanceTitle", "instance_title"),
            Map.entry("instanceNo", "instance_no"),
            Map.entry("starterRealname", "starter_realname"),
            Map.entry("instanceStatus", "status"),
            Map.entry("processStartTime", "start_time"),
            Map.entry("processEndTime", "end_time"),
            Map.entry("createTime", "create_time"),
            Map.entry("updateTime", "update_time")
    );

    private final ArchiveRecordMapper archiveRecordMapper;
    private final AttachmentMapper attachmentMapper;
    private final ProcessCategoryMapper processCategoryMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessModelMapper processModelMapper;
    private final IAdminMonitorService adminMonitorService;
    private final IAttachmentRuntimeService attachmentRuntimeService;
    private final IDiagramService diagramService;
    private final IWorkflowDownloadService workflowDownloadService;
    private final UserMapper userMapper;

    public ArchiveServiceImpl(ArchiveRecordMapper archiveRecordMapper,
            AttachmentMapper attachmentMapper,
            ProcessCategoryMapper processCategoryMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessModelMapper processModelMapper,
            IAdminMonitorService adminMonitorService,
            IAttachmentRuntimeService attachmentRuntimeService,
            IDiagramService diagramService,
            IWorkflowDownloadService workflowDownloadService,
            UserMapper userMapper) {
        this.archiveRecordMapper = archiveRecordMapper;
        this.attachmentMapper = attachmentMapper;
        this.processCategoryMapper = processCategoryMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processModelMapper = processModelMapper;
        this.adminMonitorService = adminMonitorService;
        this.attachmentRuntimeService = attachmentRuntimeService;
        this.diagramService = diagramService;
        this.workflowDownloadService = workflowDownloadService;
        this.userMapper = userMapper;
    }

    @Override
    public BaseResult<List<ArchiveTreeNodeVO>> tree(RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            return BaseResult.success(buildArchiveTree(tenantId));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程归档树失败", e);
        }
    }

    @Override
    public BaseResult<PageVO<ArchiveRecordVO>> pageArchived(ArchivePageReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
            int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
            QueryWrapper<ArchiveRecord> wrapper = buildArchivedWrapper(req, tenantId);
            applyAllowedSorting(wrapper, req == null ? null : req.getSortField(),
                    req == null ? null : req.getSortOrder(), ARCHIVE_SORT_FIELDS, "archive_time");
            Page<ArchiveRecord> page = archiveRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            return BaseResult.success(new PageVO<>(
                    page.getRecords().stream().map(this::buildArchiveRecordVO).toList(),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程归档失败", e);
        }
    }

    @Override
    public BaseResult<PageVO<ArchiveRecordVO>> pageUnarchived(ArchivePageReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
            int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
            QueryWrapper<ProcessInstance> wrapper = buildUnarchivedWrapper(req, tenantId);
            applyAllowedSorting(wrapper, req == null ? null : req.getSortField(),
                    req == null ? null : req.getSortOrder(), UNARCHIVED_SORT_FIELDS, "end_time");
            Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            return BaseResult.success(new PageVO<>(
                    buildUnarchivedRecords(page.getRecords(), tenantId),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询未归档流程失败", e);
        }
    }

    @Override
    public BaseResult<AdminMonitorDetailVO> detail(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            ensureArchiveDetailVisible(processInstanceId, tenantId);
            return adminMonitorService.detail(processInstanceId, context);
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程归档详情失败", e);
        }
    }

    @Override
    public BaseResult<InstanceDiagramVO> diagram(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            ensureArchiveDetailVisible(processInstanceId, tenantId);
            return diagramService.getInstanceDiagramForGrantedAccess(processInstanceId, context);
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程归档图谱失败", e);
        }
    }

    @Override
    public BaseResult<List<AttachmentVO>> listAttachments(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            ensureArchiveDetailVisible(processInstanceId, tenantId);
            return attachmentRuntimeService.listByInstanceForGrantedAccess(processInstanceId, context);
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程归档附件失败", e);
        }
    }

    @Override
    public FileUploadVO requireAttachmentFile(String attachmentId, RequestContext context) {
        ensureArchiveAttachmentVisible(attachmentId, RuntimeSupport.requireTenantId(context));
        return attachmentRuntimeService.requireFileForGrantedAccess(attachmentId, context);
    }

    @Override
    public InputStream downloadAttachmentContent(String attachmentId, RequestContext context) {
        ensureArchiveAttachmentVisible(attachmentId, RuntimeSupport.requireTenantId(context));
        return attachmentRuntimeService.downloadContentForGrantedAccess(attachmentId, context);
    }

    @Override
    public WorkflowDownloadFile downloadAttachmentPackage(String processInstanceId, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        ensureArchiveDetailVisible(processInstanceId, tenantId);
        return attachmentRuntimeService.downloadPackageByInstanceForGrantedAccess(processInstanceId, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ArchiveRecordVO> archiveAutomatically(String processInstanceId, RequestContext context) {
        try {
            String tenantId = resolveTenantId(context, processInstanceId);
            ArchiveRecord record = createArchiveRecord(processInstanceId, tenantId,
                    WorkflowConstants.ArchiveSource.AUTO, null, context, false);
            return BaseResult.success(buildArchiveRecordVO(record));
        } catch (IllegalArgumentException e) {
            markRollbackIfActive();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            markRollbackIfActive();
            return systemError("自动归档流程失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ArchiveRecordVO> archiveFromMonitor(ArchiveActionReq req, RequestContext context) {
        return archiveManually(req, WorkflowConstants.ArchiveSource.MONITOR_MANUAL, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<List<ArchiveRecordVO>> batchArchiveFromMonitor(ArchiveActionReq req, RequestContext context) {
        return batchArchiveManually(req, WorkflowConstants.ArchiveSource.MONITOR_MANUAL, context, "批量归档流程失败: ");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<List<ArchiveRecordVO>> batchArchiveByQueryFromMonitor(AdminMonitorPageReq req,
            RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            QueryWrapper<ProcessInstance> countWrapper = buildMonitorUnarchivedWrapper(req, tenantId);
            Long total = processInstanceMapper.selectCount(countWrapper);
            if (total == null || total == 0) {
                return BaseResult.success(List.of());
            }
            if (total > MAX_BATCH_ARCHIVE_BY_QUERY_SIZE) {
                throw new IllegalArgumentException("单次最多归档" + MAX_BATCH_ARCHIVE_BY_QUERY_SIZE + "个流程，请缩小查询范围");
            }
            QueryWrapper<ProcessInstance> queryWrapper = buildMonitorUnarchivedWrapper(req, tenantId);
            queryWrapper.select("id").orderByDesc("end_time").orderByDesc("create_time");
            String reason = req == null ? null : req.getArchiveReason();
            List<ArchiveRecordVO> records = processInstanceMapper.selectList(queryWrapper).stream()
                    .map(ProcessInstance::getId)
                    .map(processInstanceId -> createArchiveRecord(processInstanceId, tenantId,
                            WorkflowConstants.ArchiveSource.MONITOR_MANUAL, reason, context, true))
                    .map(this::buildArchiveRecordVO)
                    .toList();
            return BaseResult.success(records);
        } catch (IllegalArgumentException e) {
            markRollbackIfActive();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            markRollbackIfActive();
            return systemError("按查询条件批量归档流程失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ArchiveRecordVO> archiveFromArchiveMenu(ArchiveActionReq req, RequestContext context) {
        return archiveManually(req, WorkflowConstants.ArchiveSource.ARCHIVE_MANUAL, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<List<ArchiveRecordVO>> batchArchiveFromArchiveMenu(ArchiveActionReq req,
            RequestContext context) {
        return batchArchiveManually(req, WorkflowConstants.ArchiveSource.ARCHIVE_MANUAL, context, "批量归档流程失败: ");
    }

    private BaseResult<List<ArchiveRecordVO>> batchArchiveManually(ArchiveActionReq req, String archiveSource,
            RequestContext context, String errorPrefix) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            List<String> processInstanceIds = normalizeProcessInstanceIds(req);
            String reason = req == null ? null : req.getArchiveReason();
            List<ArchiveRecordVO> records = processInstanceIds.stream()
                    .map(processInstanceId -> createArchiveRecord(processInstanceId, tenantId,
                            archiveSource, reason, context, true))
                    .map(this::buildArchiveRecordVO)
                    .toList();
            return BaseResult.success(records);
        } catch (IllegalArgumentException e) {
            markRollbackIfActive();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            markRollbackIfActive();
            return systemError(stripErrorSuffix(errorPrefix), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<List<ArchiveRecordVO>> batchArchiveByQueryFromArchiveMenu(ArchivePageReq req,
            RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            QueryWrapper<ProcessInstance> countWrapper = buildUnarchivedWrapper(req, tenantId);
            Long total = processInstanceMapper.selectCount(countWrapper);
            if (total == null || total == 0) {
                return BaseResult.success(List.of());
            }
            if (total > MAX_BATCH_ARCHIVE_BY_QUERY_SIZE) {
                throw new IllegalArgumentException("单次最多归档" + MAX_BATCH_ARCHIVE_BY_QUERY_SIZE + "个流程，请缩小查询范围");
            }
            QueryWrapper<ProcessInstance> queryWrapper = buildUnarchivedWrapper(req, tenantId);
            queryWrapper.select("id").orderByDesc("end_time").orderByDesc("create_time");
            String reason = req == null ? null : req.getArchiveReason();
            List<ArchiveRecordVO> records = processInstanceMapper.selectList(queryWrapper).stream()
                    .map(ProcessInstance::getId)
                    .map(processInstanceId -> createArchiveRecord(processInstanceId, tenantId,
                            WorkflowConstants.ArchiveSource.ARCHIVE_MANUAL, reason, context, true))
                    .map(this::buildArchiveRecordVO)
                    .toList();
            return BaseResult.success(records);
        } catch (IllegalArgumentException e) {
            markRollbackIfActive();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            markRollbackIfActive();
            return systemError("按查询条件批量归档流程失败", e);
        }
    }

    @Override
    public WorkflowDownloadFile downloadPackage(String processInstanceId, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        if (findArchiveRecord(processInstanceId, tenantId) == null) {
            throw new IllegalArgumentException("流程尚未归档，不能下载归档材料");
        }
        return workflowDownloadService.downloadArchivePackage(processInstanceId, context);
    }

    @Override
    public boolean isArchived(String processInstanceId, String tenantId) {
        if (!StringUtils.hasText(processInstanceId) || !StringUtils.hasText(tenantId)) {
            return false;
        }
        return archiveRecordMapper.selectCount(new QueryWrapper<ArchiveRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)) > 0;
    }

    private BaseResult<ArchiveRecordVO> archiveManually(ArchiveActionReq req, String archiveSource,
            RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String processInstanceId = requireProcessInstanceId(req);
            ArchiveRecord record = createArchiveRecord(processInstanceId, tenantId, archiveSource,
                    req == null ? null : req.getArchiveReason(), context, true);
            return BaseResult.success(buildArchiveRecordVO(record));
        } catch (IllegalArgumentException e) {
            markRollbackIfActive();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            markRollbackIfActive();
            return systemError("归档流程失败", e);
        }
    }

    private <T> BaseResult<T> systemError(String message, Exception e) {
        log.error(message, e);
        return BaseResult.error(message);
    }

    private String stripErrorSuffix(String errorPrefix) {
        if (errorPrefix == null) {
            return "归档流程失败";
        }
        return errorPrefix.endsWith(": ") ? errorPrefix.substring(0, errorPrefix.length() - 2) : errorPrefix;
    }

    private List<ArchiveTreeNodeVO> buildArchiveTree(String tenantId) {
        List<ProcessModel> processes = listLatestPublishedProcessModels(tenantId);
        Map<String, ProcessCategory> categoryMap = buildCategoryMap(processes, tenantId);
        ArchiveTreeNodeVO root = new ArchiveTreeNodeVO();
        root.setKey("all");
        root.setTitle("全部流程");
        root.setType("all");

        Map<String, List<ProcessModel>> processesByCategory = processes.stream()
                .filter(process -> StringUtils.hasText(process.getCategoryId()))
                .collect(Collectors.groupingBy(ProcessModel::getCategoryId));
        List<ArchiveTreeNodeVO> categoryNodes = processesByCategory.entrySet().stream()
                .map(entry -> buildCategoryNode(entry.getKey(), categoryMap.get(entry.getKey()), entry.getValue()))
                .sorted((left, right) -> safeText(left.getTitle()).compareTo(safeText(right.getTitle())))
                .toList();
        root.getChildren().addAll(categoryNodes);
        processes.stream()
                .filter(process -> !StringUtils.hasText(process.getCategoryId()))
                .map(this::buildProcessNode)
                .forEach(root.getChildren()::add);
        return List.of(root);
    }

    private ArchiveTreeNodeVO buildCategoryNode(String categoryId, ProcessCategory category,
            List<ProcessModel> processes) {
        ArchiveTreeNodeVO node = new ArchiveTreeNodeVO();
        node.setKey("category:" + categoryId);
        node.setTitle(category == null ? categoryId : category.getCategoryName());
        node.setType("category");
        node.setCategoryId(categoryId);
        List<ArchiveTreeNodeVO> children = processes.stream()
                .sorted((left, right) -> safeText(left.getProcessName()).compareTo(safeText(right.getProcessName())))
                .map(this::buildProcessNode)
                .toList();
        node.getChildren().addAll(children);
        return node;
    }

    private ArchiveTreeNodeVO buildProcessNode(ProcessModel process) {
        ArchiveTreeNodeVO node = new ArchiveTreeNodeVO();
        node.setKey("process:" + process.getProcessKey());
        node.setTitle(process.getProcessName());
        node.setType("process");
        node.setCategoryId(process.getCategoryId());
        node.setProcessModelId(process.getId());
        node.setProcessKey(process.getProcessKey());
        node.setProcessName(process.getProcessName());
        return node;
    }

    private List<ProcessModel> listLatestPublishedProcessModels(String tenantId) {
        return processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0)
                .eq("status", WorkflowConstants.Status.PUBLISHED)
                .inSql("id", latestPublishedProcessModelSql())
                .orderByAsc("process_name")
                .orderByAsc("process_key"));
    }

    private String latestPublishedProcessModelSql() {
        return """
                SELECT latest_model.id
                FROM wf_process_model latest_model
                INNER JOIN (
                    SELECT tenant_id, process_key, MAX(version) AS max_version
                    FROM wf_process_model
                    WHERE delete_flag = 0 AND status = 'published'
                    GROUP BY tenant_id, process_key
                ) latest_version
                    ON latest_version.tenant_id = latest_model.tenant_id
                    AND latest_version.process_key = latest_model.process_key
                    AND latest_version.max_version = latest_model.version
                WHERE latest_model.delete_flag = 0 AND latest_model.status = 'published'
                """;
    }

    private QueryWrapper<ArchiveRecord> buildArchivedWrapper(ArchivePageReq req, String tenantId) {
        QueryWrapper<ArchiveRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("delete_flag", 0);
        if (req == null) {
            return wrapper;
        }
        if (StringUtils.hasText(req.getInstanceTitle())) {
            wrapper.like("instance_title", req.getInstanceTitle());
        }
        if (StringUtils.hasText(req.getInstanceNo())) {
            wrapper.like("instance_no", req.getInstanceNo());
        }
        if (StringUtils.hasText(req.getCategoryId())) {
            wrapper.eq("category_id", req.getCategoryId());
        }
        if (StringUtils.hasText(req.getProcessKey())) {
            wrapper.eq("process_key", req.getProcessKey());
        }
        if (StringUtils.hasText(req.getProcessName())) {
            wrapper.like("process_name", req.getProcessName());
        }
        if (req.getProcessVersion() != null) {
            wrapper.eq("process_version", req.getProcessVersion());
        }
        if (StringUtils.hasText(req.getStarterRealname())) {
            wrapper.like("starter_realname", req.getStarterRealname());
        }
        if (StringUtils.hasText(req.getInstanceStatus())) {
            wrapper.eq("instance_status", req.getInstanceStatus());
        }
        if (StringUtils.hasText(req.getArchiveSource())) {
            wrapper.eq("archive_source", req.getArchiveSource());
        }
        applyDateRange(wrapper, "process_start_time", req.getProcessStartTimeGe(), req.getProcessStartTimeLe(),
                "流程发起时间");
        applyDateRange(wrapper, "process_end_time", req.getProcessEndTimeGe(), req.getProcessEndTimeLe(),
                "流程结束时间");
        applyDateRange(wrapper, "archive_time", req.getArchiveTimeGe(), req.getArchiveTimeLe(), "归档时间");
        return wrapper;
    }

    private QueryWrapper<ProcessInstance> buildUnarchivedWrapper(ArchivePageReq req, String tenantId) {
        QueryWrapper<ProcessInstance> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("delete_flag", 0)
                .in("status", MANUAL_ARCHIVABLE_STATUSES)
                .notExists("select 1 from wf_archive_record ar "
                        + "where ar.tenant_id = wf_process_instance.tenant_id "
                        + "and ar.process_instance_id = wf_process_instance.id "
                        + "and ar.delete_flag = 0");
        if (req == null) {
            return wrapper;
        }
        if (StringUtils.hasText(req.getArchiveSource())) {
            wrapper.eq("id", "__none__");
        }
        if (StringUtils.hasText(req.getInstanceTitle())) {
            wrapper.like("instance_title", req.getInstanceTitle());
        }
        if (StringUtils.hasText(req.getInstanceNo())) {
            wrapper.like("instance_no", req.getInstanceNo());
        }
        if (StringUtils.hasText(req.getStarterRealname())) {
            wrapper.like("starter_realname", req.getStarterRealname());
        }
        if (StringUtils.hasText(req.getInstanceStatus())) {
            wrapper.eq("status", req.getInstanceStatus());
        }
        applyDateRange(wrapper, "start_time", req.getProcessStartTimeGe(), req.getProcessStartTimeLe(), "流程发起时间");
        applyDateRange(wrapper, "end_time", req.getProcessEndTimeGe(), req.getProcessEndTimeLe(), "流程结束时间");
        List<String> processModelIds = listMatchedProcessModelIds(req, tenantId);
        if (processModelIds != null) {
            if (processModelIds.isEmpty()) {
                wrapper.eq("id", "__none__");
            } else {
                wrapper.in("process_model_id", processModelIds);
            }
        }
        return wrapper;
    }

    private QueryWrapper<ProcessInstance> buildMonitorUnarchivedWrapper(AdminMonitorPageReq req, String tenantId) {
        QueryWrapper<ProcessInstance> wrapper = baseUnarchivedProcessInstanceWrapper(tenantId);
        if (req == null) {
            return wrapper;
        }
        if (StringUtils.hasText(req.getInstanceTitle())) {
            wrapper.like("instance_title", req.getInstanceTitle());
        }
        if (StringUtils.hasText(req.getInstanceNo())) {
            wrapper.like("instance_no", req.getInstanceNo());
        }
        if (StringUtils.hasText(req.getStarterRealname())) {
            wrapper.like("starter_realname", req.getStarterRealname());
        }
        if (StringUtils.hasText(req.getCurrentTaskNames())) {
            wrapper.like("current_task_names", req.getCurrentTaskNames());
        }
        if (StringUtils.hasText(req.getCurrentAssigneeNames())) {
            wrapper.like("current_assignee_names", req.getCurrentAssigneeNames());
        }
        if (StringUtils.hasText(req.getStatus())) {
            wrapper.eq("status", req.getStatus());
        }
        applyDateRange(wrapper, "start_time", req.getStartTimeGe(), req.getStartTimeLe(), "发起时间");
        applyDateRange(wrapper, "update_time", req.getUpdateTimeGe(), req.getUpdateTimeLe(), "更新时间");
        List<String> processModelIds = listMonitorMatchedProcessModelIds(req, tenantId);
        if (processModelIds != null) {
            if (processModelIds.isEmpty()) {
                wrapper.eq("id", "__none__");
            } else {
                wrapper.in("process_model_id", processModelIds);
            }
        }
        return wrapper;
    }

    private QueryWrapper<ProcessInstance> baseUnarchivedProcessInstanceWrapper(String tenantId) {
        return new QueryWrapper<ProcessInstance>()
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0)
                .in("status", MANUAL_ARCHIVABLE_STATUSES)
                .notExists("select 1 from wf_archive_record ar "
                        + "where ar.tenant_id = wf_process_instance.tenant_id "
                        + "and ar.process_instance_id = wf_process_instance.id "
                        + "and ar.delete_flag = 0");
    }

    /**
     * 创建归档记录时必须重新读取实例、模型和分类，归档表只保存查询和审计所需快照，
     * 不反向修改流程状态，也不生成审批动作。
     */
    private ArchiveRecord createArchiveRecord(String processInstanceId, String tenantId, String archiveSource,
            String archiveReason, RequestContext context, boolean failIfExists) {
        ProcessInstance processInstance = requireProcessInstance(processInstanceId, tenantId);
        ArchiveRecord existing = findArchiveRecord(processInstance.getId(), tenantId);
        if (existing != null) {
            if (failIfExists) {
                throw new IllegalArgumentException("当前流程已归档");
            }
            return existing;
        }
        validateArchiveStatus(processInstance, archiveSource);
        ProcessModel processModel = requireProcessModel(processInstance.getProcessModelId(), tenantId);
        ProcessCategory category = findProcessCategory(processModel.getCategoryId(), tenantId);
        ArchiveRecord record = new ArchiveRecord();
        record.setId(newId());
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setProcessModelId(processModel.getId());
        record.setCategoryId(processModel.getCategoryId());
        record.setCategoryName(category == null ? null : category.getCategoryName());
        record.setProcessKey(processModel.getProcessKey());
        record.setProcessName(processModel.getProcessName());
        record.setProcessVersion(processModel.getVersion());
        record.setFormInstanceId(processInstance.getFormInstanceId());
        record.setFormDefinitionId(processInstance.getFormDefinitionId());
        record.setInstanceNo(processInstance.getInstanceNo());
        record.setInstanceTitle(processInstance.getInstanceTitle());
        record.setStarterUserId(processInstance.getStarterUserId());
        record.setStarterUsername(processInstance.getStarterUsername());
        record.setStarterRealname(processInstance.getStarterRealname());
        record.setInstanceStatus(processInstance.getStatus());
        record.setProcessStartTime(processInstance.getStartTime());
        record.setProcessEndTime(processInstance.getEndTime());
        record.setArchiveSource(archiveSource);
        record.setArchiveReason(archiveReason);
        fillArchiver(record, context, archiveSource);
        record.setArchiveTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        archiveRecordMapper.insert(record);
        return record;
    }

    /**
     * 自动归档只处理自然结束，手动归档只处理已结束未归档流程，避免把撤回或运行中流程纳入档案口径。
     */
    private void validateArchiveStatus(ProcessInstance processInstance, String archiveSource) {
        if (WorkflowConstants.ArchiveSource.AUTO.equals(archiveSource)) {
            if (!WorkflowConstants.Status.APPROVED.equals(processInstance.getStatus())
                    && !WorkflowConstants.Status.REJECTED.equals(processInstance.getStatus())) {
                throw new IllegalArgumentException("只有正常结束流程才能自动归档");
            }
            return;
        }
        if (!MANUAL_ARCHIVABLE_STATUSES.contains(processInstance.getStatus())) {
            throw new IllegalArgumentException("只有已结束且未归档的流程才能手动归档");
        }
    }

    private void fillArchiver(ArchiveRecord record, RequestContext context, String archiveSource) {
        if (context == null || !StringUtils.hasText(context.getUserId())) {
            record.setArchiverUserId("system");
            record.setArchiverUsername("system");
            record.setArchiverRealname("系统");
            return;
        }
        record.setArchiverUserId(context.getUserId());
        record.setArchiverUsername(RuntimeSupport.username(context));
        record.setArchiverRealname(resolveRealname(context.getUserId(), archiveSource));
    }

    private String resolveRealname(String userId, String archiveSource) {
        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("id", userId)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (user != null && StringUtils.hasText(user.getRealname())) {
            return user.getRealname();
        }
        return WorkflowConstants.ArchiveSource.AUTO.equals(archiveSource) ? "系统" : null;
    }

    private List<ArchiveRecordVO> buildUnarchivedRecords(List<ProcessInstance> instances, String tenantId) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        Map<String, ProcessModel> processModelMap = buildProcessModelMap(instances, tenantId);
        Map<String, ProcessCategory> categoryMap = buildCategoryMap(processModelMap.values().stream().toList(), tenantId);
        return instances.stream()
                .map(instance -> buildUnarchivedVO(instance, processModelMap.get(instance.getProcessModelId()),
                        categoryMap))
                .toList();
    }

    private ArchiveRecordVO buildUnarchivedVO(ProcessInstance instance, ProcessModel processModel,
            Map<String, ProcessCategory> categoryMap) {
        ArchiveRecordVO vo = new ArchiveRecordVO();
        vo.setId(instance.getId());
        vo.setCreateBy(instance.getCreateBy());
        vo.setCreateTime(instance.getCreateTime());
        vo.setUpdateBy(instance.getUpdateBy());
        vo.setUpdateTime(instance.getUpdateTime());
        vo.setTenantId(instance.getTenantId());
        vo.setProcessInstanceId(instance.getId());
        vo.setProcessModelId(instance.getProcessModelId());
        vo.setCategoryId(processModel == null ? null : processModel.getCategoryId());
        ProcessCategory category = processModel == null ? null : categoryMap.get(processModel.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getCategoryName());
        vo.setProcessKey(processModel == null ? null : processModel.getProcessKey());
        vo.setProcessName(processModel == null ? null : processModel.getProcessName());
        vo.setProcessVersion(processModel == null ? null : processModel.getVersion());
        vo.setFormInstanceId(instance.getFormInstanceId());
        vo.setFormDefinitionId(instance.getFormDefinitionId());
        vo.setInstanceNo(instance.getInstanceNo());
        vo.setInstanceTitle(instance.getInstanceTitle());
        vo.setStarterUserId(instance.getStarterUserId());
        vo.setStarterUsername(instance.getStarterUsername());
        vo.setStarterRealname(instance.getStarterRealname());
        vo.setInstanceStatus(instance.getStatus());
        vo.setProcessStartTime(instance.getStartTime());
        vo.setProcessEndTime(instance.getEndTime());
        return vo;
    }

    private ArchiveRecordVO buildArchiveRecordVO(ArchiveRecord record) {
        ArchiveRecordVO vo = new ArchiveRecordVO();
        vo.setId(record.getId());
        vo.setCreateBy(record.getCreateBy());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setUpdateTime(record.getUpdateTime());
        vo.setTenantId(record.getTenantId());
        vo.setProcessInstanceId(record.getProcessInstanceId());
        vo.setProcessModelId(record.getProcessModelId());
        vo.setCategoryId(record.getCategoryId());
        vo.setCategoryName(record.getCategoryName());
        vo.setProcessKey(record.getProcessKey());
        vo.setProcessName(record.getProcessName());
        vo.setProcessVersion(record.getProcessVersion());
        vo.setFormInstanceId(record.getFormInstanceId());
        vo.setFormDefinitionId(record.getFormDefinitionId());
        vo.setInstanceNo(record.getInstanceNo());
        vo.setInstanceTitle(record.getInstanceTitle());
        vo.setStarterUserId(record.getStarterUserId());
        vo.setStarterUsername(record.getStarterUsername());
        vo.setStarterRealname(record.getStarterRealname());
        vo.setInstanceStatus(record.getInstanceStatus());
        vo.setProcessStartTime(record.getProcessStartTime());
        vo.setProcessEndTime(record.getProcessEndTime());
        vo.setArchiveSource(record.getArchiveSource());
        vo.setArchiveReason(record.getArchiveReason());
        vo.setArchiverUserId(record.getArchiverUserId());
        vo.setArchiverUsername(record.getArchiverUsername());
        vo.setArchiverRealname(record.getArchiverRealname());
        vo.setArchiveTime(record.getArchiveTime());
        return vo;
    }

    private List<String> listMatchedProcessModelIds(ArchivePageReq req, String tenantId) {
        if (req == null || (!StringUtils.hasText(req.getCategoryId())
                && !StringUtils.hasText(req.getProcessKey())
                && !StringUtils.hasText(req.getProcessName())
                && req.getProcessVersion() == null)) {
            return null;
        }
        QueryWrapper<ProcessModel> wrapper = new QueryWrapper<>();
        wrapper.select("id")
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        if (StringUtils.hasText(req.getCategoryId())) {
            wrapper.eq("category_id", req.getCategoryId());
        }
        if (StringUtils.hasText(req.getProcessKey())) {
            wrapper.eq("process_key", req.getProcessKey());
        }
        if (StringUtils.hasText(req.getProcessName())) {
            wrapper.like("process_name", req.getProcessName());
        }
        if (req.getProcessVersion() != null) {
            wrapper.eq("version", req.getProcessVersion());
        }
        return processModelMapper.selectList(wrapper).stream()
                .map(ProcessModel::getId)
                .toList();
    }

    /**
     * 流程监控左侧树按“最新发布定义”展示，按查询归档时需要覆盖同一流程编码的历史版本实例。
     */
    private List<String> listMonitorMatchedProcessModelIds(AdminMonitorPageReq req, String tenantId) {
        String categoryId = req == null ? null : req.getCategoryId();
        String processKey = req == null ? null : req.getProcessKey();
        String processName = req == null ? null : req.getProcessName();
        Integer processVersion = req == null ? null : req.getProcessVersion();
        if (!StringUtils.hasText(categoryId) && !StringUtils.hasText(processKey)
                && !StringUtils.hasText(processName) && processVersion == null) {
            return null;
        }
        QueryWrapper<ProcessModel> wrapper = new QueryWrapper<ProcessModel>()
                .select("id")
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        if (StringUtils.hasText(categoryId)) {
            List<String> categoryProcessKeys = listPublishedProcessKeysByCategory(categoryId, tenantId);
            if (categoryProcessKeys.isEmpty()) {
                return List.of();
            }
            wrapper.in("process_key", categoryProcessKeys);
        }
        if (StringUtils.hasText(processKey)) {
            wrapper.eq("process_key", processKey);
        }
        if (StringUtils.hasText(processName)) {
            wrapper.like("process_name", processName);
        }
        if (processVersion != null) {
            wrapper.eq("version", processVersion);
        }
        return processModelMapper.selectList(wrapper).stream()
                .map(ProcessModel::getId)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> listPublishedProcessKeysByCategory(String categoryId, String tenantId) {
        return processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .select("process_key")
                        .eq("tenant_id", tenantId)
                        .eq("category_id", categoryId)
                        .eq("status", WorkflowConstants.Status.PUBLISHED)
                        .eq("delete_flag", 0))
                .stream()
                .map(ProcessModel::getProcessKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Map<String, ProcessModel> buildProcessModelMap(List<ProcessInstance> instances, String tenantId) {
        Set<String> processModelIds = instances.stream()
                .map(ProcessInstance::getProcessModelId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (processModelIds.isEmpty()) {
            return Map.of();
        }
        return processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0)
                        .in("id", processModelIds))
                .stream()
                .collect(Collectors.toMap(ProcessModel::getId, Function.identity(), (left, right) -> left));
    }

    private Map<String, ProcessCategory> buildCategoryMap(List<ProcessModel> models, String tenantId) {
        Set<String> categoryIds = models.stream()
                .map(ProcessModel::getCategoryId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return processCategoryMapper.selectList(new QueryWrapper<ProcessCategory>()
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0)
                        .in("id", categoryIds))
                .stream()
                .collect(Collectors.toMap(ProcessCategory::getId, Function.identity(), (left, right) -> left));
    }

    private ArchiveRecord findArchiveRecord(String processInstanceId, String tenantId) {
        return archiveRecordMapper.selectOne(new QueryWrapper<ArchiveRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .last("limit 1"));
    }

    /**
     * 归档详情入口只允许查看已归档记录，或未归档池中的已结束实例，避免借归档权限查看运行中流程。
     */
    private void ensureArchiveDetailVisible(String processInstanceId, String tenantId) {
        ProcessInstance processInstance = requireProcessInstance(processInstanceId, tenantId);
        if (findArchiveRecord(processInstance.getId(), tenantId) != null) {
            return;
        }
        if (!MANUAL_ARCHIVABLE_STATUSES.contains(processInstance.getStatus())) {
            throw new IllegalArgumentException("当前流程不属于归档查看范围");
        }
    }

    /**
     * 归档附件入口先由附件反查实例，再复用归档详情可见范围，避免凭附件 ID 越权读取运行中流程附件。
     */
    private void ensureArchiveAttachmentVisible(String attachmentId, String tenantId) {
        if (!StringUtils.hasText(attachmentId)) {
            throw new IllegalArgumentException("附件ID不能为空");
        }
        Attachment attachment = attachmentMapper.selectOne(new QueryWrapper<Attachment>()
                .eq("tenant_id", tenantId)
                .eq("id", attachmentId)
                .eq("status", WorkflowConstants.AttachmentStatus.ACTIVE)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (attachment == null) {
            throw new IllegalArgumentException("审批附件不存在");
        }
        ensureArchiveDetailVisible(attachment.getProcessInstanceId(), tenantId);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private ProcessInstance requireProcessInstance(String processInstanceId, String tenantId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("tenant_id", tenantId)
                .eq("id", processInstanceId)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (processInstance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        return processInstance;
    }

    private ProcessModel requireProcessModel(String processModelId, String tenantId) {
        ProcessModel processModel = processModelMapper.selectOne(new QueryWrapper<ProcessModel>()
                .eq("tenant_id", tenantId)
                .eq("id", processModelId)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (processModel == null) {
            throw new IllegalArgumentException("流程定义不存在");
        }
        return processModel;
    }

    private ProcessCategory findProcessCategory(String categoryId, String tenantId) {
        if (!StringUtils.hasText(categoryId)) {
            return null;
        }
        return processCategoryMapper.selectOne(new QueryWrapper<ProcessCategory>()
                .eq("tenant_id", tenantId)
                .eq("id", categoryId)
                .eq("delete_flag", 0)
                .last("limit 1"));
    }

    private String resolveTenantId(RequestContext context, String processInstanceId) {
        if (context != null && StringUtils.hasText(context.getTenantId())) {
            return context.getTenantId();
        }
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (processInstance == null || !StringUtils.hasText(processInstance.getTenantId())) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        return processInstance.getTenantId();
    }

    private String requireProcessInstanceId(ArchiveActionReq req) {
        String processInstanceId = req == null ? null : req.getProcessInstanceId();
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        return processInstanceId;
    }

    private List<String> normalizeProcessInstanceIds(ArchiveActionReq req) {
        List<String> ids = req == null ? null : req.getProcessInstanceIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需要归档的流程");
        }
        LinkedHashSet<String> normalized = ids.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请选择需要归档的流程");
        }
        return normalized.stream().toList();
    }

    private <T> void applyDateRange(QueryWrapper<T> wrapper, String column, String startValue, String endValue,
            String fieldName) {
        if (StringUtils.hasText(startValue)) {
            wrapper.ge(column, parseDateTime(startValue, fieldName + "开始值不合法"));
        }
        if (StringUtils.hasText(endValue)) {
            wrapper.le(column, parseDateTime(endValue, fieldName + "结束值不合法"));
        }
    }

    private LocalDateTime parseDateTime(String value, String message) {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private <T> void applyAllowedSorting(QueryWrapper<T> wrapper, String sortField, String sortOrder,
            Map<String, String> allowedFields, String defaultTimeColumn) {
        boolean sorted = false;
        if (StringUtils.hasText(sortField)) {
            for (String field : sortField.split(",")) {
                String column = allowedFields.get(field.trim());
                if (!StringUtils.hasText(column)) {
                    continue;
                }
                if ("asc".equalsIgnoreCase(sortOrder)) {
                    wrapper.orderByAsc(column);
                } else {
                    wrapper.orderByDesc(column);
                }
                sorted = true;
            }
        }
        if (!sorted) {
            wrapper.orderByDesc(defaultTimeColumn).orderByDesc("create_time");
        }
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void markRollbackIfActive() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (Exception ignored) {
            // 查询接口复用异常处理时可能不存在事务，忽略即可。
        }
    }
}
