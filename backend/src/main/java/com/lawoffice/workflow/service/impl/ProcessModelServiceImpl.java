package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableDeploymentResult;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.req.ProcessTemplateCopyReq;
import com.lawoffice.workflow.service.IBpmnSecurityService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IProcessModelService;
import com.lawoffice.workflow.vo.ProcessModelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProcessModelServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessModelMapper, ProcessModel, ProcessModelVO> implements IProcessModelService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LATEST_VERSION_ID_SQL = """
            SELECT latest_model.id
            FROM wf_process_model latest_model
            INNER JOIN (
                SELECT tenant_id, process_key, MAX(version) AS max_version
                FROM wf_process_model
                WHERE delete_flag = 0
                GROUP BY tenant_id, process_key
            ) latest_version
                ON latest_version.tenant_id = latest_model.tenant_id
                AND latest_version.process_key = latest_model.process_key
                AND latest_version.max_version = latest_model.version
            WHERE latest_model.delete_flag = 0
            """;

    private final ProcessCategoryMapper processCategoryMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final FieldPermissionMapper fieldPermissionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final IFlowableService flowableService;
    private final IBpmnSecurityService bpmnSecurityService;

    @Autowired
    public ProcessModelServiceImpl(ProcessCategoryMapper processCategoryMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            FieldPermissionMapper fieldPermissionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            IFlowableService flowableService,
            IBpmnSecurityService bpmnSecurityService) {
        this.processCategoryMapper = processCategoryMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.flowableService = flowableService;
        this.bpmnSecurityService = bpmnSecurityService;
    }

    @Override
    protected void doBeforeList(BaseDTO<ProcessModel> baseDTO) {
        applyTenantAndDefaultSort(baseDTO);
    }

    @Override
    protected void doBeforePage(BasePageDTO<ProcessModel> basePageDTO) {
        applyTenantAndDefaultSort(basePageDTO);
    }

    @Override
    public BaseResult<PageVO<ProcessModelVO>> pageLatest(BasePageDTO<ProcessModel> basePageDTO) {
        try {
            QueryWrapper<ProcessModel> wrapper = ensureQueryWrapper(basePageDTO);
            wrapper.inSql("id", LATEST_VERSION_ID_SQL);
            applyTenantAndDefaultSort(basePageDTO);
            wrapper.eq("delete_flag", 0);

            Page<ProcessModel> page = new Page<>(basePageDTO.getPageNum(), basePageDTO.getPageSize());
            Page<ProcessModel> resultPage = baseMapper.selectPage(page, wrapper);
            List<ProcessModelVO> voList = buildProcessModelVOList(resultPage.getRecords(),
                    resolveTenantId(null, basePageDTO.getContext()));
            PageVO<ProcessModelVO> pageVO = new PageVO<>(voList, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
            return BaseResult.success(pageVO);
        } catch (Exception e) {
            return BaseResult.error("查询流程最新版本失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<List<ProcessModelVO>> listHistory(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            ProcessModel current = requireCurrent(id, tenantId, "流程模型不存在");
            QueryWrapper<ProcessModel> wrapper = new QueryWrapper<>();
            wrapper.eq("tenant_id", tenantId)
                    .eq("process_key", current.getProcessKey())
                    .eq("delete_flag", 0)
                    .orderByDesc("version")
                    .orderByDesc("create_time");
            List<ProcessModel> models = baseMapper.selectList(wrapper);
            return BaseResult.success(buildProcessModelVOList(models, tenantId));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询流程历史版本失败: " + e.getMessage());
        }
    }

    @Override
    protected void doBeforeSave(BaseDTO<ProcessModel> saveDTO) {
        ProcessModel model = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(model, saveDTO);
        normalize(model);

        if (StringUtils.hasText(model.getId())) {
            ProcessModel current = requireCurrent(model.getId(), model.getTenantId(), "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(current.getStatus())) {
                throw new IllegalArgumentException("已发布流程版本不可直接修改，请复制为新版本草稿后调整");
            }
        }

        requireText(model.getProcessKey(), "流程标识不能为空");
        requireText(model.getProcessName(), "流程名称不能为空");
        requireActiveById(processCategoryMapper, model.getCategoryId(), model.getTenantId(), "流程分类不存在");
        requireActiveById(formDefinitionMapper, model.getFormDefinitionId(), model.getTenantId(), "表单定义不存在");
        validateDesignerPayload(model);
        validateUnique(model, "同一租户下流程标识和版本不能重复",
                "process_key", model.getProcessKey(),
                "version", model.getVersion());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<ProcessModel> deleteDTO) {
        for (String id : resolveDeleteIds(deleteDTO)) {
            String tenantId = resolveTenantId(null, deleteDTO.getContext());
            ProcessModel model = requireCurrent(id, tenantId, "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
                throw new IllegalArgumentException("已发布流程版本不可删除");
            }
            if (countActive(processInstanceMapper, tenantId, "process_model_id", id) > 0) {
                throw new IllegalArgumentException("流程已有实例数据，不能删除");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> delete(BaseDTO<ProcessModel> deleteDTO) {
        try {
            doBeforeDelete(deleteDTO);
            String tenantId = resolveTenantId(null, deleteDTO.getContext());
            for (String id : resolveDeleteIds(deleteDTO)) {
                ProcessModel model = requireCurrent(id, tenantId, "流程模型不存在");
                EntityFillUtils.fillDeleteFields(model, deleteDTO.getContext() == null ? "system" : deleteDTO.getContext().getUsername());
                baseMapper.updateById(model);
            }
            doAfterDelete(deleteDTO);
            return BaseResult.success();
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("删除流程失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<ProcessModel> deleteDTO) {
        String tenantId = resolveTenantId(null, deleteDTO.getContext());
        for (String id : resolveDeleteIds(deleteDTO)) {
            logicDeleteChildren(processNodeConfigMapper, tenantId, id, deleteDTO.getContext());
            logicDeleteChildren(fieldPermissionMapper, tenantId, id, deleteDTO.getContext());
            logicDeleteChildren(processStartPermissionMapper, tenantId, id, deleteDTO.getContext());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> publish(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            ProcessModel model = requireCurrent(id, tenantId, "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
                return BaseResult.success(buildProcessModelVO(model, tenantId));
            }
            if (!WorkflowConstants.Status.DRAFT.equals(model.getStatus())) {
                throw new IllegalArgumentException("只有草稿流程可以发布");
            }
            validateBeforePublish(model);
            FlowableDeploymentResult deploymentResult = flowableService.deployProcessModel(model);
            model.setFlowableDeploymentId(deploymentResult.getDeploymentId());
            model.setFlowableProcessDefinitionId(deploymentResult.getProcessDefinitionId());
            model.setStatus(WorkflowConstants.Status.PUBLISHED);
            model.setPublishedTime(LocalDateTime.now());
            fillUpdate(model, context);
            updateById(model);
            disableOtherPublishedVersions(model, context);
            return BaseResult.success(buildProcessModelVO(model, tenantId));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("发布流程失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> copyAsDraft(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            ProcessModel source = requireCurrent(id, tenantId, "流程模型不存在");
            ensureNoDraftVersion(source.getTenantId(), source.getProcessKey());
            ProcessModel draft = BeanUtil.copyProperties(source, ProcessModel.class);
            draft.setId(null);
            draft.setVersion(resolveNextVersion(source.getTenantId(), source.getProcessKey()));
            draft.setStatus(WorkflowConstants.Status.DRAFT);
            draft.setPublishedTime(null);
            draft.setFlowableDeploymentId(null);
            draft.setFlowableProcessDefinitionId(null);
            EntityFillUtils.fillAuditFields(draft, context, true);
            save(draft);
            copyChildren(source.getId(), draft.getId(), tenantId, context);
            return BaseResult.success(buildProcessModelVO(draft, tenantId));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("复制流程版本失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> copyTemplate(ProcessTemplateCopyReq req, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            String sourceModelId = resolveSourceModelId(req);
            ProcessModel sourceModel = requireCurrent(sourceModelId, tenantId, "来源流程模型不存在");
            requireActiveById(formDefinitionMapper, sourceModel.getFormDefinitionId(), tenantId, "来源表单定义不存在");

            normalizeCopyReq(req);
            String targetCategoryId = StringUtils.hasText(req.getCategoryId()) ? req.getCategoryId() : sourceModel.getCategoryId();
            requireActiveById(processCategoryMapper, targetCategoryId, tenantId, "流程分类不存在");
            FormDefinition targetForm = requireActiveById(formDefinitionMapper, req.getFormDefinitionId(), tenantId, "绑定表单不存在");
            if (!WorkflowConstants.Status.PUBLISHED.equals(targetForm.getStatus())) {
                throw new IllegalArgumentException("复制模板必须绑定已发布表单版本");
            }
            ensureTemplateKeyAvailable(tenantId, req.getProcessKey());

            ProcessModel targetModel = buildCopiedProcess(sourceModel, req, targetCategoryId, targetForm.getId(), context);
            baseMapper.insert(targetModel);
            copyChildren(sourceModel.getId(), targetModel.getId(), tenantId, context,
                    sourceModel.getFormDefinitionId().equals(targetForm.getId()));

            return BaseResult.success(buildProcessModelVO(targetModel, tenantId));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("复制审批模板失败: " + e.getMessage());
        }
    }

    /**
     * 同一流程编码只保留最新发布版本可发起，旧发布版本转为禁用，历史实例继续引用原版本快照。
     */
    private void disableOtherPublishedVersions(ProcessModel publishedModel, RequestContext context) {
        ProcessModel update = new ProcessModel();
        update.setStatus(WorkflowConstants.Status.DISABLED);
        EntityFillUtils.fillAuditFields(update, context, false);
        baseMapper.update(update, new QueryWrapper<ProcessModel>()
                .eq("tenant_id", publishedModel.getTenantId())
                .eq("process_key", publishedModel.getProcessKey())
                .eq("status", WorkflowConstants.Status.PUBLISHED)
                .eq("delete_flag", 0)
                .ne("id", publishedModel.getId()));
    }

    private void normalize(ProcessModel model) {
        model.setProcessKey(trimToNull(model.getProcessKey()));
        model.setProcessName(trimToNull(model.getProcessName()));
        if (!StringUtils.hasText(model.getStatus())) {
            model.setStatus(WorkflowConstants.Status.DRAFT);
        }
        validateIn(model.getStatus(), "流程状态不合法",
                WorkflowConstants.Status.DRAFT,
                WorkflowConstants.Status.PUBLISHED,
                WorkflowConstants.Status.DISABLED);
        if (!StringUtils.hasText(model.getDesignerType())) {
            model.setDesignerType(WorkflowConstants.DesignerType.SIMPLE);
        }
        validateIn(model.getDesignerType(), "设计器类型不合法",
                WorkflowConstants.DesignerType.SIMPLE,
                WorkflowConstants.DesignerType.BPMN);
        if (!StringUtils.hasText(model.getStartScopeType())) {
            model.setStartScopeType(WorkflowConstants.StartScopeType.ALL);
        }
        validateIn(model.getStartScopeType(), "发起范围不合法",
                WorkflowConstants.StartScopeType.ALL,
                WorkflowConstants.StartScopeType.SPECIFIED);
        if (model.getVersion() == null) {
            model.setVersion(resolveNextVersion(model.getTenantId(), model.getProcessKey()));
        }
    }

    private List<ProcessModelVO> buildProcessModelVOList(List<ProcessModel> models, String tenantId) {
        Map<String, FormDefinition> formMap = buildFormDefinitionMap(models, tenantId);
        return models.stream()
                .map(model -> buildProcessModelVO(model, formMap.get(model.getFormDefinitionId())))
                .toList();
    }

    private ProcessModelVO buildProcessModelVO(ProcessModel model, String tenantId) {
        if (model == null) {
            return null;
        }
        FormDefinition form = null;
        if (StringUtils.hasText(model.getFormDefinitionId())) {
            form = formDefinitionMapper.selectOne(new QueryWrapper<FormDefinition>()
                    .select("id", "form_key", "form_name", "version")
                    .eq("id", model.getFormDefinitionId())
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0)
                    .last("LIMIT 1"));
        }
        return buildProcessModelVO(model, form);
    }

    private ProcessModelVO buildProcessModelVO(ProcessModel model, FormDefinition form) {
        ProcessModelVO vo = BeanUtil.toBean(model, ProcessModelVO.class);
        if (form != null) {
            vo.setFormKey(form.getFormKey());
            vo.setFormName(form.getFormName());
            vo.setFormVersion(form.getVersion());
        }
        return vo;
    }

    private Map<String, FormDefinition> buildFormDefinitionMap(List<ProcessModel> models, String tenantId) {
        Set<String> formIds = models.stream()
                .map(ProcessModel::getFormDefinitionId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (formIds.isEmpty()) {
            return Map.of();
        }
        return formDefinitionMapper.selectList(new QueryWrapper<FormDefinition>()
                        .select("id", "form_key", "form_name", "version")
                        .in("id", formIds)
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .stream()
                .collect(Collectors.toMap(
                        FormDefinition::getId,
                        form -> form,
                        (left, right) -> left));
    }

    private String resolveSourceModelId(ProcessTemplateCopyReq req) {
        if (req == null) {
            throw new IllegalArgumentException("复制模板请求不能为空");
        }
        String sourceModelId = trimToNull(req.getSourceProcessModelId());
        if (!StringUtils.hasText(sourceModelId)) {
            sourceModelId = trimToNull(req.getId());
        }
        if (!StringUtils.hasText(sourceModelId)) {
            throw new IllegalArgumentException("来源流程模型不能为空");
        }
        return sourceModelId;
    }

    private void normalizeCopyReq(ProcessTemplateCopyReq req) {
        req.setCategoryId(trimToNull(req.getCategoryId()));
        req.setFormDefinitionId(trimToNull(req.getFormDefinitionId()));
        req.setProcessKey(trimToNull(req.getProcessKey()));
        req.setProcessName(trimToNull(req.getProcessName()));
        req.setRemark(trimToNull(req.getRemark()));
        requireText(req.getFormDefinitionId(), "绑定表单不能为空");
        requireText(req.getProcessKey(), "流程编码不能为空");
        requireText(req.getProcessName(), "流程名称不能为空");
    }

    private void ensureTemplateKeyAvailable(String tenantId, String processKey) {
        long processCount = baseMapper.selectCount(new QueryWrapper<ProcessModel>()
                .eq("tenant_id", tenantId)
                .eq("process_key", processKey)
                .eq("delete_flag", 0));
        if (processCount > 0) {
            throw new IllegalArgumentException("流程编码已存在，请更换后再复制");
        }
    }

    /**
     * 模板复制只复制定义内容，发布状态和 Flowable 部署标识必须清空，避免新模板误用来源流程运行态。
     */
    private ProcessModel buildCopiedProcess(ProcessModel sourceModel, ProcessTemplateCopyReq req,
            String categoryId, String formDefinitionId, RequestContext context) {
        ProcessModel target = BeanUtil.copyProperties(sourceModel, ProcessModel.class);
        target.setId(null);
        target.setCategoryId(categoryId);
        target.setFormDefinitionId(formDefinitionId);
        target.setProcessKey(req.getProcessKey());
        target.setProcessName(req.getProcessName());
        target.setVersion(1);
        target.setStatus(WorkflowConstants.Status.DRAFT);
        target.setPublishedTime(null);
        target.setFlowableDeploymentId(null);
        target.setFlowableProcessDefinitionId(null);
        target.setBpmnSecurityStatus(null);
        target.setBpmnSecurityMessage(null);
        target.setBpmnXml(adaptCopiedBpmnXml(sourceModel, req));
        target.setRemark(buildCopyRemark(req.getRemark(), "来源流程: " + sourceModel.getProcessName() + "(" + sourceModel.getProcessKey() + ")"));
        EntityFillUtils.fillAuditFields(target, context, true);
        return target;
    }

    /**
     * BPMN XML 中 process id 就是 Flowable 流程定义 key。复制模板更换流程编码后，
     * 必须同步 XML 内部 id，否则发布时部署出的 key 仍是来源流程编码。
     */
    private String adaptCopiedBpmnXml(ProcessModel sourceModel, ProcessTemplateCopyReq req) {
        if (!StringUtils.hasText(sourceModel.getBpmnXml())) {
            return sourceModel.getBpmnXml();
        }
        try {
            return adaptBpmnXmlProcessIdentity(sourceModel.getBpmnXml(), req.getProcessKey(), req.getProcessName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("复制流程时更新BPMN XML失败");
        }
    }

    private String adaptBpmnXmlProcessIdentity(String bpmnXml, String processKey, String processName) throws Exception {
        Document document = parseBpmnDocument(bpmnXml);
        Element process = requireSingleBpmnProcess(document);
        String oldProcessId = process.getAttribute("id");
        process.setAttribute("id", processKey);
        process.setAttribute("name", processName);
        updateBpmnDiagramProcessReferences(document, oldProcessId, processKey);
        return serializeBpmnDocument(document);
    }

    private Element requireSingleBpmnProcess(Document document) {
        NodeList processes = document.getElementsByTagNameNS("*", "process");
        if (processes.getLength() != 1) {
            throw new IllegalArgumentException("BPMN XML必须包含且只包含一个流程定义");
        }
        Element process = (Element) processes.item(0);
        if (!StringUtils.hasText(process.getAttribute("id"))) {
            throw new IllegalArgumentException("BPMN XML中的流程ID不能为空");
        }
        return process;
    }

    private void updateBpmnDiagramProcessReferences(Document document, String oldProcessId, String newProcessId) {
        NodeList planes = document.getElementsByTagNameNS("*", "BPMNPlane");
        for (int i = 0; i < planes.getLength(); i++) {
            Element plane = (Element) planes.item(i);
            if (oldProcessId.equals(plane.getAttribute("bpmnElement"))) {
                plane.setAttribute("bpmnElement", newProcessId);
            }
        }
        Element root = document.getDocumentElement();
        if (root != null && oldProcessId != null && root.hasAttribute("id")) {
            root.setAttribute("id", "Definitions_" + newProcessId);
        }
    }

    private String serializeBpmnDocument(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private String buildCopyRemark(String userRemark, String sourceText) {
        String remark = StringUtils.hasText(userRemark)
                ? userRemark + "；复制来源：" + sourceText
                : "复制来源：" + sourceText;
        return remark.length() <= 500 ? remark : remark.substring(0, 500);
    }

    private void validateBeforePublish(ProcessModel model) {
        validateDesignerPayload(model);
        requireText(model.getBpmnXml(), "BPMN XML不能为空");
        normalizeBpmnXmlBeforePublish(model);
        if (WorkflowConstants.DesignerType.BPMN.equals(model.getDesignerType())) {
            String message = bpmnSecurityService.validateBpmnXml(model.getBpmnXml());
            model.setBpmnSecurityStatus(WorkflowConstants.BpmnSecurityStatus.PASSED);
            model.setBpmnSecurityMessage(message);
        }
        FormDefinition form = requireActiveById(formDefinitionMapper, model.getFormDefinitionId(), model.getTenantId(), "表单定义不存在");
        if (!WorkflowConstants.Status.PUBLISHED.equals(form.getStatus())) {
            throw new IllegalArgumentException("流程发布必须绑定已发布表单版本");
        }
        if (countActive(processNodeConfigMapper, model.getTenantId(),
                "process_model_id", model.getId(),
                "node_type", WorkflowConstants.NodeType.APPROVER) == 0) {
            throw new IllegalArgumentException("流程至少需要配置一个审批节点");
        }
        validateNodeConfigsBeforePublish(model);
        validateBpmnUserTaskNodeConfigs(model);
        if (WorkflowConstants.StartScopeType.SPECIFIED.equals(model.getStartScopeType())
                && countActive(processStartPermissionMapper, model.getTenantId(), "process_model_id", model.getId()) == 0) {
            throw new IllegalArgumentException("指定发起范围时必须配置发起权限");
        }
    }

    /**
     * 历史草稿或复制模板可能残留旧流程编码，发布前兜底修正，保证 Flowable key 与流程标识一致。
     */
    private void normalizeBpmnXmlBeforePublish(ProcessModel model) {
        if (!StringUtils.hasText(model.getBpmnXml())) {
            return;
        }
        try {
            model.setBpmnXml(adaptBpmnXmlProcessIdentity(model.getBpmnXml(), model.getProcessKey(), model.getProcessName()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("发布前更新BPMN XML失败");
        }
    }

    private void validateDesignerPayload(ProcessModel model) {
        if (WorkflowConstants.DesignerType.SIMPLE.equals(model.getDesignerType())) {
            validateJson(model.getNodeJson(), "简单设计器节点JSON", true);
            return;
        }
        if (WorkflowConstants.DesignerType.BPMN.equals(model.getDesignerType())) {
            requireText(model.getBpmnXml(), "BPMN XML不能为空");
            return;
        }
        throw new IllegalArgumentException("设计器类型不合法");
    }

    private void validateNodeConfigsBeforePublish(ProcessModel model) {
        List<ProcessNodeConfig> nodeConfigs = processNodeConfigMapper.selectList(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", model.getTenantId())
                .eq("process_model_id", model.getId())
                .eq("delete_flag", 0));
        Set<String> nodeIds = nodeConfigs.stream()
                .map(ProcessNodeConfig::getNodeId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        for (ProcessNodeConfig nodeConfig : nodeConfigs) {
            validateNodeConfigBeforePublish(nodeConfig, nodeIds);
        }
    }

    private void validateNodeConfigBeforePublish(ProcessNodeConfig nodeConfig, Set<String> nodeIds) {
        if (WorkflowConstants.NodeType.APPROVER.equals(nodeConfig.getNodeType())) {
            if (!StringUtils.hasText(nodeConfig.getAssigneeType())) {
                throw new IllegalArgumentException("审批节点审批人类型不能为空: " + nodeConfig.getNodeName());
            }
            if (!StringUtils.hasText(nodeConfig.getApprovalMode())) {
                throw new IllegalArgumentException("审批节点办理策略不能为空: " + nodeConfig.getNodeName());
            }
            validateIn(nodeConfig.getApprovalMode(), "审批节点办理策略不合法: " + nodeConfig.getNodeName(),
                    WorkflowConstants.ApprovalMode.SINGLE,
                    WorkflowConstants.ApprovalMode.COUNTERSIGN,
                    WorkflowConstants.ApprovalMode.ORSIGN);
            if (!StringUtils.hasText(nodeConfig.getAssigneeResolveMode())) {
                throw new IllegalArgumentException("审批节点执行人确定方式不能为空: " + nodeConfig.getNodeName());
            }
            validateIn(nodeConfig.getAssigneeResolveMode(), "审批节点执行人确定方式不合法: " + nodeConfig.getNodeName(),
                    WorkflowConstants.AssigneeResolveMode.ALL,
                    WorkflowConstants.AssigneeResolveMode.SELECT);
            if (WorkflowConstants.ApprovalMode.SINGLE.equals(nodeConfig.getApprovalMode())
                    && !WorkflowConstants.AssigneeResolveMode.SELECT.equals(nodeConfig.getAssigneeResolveMode())) {
                throw new IllegalArgumentException("单人审批执行人确定方式必须为上一步选择: " + nodeConfig.getNodeName());
            }
            if (!StringUtils.hasText(nodeConfig.getRejectPolicy())) {
                throw new IllegalArgumentException("审批节点不通过策略不能为空: " + nodeConfig.getNodeName());
            }
            validateIn(nodeConfig.getRejectPolicy(), "审批节点不通过策略不合法: " + nodeConfig.getNodeName(),
                    WorkflowConstants.RejectPolicy.TERMINATE);
        }
        if (WorkflowConstants.NodeType.GATEWAY.equals(nodeConfig.getNodeType())) {
            if (!StringUtils.hasText(nodeConfig.getBranchJson())) {
                throw new IllegalArgumentException("网关节点必须配置条件分支: " + nodeConfig.getNodeName());
            }
            validateGatewayBranchTargets(nodeConfig, nodeIds);
        }
    }

    private void validateGatewayBranchTargets(ProcessNodeConfig nodeConfig, Set<String> nodeIds) {
        try {
            JsonNode branches = OBJECT_MAPPER.readTree(nodeConfig.getBranchJson()).get("branches");
            if (branches == null || !branches.isArray() || branches.isEmpty()) {
                throw new IllegalArgumentException("条件分支配置必须包含branches数组: " + nodeConfig.getNodeName());
            }
            boolean hasDefaultBranch = false;
            for (JsonNode branch : branches) {
                String branchId = branch.path("branchId").asText(null);
                String targetNodeId = branch.path("targetNodeId").asText(null);
                if (!StringUtils.hasText(branchId)) {
                    throw new IllegalArgumentException("条件分支ID不能为空: " + nodeConfig.getNodeName());
                }
                if (!StringUtils.hasText(targetNodeId)) {
                    throw new IllegalArgumentException("条件分支目标节点不能为空: " + nodeConfig.getNodeName());
                }
                if (!nodeIds.contains(targetNodeId)) {
                    throw new IllegalArgumentException("条件分支目标节点不存在: " + targetNodeId);
                }
                hasDefaultBranch = hasDefaultBranch || branch.path("defaultBranch").asBoolean(false);
            }
            if (!hasDefaultBranch) {
                throw new IllegalArgumentException("条件分支必须配置默认分支: " + nodeConfig.getNodeName());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("条件分支配置JSON不是合法JSON: " + nodeConfig.getNodeName());
        }
    }

    /**
     * Flowable 运行时以 userTask 的 id 作为 taskDefinitionKey，因此发布前必须确认每个用户任务都有审批配置。
     */
    private void validateBpmnUserTaskNodeConfigs(ProcessModel model) {
        Set<String> userTaskIds = parseBpmnUserTaskIds(model.getBpmnXml());
        if (userTaskIds.isEmpty()) {
            throw new IllegalArgumentException("BPMN流程至少需要一个用户任务节点");
        }

        List<ProcessNodeConfig> configuredNodes = processNodeConfigMapper.selectList(new QueryWrapper<ProcessNodeConfig>()
                        .eq("tenant_id", model.getTenantId())
                        .eq("process_model_id", model.getId())
                        .eq("delete_flag", 0));
        Set<String> configuredNodeIds = configuredNodes.stream()
                .filter(config -> WorkflowConstants.NodeType.APPROVER.equals(config.getNodeType()))
                .map(ProcessNodeConfig::getNodeId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        for (String userTaskId : userTaskIds) {
            if (!configuredNodeIds.contains(userTaskId)) {
                throw new IllegalArgumentException("BPMN用户任务缺少节点配置: " + userTaskId);
            }
        }

        Map<String, Set<String>> gatewayOutgoingTargets = parseBpmnExclusiveGatewayOutgoingTargets(model.getBpmnXml());
        Map<String, ProcessNodeConfig> configuredGateways = configuredNodes.stream()
                .filter(config -> WorkflowConstants.NodeType.GATEWAY.equals(config.getNodeType()))
                .filter(config -> StringUtils.hasText(config.getNodeId()))
                .collect(java.util.stream.Collectors.toMap(
                        ProcessNodeConfig::getNodeId,
                        config -> config,
                        (left, right) -> left
                ));
        for (Map.Entry<String, Set<String>> entry : gatewayOutgoingTargets.entrySet()) {
            String gatewayId = entry.getKey();
            ProcessNodeConfig gatewayConfig = configuredGateways.get(gatewayId);
            if (gatewayConfig == null) {
                throw new IllegalArgumentException("BPMN排他网关缺少条件分支配置: " + gatewayId);
            }
            validateGatewayBpmnTargets(gatewayConfig, entry.getValue());
        }
    }

    /**
     * 使用 JDK XML 解析器提取 BPMN userTask，禁用外部实体以避免解析 XML 时产生外部资源访问。
     */
    private Set<String> parseBpmnUserTaskIds(String bpmnXml) {
        return parseBpmnElementIds(bpmnXml, "userTask", "BPMN用户任务");
    }

    /**
     * 排他网关的图形出线必须和结构化分支配置一致，避免 BPMN XML 里出现未受控的路由路径。
     */
    private Map<String, Set<String>> parseBpmnExclusiveGatewayOutgoingTargets(String bpmnXml) {
        Map<String, Set<String>> gatewayTargets = new HashMap<>();
        try {
            Document document = parseBpmnDocument(bpmnXml);
            NodeList gateways = document.getElementsByTagNameNS("*", "exclusiveGateway");
            for (int i = 0; i < gateways.getLength(); i++) {
                Element gateway = (Element) gateways.item(i);
                String gatewayId = gateway.getAttribute("id");
                if (!StringUtils.hasText(gatewayId)) {
                    throw new IllegalArgumentException("BPMN排他网关节点ID不能为空");
                }
                gatewayTargets.put(gatewayId, new LinkedHashSet<>());
            }

            NodeList sequenceFlows = document.getElementsByTagNameNS("*", "sequenceFlow");
            for (int i = 0; i < sequenceFlows.getLength(); i++) {
                Element flow = (Element) sequenceFlows.item(i);
                String sourceRef = flow.getAttribute("sourceRef");
                if (!gatewayTargets.containsKey(sourceRef)) {
                    continue;
                }
                String targetRef = flow.getAttribute("targetRef");
                if (!StringUtils.hasText(targetRef)) {
                    throw new IllegalArgumentException("BPMN排他网关出线目标节点不能为空: " + sourceRef);
                }
                gatewayTargets.get(sourceRef).add(targetRef);
            }
            return gatewayTargets;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("BPMN XML解析失败");
        }
    }

    private void validateGatewayBpmnTargets(ProcessNodeConfig gatewayConfig, Set<String> bpmnTargets) {
        Set<String> configuredTargets = parseGatewayBranchTargets(gatewayConfig);
        if (bpmnTargets.isEmpty()) {
            throw new IllegalArgumentException("BPMN排他网关必须至少配置一条出线: " + gatewayConfig.getNodeId());
        }
        for (String targetNodeId : bpmnTargets) {
            if (!configuredTargets.contains(targetNodeId)) {
                throw new IllegalArgumentException("BPMN排他网关存在未配置的出线目标: " + targetNodeId);
            }
        }
        for (String targetNodeId : configuredTargets) {
            if (!bpmnTargets.contains(targetNodeId)) {
                throw new IllegalArgumentException("条件分支目标节点未连接到BPMN排他网关出线: " + targetNodeId);
            }
        }
    }

    private Set<String> parseGatewayBranchTargets(ProcessNodeConfig gatewayConfig) {
        try {
            JsonNode branches = OBJECT_MAPPER.readTree(gatewayConfig.getBranchJson()).get("branches");
            Set<String> targets = new LinkedHashSet<>();
            if (branches != null && branches.isArray()) {
                for (JsonNode branch : branches) {
                    String targetNodeId = branch.path("targetNodeId").asText(null);
                    if (StringUtils.hasText(targetNodeId)) {
                        targets.add(targetNodeId);
                    }
                }
            }
            return targets;
        } catch (Exception e) {
            throw new IllegalArgumentException("条件分支配置JSON不是合法JSON: " + gatewayConfig.getNodeName());
        }
    }

    /**
     * 使用 JDK XML 解析器提取指定 BPMN 元素，禁用外部实体以避免解析 XML 时产生外部资源访问。
     */
    private Set<String> parseBpmnElementIds(String bpmnXml, String elementName, String displayName) {
        Set<String> userTaskIds = new LinkedHashSet<>();
        try {
            Document document = parseBpmnDocument(bpmnXml);
            NodeList userTasks = document.getElementsByTagNameNS("*", elementName);
            for (int i = 0; i < userTasks.getLength(); i++) {
                Element element = (Element) userTasks.item(i);
                String userTaskId = element.getAttribute("id");
                if (!StringUtils.hasText(userTaskId)) {
                    throw new IllegalArgumentException(displayName + "节点ID不能为空");
                }
                if (!userTaskIds.add(userTaskId)) {
                    throw new IllegalArgumentException(displayName + "节点ID重复: " + userTaskId);
                }
            }
            return userTaskIds;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("BPMN XML解析失败");
        }
    }

    private Document parseBpmnDocument(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(bpmnXml)));
    }

    private Integer resolveNextVersion(String tenantId, String processKey) {
        if (!StringUtils.hasText(processKey)) {
            return 1;
        }
        QueryWrapper<ProcessModel> wrapper = new QueryWrapper<>();
        wrapper.select("max(version) as version")
                .eq("tenant_id", tenantId)
                .eq("process_key", processKey)
                .eq("delete_flag", 0);
        ProcessModel latest = baseMapper.selectOne(wrapper);
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }

    /**
     * 同一个流程编码只允许存在一个草稿，避免同时维护多份未发布设计导致发布和发起版本混乱。
     */
    private void ensureNoDraftVersion(String tenantId, String processKey) {
        if (!StringUtils.hasText(processKey)) {
            return;
        }
        Long draftCount = baseMapper.selectCount(new QueryWrapper<ProcessModel>()
                .eq("tenant_id", tenantId)
                .eq("process_key", processKey)
                .eq("status", WorkflowConstants.Status.DRAFT)
                .eq("delete_flag", 0));
        if (draftCount != null && draftCount > 0) {
            throw new IllegalArgumentException("该流程已存在草稿版本，请先发布或删除草稿后再新建版本");
        }
    }

    private void copyChildren(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        copyChildren(sourceModelId, targetModelId, tenantId, context, true);
    }

    private void copyChildren(String sourceModelId, String targetModelId, String tenantId,
            RequestContext context, boolean copyFieldPermissions) {
        copyNodeConfigs(sourceModelId, targetModelId, tenantId, context);
        if (copyFieldPermissions) {
            copyFieldPermissions(sourceModelId, targetModelId, tenantId, context);
        }
        copyStartPermissions(sourceModelId, targetModelId, tenantId, context);
    }

    private void copyNodeConfigs(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        QueryWrapper<ProcessNodeConfig> wrapper = activeChildrenWrapper(sourceModelId, tenantId);
        List<ProcessNodeConfig> configs = processNodeConfigMapper.selectList(wrapper);
        for (ProcessNodeConfig source : configs) {
            ProcessNodeConfig target = BeanUtil.copyProperties(source, ProcessNodeConfig.class);
            target.setId(null);
            target.setProcessModelId(targetModelId);
            EntityFillUtils.fillAuditFields(target, context, true);
            processNodeConfigMapper.insert(target);
        }
    }

    private void copyFieldPermissions(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        QueryWrapper<FieldPermission> wrapper = activeChildrenWrapper(sourceModelId, tenantId);
        List<FieldPermission> permissions = fieldPermissionMapper.selectList(wrapper);
        for (FieldPermission source : permissions) {
            FieldPermission target = BeanUtil.copyProperties(source, FieldPermission.class);
            target.setId(null);
            target.setProcessModelId(targetModelId);
            EntityFillUtils.fillAuditFields(target, context, true);
            fieldPermissionMapper.insert(target);
        }
    }

    private void copyStartPermissions(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        QueryWrapper<ProcessStartPermission> wrapper = activeChildrenWrapper(sourceModelId, tenantId);
        List<ProcessStartPermission> permissions = processStartPermissionMapper.selectList(wrapper);
        for (ProcessStartPermission source : permissions) {
            ProcessStartPermission target = BeanUtil.copyProperties(source, ProcessStartPermission.class);
            target.setId(null);
            target.setProcessModelId(targetModelId);
            EntityFillUtils.fillAuditFields(target, context, true);
            processStartPermissionMapper.insert(target);
        }
    }

    private <T> QueryWrapper<T> activeChildrenWrapper(String processModelId, String tenantId) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("delete_flag", 0);
        return wrapper;
    }

    private <T extends com.lawoffice.framework.entity.BaseEntity> void logicDeleteChildren(
            com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
            String tenantId,
            String processModelId,
            RequestContext context) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("delete_flag", 0);
        List<T> children = mapper.selectList(wrapper);
        for (T child : children) {
            EntityFillUtils.fillDeleteFields(child, context == null ? "system" : context.getUsername());
            mapper.updateById(child);
        }
    }

    private void applyTenantAndDefaultSort(BaseDTO<ProcessModel> baseDTO) {
        QueryWrapper<ProcessModel> wrapper = ensureQueryWrapper(baseDTO);
        wrapper.eq("tenant_id", resolveTenantId(null, baseDTO.getContext()))
                .orderByDesc("create_time");
    }

    private QueryWrapper<ProcessModel> ensureQueryWrapper(BaseDTO<ProcessModel> baseDTO) {
        QueryWrapper<ProcessModel> wrapper = (QueryWrapper<ProcessModel>) baseDTO.getQueryWrapper();
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
            baseDTO.setQueryWrapper(wrapper);
        }
        return wrapper;
    }
}
