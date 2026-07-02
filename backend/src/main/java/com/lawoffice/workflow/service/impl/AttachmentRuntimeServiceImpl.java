package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;
import com.lawoffice.workflow.entity.Attachment;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.AttachmentBindReq;
import com.lawoffice.workflow.service.IAttachmentRuntimeService;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.vo.AttachmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AttachmentRuntimeServiceImpl implements IAttachmentRuntimeService {

    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final Set<String> SUPPORTED_ATTACHMENT_SOURCES = Set.of(
            WorkflowConstants.AttachmentSource.START,
            WorkflowConstants.AttachmentSource.TASK,
            WorkflowConstants.AttachmentSource.COMMENT
    );

    private final AttachmentMapper attachmentMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IRuntimeAccessService runtimeAccessService;
    private final ISysFilesService sysFilesService;

    public AttachmentRuntimeServiceImpl(AttachmentMapper attachmentMapper,
            ProcessInstanceMapper processInstanceMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IRuntimeAccessService runtimeAccessService,
            ISysFilesService sysFilesService) {
        this.attachmentMapper = attachmentMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.runtimeAccessService = runtimeAccessService;
        this.sysFilesService = sysFilesService;
    }

    @Override
    public BaseResult<List<AttachmentVO>> listByInstance(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processInstanceId)) {
                throw new IllegalArgumentException("流程实例ID不能为空");
            }
            requireAccessibleInstance(processInstanceId, tenantId, context);
            List<Attachment> attachments = listActiveAttachments(processInstanceId, tenantId);
            return BaseResult.success(buildAttachmentVOList(attachments));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询审批附件失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<List<AttachmentVO>> listByInstanceForGrantedAccess(String processInstanceId,
            RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processInstanceId)) {
                throw new IllegalArgumentException("流程实例ID不能为空");
            }
            requireExistingInstance(processInstanceId, tenantId);
            List<Attachment> attachments = listActiveAttachments(processInstanceId, tenantId);
            return BaseResult.success(buildAttachmentVOList(attachments));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询审批附件失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<AttachmentVO> bind(AttachmentBindReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            validateBindReq(req);
            ProcessInstance processInstance = requireAccessibleInstance(req.getProcessInstanceId(), tenantId, context);
            ensureUploadAllowed(processInstance, req, tenantId, userId);
            FileRelationVO fileRelation = bindBusinessFile(req, context);

            Attachment attachment = new Attachment();
            attachment.setTenantId(tenantId);
            attachment.setProcessInstanceId(req.getProcessInstanceId());
            attachment.setTaskId(req.getTaskId());
            attachment.setNodeId(req.getNodeId());
            attachment.setNodeName(req.getNodeName());
            attachment.setFileId(req.getFileId());
            attachment.setFileRelationId(fileRelation.getId());
            attachment.setAttachmentSource(req.getAttachmentSource());
            attachment.setUploaderUserId(userId);
            attachment.setUploaderUsername(context.getUsername());
            attachment.setStatus(WorkflowConstants.AttachmentStatus.ACTIVE);
            attachment.setSortOrder(0);
            attachment.setRemark(req.getRemark());
            EntityFillUtils.fillAuditFields(attachment, context, true);
            attachmentMapper.insert(attachment);
            return BaseResult.success(BeanUtil.toBean(attachment, AttachmentVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("绑定审批附件失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> delete(String attachmentId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            RuntimeSupport.requireUserId(context);
            if (!StringUtils.hasText(attachmentId)) {
                throw new IllegalArgumentException("附件ID不能为空");
            }
            Attachment attachment = attachmentMapper.selectOne(new QueryWrapper<Attachment>()
                    .eq("id", attachmentId)
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0));
            if (attachment == null) {
                throw new IllegalArgumentException("审批附件不存在");
            }
            ProcessInstance processInstance = requireAccessibleInstance(
                    attachment.getProcessInstanceId(), tenantId, context);
            ensureDeleteAllowed(processInstance, attachment, tenantId, context.getUserId());
            attachment.setStatus(WorkflowConstants.AttachmentStatus.DELETED);
            EntityFillUtils.fillDeleteFields(attachment, RuntimeSupport.username(context));
            attachmentMapper.updateById(attachment);
            if (StringUtils.hasText(attachment.getFileRelationId())) {
                sysFilesService.unbindFile(RuntimeSupport.username(context), attachment.getFileRelationId());
            }
            return BaseResult.success();
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("删除审批附件失败: " + e.getMessage());
        }
    }

    @Override
    public FileUploadVO requireFile(String attachmentId, RequestContext context) {
        Attachment attachment = requireAccessibleAttachment(attachmentId, context);
        return sysFilesService.getFileById(attachment.getFileId());
    }

    @Override
    public FileUploadVO requireFileForGrantedAccess(String attachmentId, RequestContext context) {
        Attachment attachment = requireAttachmentForGrantedAccess(attachmentId, context);
        return sysFilesService.getFileById(attachment.getFileId());
    }

    @Override
    public InputStream downloadContent(String attachmentId, RequestContext context) {
        Attachment attachment = requireAccessibleAttachment(attachmentId, context);
        return sysFilesService.downloadFileContent(attachment.getFileId());
    }

    @Override
    public InputStream downloadContentForGrantedAccess(String attachmentId, RequestContext context) {
        Attachment attachment = requireAttachmentForGrantedAccess(attachmentId, context);
        return sysFilesService.downloadFileContent(attachment.getFileId());
    }

    @Override
    public WorkflowDownloadFile downloadPackageByInstance(String processInstanceId, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = requireAccessibleInstance(processInstanceId, tenantId, context);
        List<Attachment> attachments = listActiveAttachments(processInstanceId, tenantId);
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("暂无可下载附件");
        }
        byte[] zipContent = buildAttachmentPackage(attachments);
        return new WorkflowDownloadFile(buildPackageFileName(processInstance) + ".zip", ZIP_CONTENT_TYPE, zipContent);
    }

    @Override
    public WorkflowDownloadFile downloadPackageByInstanceForGrantedAccess(String processInstanceId,
            RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = requireExistingInstance(processInstanceId, tenantId);
        List<Attachment> attachments = listActiveAttachments(processInstanceId, tenantId);
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("暂无可下载附件");
        }
        byte[] zipContent = buildAttachmentPackage(attachments);
        return new WorkflowDownloadFile(buildPackageFileName(processInstance) + ".zip", ZIP_CONTENT_TYPE, zipContent);
    }

    private void validateBindReq(AttachmentBindReq req) {
        if (req == null) {
            throw new IllegalArgumentException("附件绑定请求不能为空");
        }
        if (!StringUtils.hasText(req.getProcessInstanceId())) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        if (!StringUtils.hasText(req.getFileId())) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        if (!StringUtils.hasText(req.getAttachmentSource())) {
            throw new IllegalArgumentException("附件来源不能为空");
        }
        if (!SUPPORTED_ATTACHMENT_SOURCES.contains(req.getAttachmentSource())) {
            throw new IllegalArgumentException("附件来源不支持");
        }
    }

    /**
     * 审批附件在文件中心统一按流程实例建立业务关系，文档中心再由 Provider 解析流程名称分组。
     */
    private FileRelationVO bindBusinessFile(AttachmentBindReq req, RequestContext context) {
        FileRelationReq relationReq = new FileRelationReq();
        relationReq.setFileId(req.getFileId());
        relationReq.setBizType(WorkflowConstants.BusinessDocument.APPROVAL_BIZ_TYPE);
        relationReq.setBizId(req.getProcessInstanceId());
        relationReq.setRelationType(1);
        relationReq.setSortOrder(0);
        return sysFilesService.bindFile(RuntimeSupport.username(context), relationReq);
    }

    private List<AttachmentVO> buildAttachmentVOList(List<Attachment> attachments) {
        return attachments.stream()
                .map(this::buildAttachmentVO)
                .toList();
    }

    private List<Attachment> listActiveAttachments(String processInstanceId, String tenantId) {
        return attachmentMapper.selectList(new QueryWrapper<Attachment>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.AttachmentStatus.ACTIVE)
                .eq("delete_flag", 0)
                .orderByAsc("sort_order")
                .orderByAsc("create_time"));
    }

    private byte[] buildAttachmentPackage(List<Attachment> attachments) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            Set<String> usedNames = new HashSet<>();
            for (Attachment attachment : attachments) {
                addAttachmentEntry(zipOutputStream, attachment, usedNames);
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("打包审批附件失败", e);
        }
    }

    private void addAttachmentEntry(ZipOutputStream zipOutputStream, Attachment attachment,
            Set<String> usedNames) throws IOException {
        FileUploadVO file = sysFilesService.getFileById(attachment.getFileId());
        String fileName = file != null && StringUtils.hasText(file.getFileName())
                ? file.getFileName()
                : attachment.getFileId();
        String entryName = uniqueZipEntryName(fileName, usedNames);
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        try (InputStream inputStream = sysFilesService.downloadFileContent(attachment.getFileId())) {
            inputStream.transferTo(zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }

    private String uniqueZipEntryName(String name, Set<String> usedNames) {
        String safeName = safeZipEntryName(name);
        if (usedNames.add(safeName)) {
            return safeName;
        }
        int dotIndex = safeName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? safeName.substring(0, dotIndex) : safeName;
        String extension = dotIndex > 0 ? safeName.substring(dotIndex) : "";
        int index = 1;
        String candidate;
        do {
            candidate = baseName + "(" + index++ + ")" + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    private String safeZipEntryName(String name) {
        return StringUtils.hasText(name) ? name.replace("\\", "_")
                .replace("/", "_")
                .replace("\r", "_")
                .replace("\n", "_") : "未命名";
    }

    private String buildPackageFileName(ProcessInstance processInstance) {
        String title = StringUtils.hasText(processInstance.getInstanceTitle())
                ? processInstance.getInstanceTitle()
                : "审批附件";
        String fileName = StringUtils.hasText(processInstance.getInstanceNo())
                ? title + "-" + processInstance.getInstanceNo()
                : title;
        return fileName.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
    }

    private AttachmentVO buildAttachmentVO(Attachment attachment) {
        AttachmentVO vo = BeanUtil.toBean(attachment, AttachmentVO.class);
        if (StringUtils.hasText(attachment.getFileId())) {
            fillFileInfo(vo, attachment.getFileId());
        }
        return vo;
    }

    private void fillFileInfo(AttachmentVO vo, String fileId) {
        try {
            FileUploadVO file = sysFilesService.getFileById(fileId);
            if (file == null) {
                return;
            }
            vo.setFileName(file.getFileName());
            vo.setFileType(file.getFileType());
            vo.setFileSize(file.getFileSize());
        } catch (IllegalArgumentException ignored) {
            // 附件业务记录保留，文件元数据缺失时前端仍可显示附件记录并由下载接口返回明确错误。
        }
    }

    private Attachment requireAccessibleAttachment(String attachmentId, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        if (!StringUtils.hasText(attachmentId)) {
            throw new IllegalArgumentException("附件ID不能为空");
        }
        Attachment attachment = attachmentMapper.selectOne(new QueryWrapper<Attachment>()
                .eq("id", attachmentId)
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.AttachmentStatus.ACTIVE)
                .eq("delete_flag", 0));
        if (attachment == null) {
            throw new IllegalArgumentException("审批附件不存在");
        }
        requireAccessibleInstance(attachment.getProcessInstanceId(), tenantId, context);
        return attachment;
    }

    private Attachment requireAttachmentForGrantedAccess(String attachmentId, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        if (!StringUtils.hasText(attachmentId)) {
            throw new IllegalArgumentException("附件ID不能为空");
        }
        Attachment attachment = attachmentMapper.selectOne(new QueryWrapper<Attachment>()
                .eq("id", attachmentId)
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.AttachmentStatus.ACTIVE)
                .eq("delete_flag", 0));
        if (attachment == null) {
            throw new IllegalArgumentException("审批附件不存在");
        }
        requireExistingInstance(attachment.getProcessInstanceId(), tenantId);
        return attachment;
    }

    /**
     * 归档等外层入口已完成业务鉴权时，只需要确认实例属于当前租户且未被删除。
     */
    private ProcessInstance requireExistingInstance(String processInstanceId, String tenantId) {
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        return processInstance;
    }

    /**
     * 附件查看权复用审批实例访问权，确保抄送人只能通过实例访问权查看附件，陌生用户不能枚举附件。
     */
    private ProcessInstance requireAccessibleInstance(String processInstanceId, String tenantId, RequestContext context) {
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        runtimeAccessService.ensureInstanceAccess(processInstance, context);
        return processInstance;
    }

    /**
     * 二期附件上传仅开放给流程发起人和当前办理人；抄送人虽然有查看权，但不能补充材料。
     */
    private void ensureUploadAllowed(ProcessInstance processInstance, AttachmentBindReq req,
            String tenantId, String userId) {
        ensureInstanceEditable(processInstance, "流程已结束，不能新增附件");
        if (userId.equals(processInstance.getStarterUserId())) {
            return;
        }
        if (StringUtils.hasText(req.getTaskId()) && isCurrentTaskOperator(req.getTaskId(),
                processInstance.getId(), tenantId, userId)) {
            return;
        }
        throw new IllegalArgumentException("只有发起人或当前办理人可以上传审批附件");
    }

    /**
     * 附件删除只允许上传人操作，并且实例和绑定任务都必须仍处于可办理状态，避免改写历史审批材料。
     */
    private void ensureDeleteAllowed(ProcessInstance processInstance, Attachment attachment,
            String tenantId, String userId) {
        if (!userId.equals(attachment.getUploaderUserId())) {
            throw new IllegalArgumentException("只有附件上传人可以删除附件");
        }
        ensureInstanceEditable(processInstance, "流程已结束，不能删除附件");
        if (StringUtils.hasText(attachment.getTaskId())
                && !isTaskTodo(attachment.getTaskId(), processInstance.getId(), tenantId)) {
            throw new IllegalArgumentException("任务已完成，不能删除附件");
        }
    }

    private void ensureInstanceEditable(ProcessInstance processInstance, String message) {
        if (!WorkflowConstants.Status.RUNNING.equals(processInstance.getStatus())
                && !WorkflowConstants.Status.DRAFT.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isCurrentTaskOperator(String taskId, String processInstanceId, String tenantId, String userId) {
        Task task = selectTask(taskId, processInstanceId, tenantId);
        if (task == null || !WorkflowConstants.Status.TODO.equals(task.getStatus())) {
            return false;
        }
        if (userId.equals(task.getAssigneeUserId())) {
            return true;
        }
        return taskCandidateMapper.selectCount(new QueryWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .eq("task_id", taskId)
                .eq("candidate_user_id", userId)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)) > 0;
    }

    private boolean isTaskTodo(String taskId, String processInstanceId, String tenantId) {
        Task task = selectTask(taskId, processInstanceId, tenantId);
        return task != null && WorkflowConstants.Status.TODO.equals(task.getStatus());
    }

    private Task selectTask(String taskId, String processInstanceId, String tenantId) {
        return taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("id", taskId)
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0));
    }
}
