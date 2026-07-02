package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;
import com.lawoffice.workflow.entity.Attachment;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.service.IWorkflowDownloadService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class WorkflowDownloadServiceImpl implements IWorkflowDownloadService {

    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final int GRID_SPAN = 24;
    private static final Set<String> HIDDEN_RECORD_ACTIONS = Set.of(
            WorkflowConstants.Action.BRANCH_MATCH,
            WorkflowConstants.Action.SAVE_DRAFT,
            WorkflowConstants.Action.START,
            WorkflowConstants.Action.URGE
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    private final IRuntimeAccessService runtimeAccessService;
    private final OperationRecordMapper operationRecordMapper;
    private final AttachmentMapper attachmentMapper;
    private final ISysFilesService sysFilesService;

    public WorkflowDownloadServiceImpl(IWorkflowRuntimeLookupService workflowRuntimeLookupService,
            IRuntimeAccessService runtimeAccessService,
            OperationRecordMapper operationRecordMapper,
            AttachmentMapper attachmentMapper,
            ISysFilesService sysFilesService) {
        this.workflowRuntimeLookupService = workflowRuntimeLookupService;
        this.runtimeAccessService = runtimeAccessService;
        this.operationRecordMapper = operationRecordMapper;
        this.attachmentMapper = attachmentMapper;
        this.sysFilesService = sysFilesService;
    }

    @Override
    public WorkflowDownloadFile downloadPackage(String processInstanceId, RequestContext context) {
        DownloadContext downloadContext = loadRuntimeDownloadContext(processInstanceId, context);
        byte[] pdfContent = buildPdf(downloadContext);
        byte[] zipContent = buildPackage(downloadContext, pdfContent);
        return new WorkflowDownloadFile(buildBaseFileName(downloadContext.processInstance()) + ".zip",
                ZIP_CONTENT_TYPE, zipContent);
    }

    @Override
    public WorkflowDownloadFile downloadArchivePackage(String processInstanceId, RequestContext context) {
        DownloadContext downloadContext = loadArchiveDownloadContext(processInstanceId, context);
        byte[] pdfContent = buildPdf(downloadContext);
        byte[] zipContent = buildPackage(downloadContext, pdfContent);
        return new WorkflowDownloadFile(buildBaseFileName(downloadContext.processInstance()) + ".zip",
                ZIP_CONTENT_TYPE, zipContent);
    }

    /**
     * 下载材料属于正式留存件，只允许已通过结束的实例下载，并复用详情访问权。
     */
    private DownloadContext loadRuntimeDownloadContext(String processInstanceId, RequestContext context) {
        String tenantId = workflowRuntimeLookupService.requireTenantId(context);
        ProcessInstance processInstance = workflowRuntimeLookupService.requireProcessInstance(processInstanceId, tenantId);
        runtimeAccessService.ensureInstanceAccess(processInstance, context);
        if (!WorkflowConstants.Status.APPROVED.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException("只有审批通过并结束的流程可以下载");
        }
        return buildDownloadContext(processInstance, tenantId);
    }

    /**
     * 归档材料下载由归档 Controller 的查看权限和归档记录存在性兜底，这里只校验租户与终态，
     * 允许通过、不通过和已终止实例复用同一套 PDF/ZIP 生成逻辑。
     */
    private DownloadContext loadArchiveDownloadContext(String processInstanceId, RequestContext context) {
        String tenantId = workflowRuntimeLookupService.requireTenantId(context);
        ProcessInstance processInstance = workflowRuntimeLookupService.requireProcessInstance(processInstanceId, tenantId);
        if (!WorkflowConstants.Status.APPROVED.equals(processInstance.getStatus())
                && !WorkflowConstants.Status.REJECTED.equals(processInstance.getStatus())
                && !WorkflowConstants.Status.TERMINATED.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException("只有已结束流程可以下载归档材料");
        }
        return buildDownloadContext(processInstance, tenantId);
    }

    private DownloadContext buildDownloadContext(ProcessInstance processInstance, String tenantId) {
        FormInstance formInstance = workflowRuntimeLookupService.requireFormInstance(
                processInstance.getFormInstanceId(), tenantId);
        List<OperationRecord> records = listOperationRecords(processInstance.getId(), tenantId);
        List<AttachmentFile> attachments = listAttachmentFiles(processInstance.getId(), tenantId);
        return new DownloadContext(processInstance, formInstance, records, attachments);
    }

    private byte[] buildPdf(DownloadContext downloadContext) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(document);
            PdfCanvas canvas = new PdfCanvas(document, font);
            canvas.drawTitle(downloadContext.formInstance().getFormName());
            canvas.drawFormTable(buildPrintRows(downloadContext.formInstance()));
            canvas.drawApprovalRecords(filterVisibleRecords(downloadContext.records()));
            canvas.drawAttachments(downloadContext.attachments());
            canvas.close();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成审批PDF失败", e);
        }
    }

    private byte[] buildPackage(DownloadContext downloadContext, byte[] pdfContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            addZipEntry(zipOutputStream, buildBaseFileName(downloadContext.processInstance()) + ".pdf", pdfContent);
            Set<String> usedNames = new HashSet<>();
            for (AttachmentFile attachment : downloadContext.attachments()) {
                addAttachmentEntry(zipOutputStream, attachment, usedNames);
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成审批下载包失败", e);
        }
    }

    private List<OperationRecord> listOperationRecords(String processInstanceId, String tenantId) {
        return operationRecordMapper.selectList(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByAsc("operate_time")
                .orderByAsc("create_time"));
    }

    private List<AttachmentFile> listAttachmentFiles(String processInstanceId, String tenantId) {
        return attachmentMapper.selectList(new QueryWrapper<Attachment>()
                        .eq("tenant_id", tenantId)
                        .eq("process_instance_id", processInstanceId)
                        .eq("status", WorkflowConstants.AttachmentStatus.ACTIVE)
                        .eq("delete_flag", 0)
                        .orderByAsc("sort_order")
                        .orderByAsc("create_time"))
                .stream()
                .map(this::buildAttachmentFile)
                .toList();
    }

    private AttachmentFile buildAttachmentFile(Attachment attachment) {
        FileUploadVO file = sysFilesService.getFileById(attachment.getFileId());
        String fileName = StringUtils.hasText(file.getFileName()) ? file.getFileName() : attachment.getFileId();
        return new AttachmentFile(attachment.getFileId(), fileName);
    }

    private List<OperationRecord> filterVisibleRecords(List<OperationRecord> records) {
        return records.stream()
                .filter(record -> !HIDDEN_RECORD_ACTIONS.contains(String.valueOf(record.getAction())))
                .toList();
    }

    private List<PrintRow> buildPrintRows(FormInstance formInstance) {
        List<JsonNode> rules = parseArray(formInstance.getFormSchemaSnapshotJson());
        JsonNode formData = parseObject(formInstance.getFormDataJson());
        return splitRowsByGridSpan(buildRowsFromRules(rules, formData));
    }

    private List<PrintRow> buildRowsFromRules(List<JsonNode> rules, JsonNode formData) {
        List<PrintRow> rows = new ArrayList<>();
        List<PrintField> pendingFields = new ArrayList<>();
        int[] pendingSpan = {0};
        for (JsonNode rule : rules) {
            if (!isVisibleRule(rule)) {
                continue;
            }
            if (isRowRule(rule)) {
                flushPendingFields(rows, pendingFields, pendingSpan);
                PrintRow row = buildRowFromContainer(rule, formData, rows.size());
                if (!row.fields().isEmpty()) {
                    rows.add(row);
                }
                continue;
            }
            if (isColRule(rule)) {
                flushPendingFields(rows, pendingFields, pendingSpan);
                List<PrintField> fields = collectPrintableFields(children(rule), formData, resolveRuleSpan(rule, GRID_SPAN));
                if (!fields.isEmpty()) {
                    rows.add(new PrintRow(buildRowKey(rows.size(), fields), fields));
                }
                continue;
            }
            if (hasText(rule, "field")) {
                appendField(rows, pendingFields, pendingSpan, toPrintField(rule, formData, resolveRuleSpan(rule, GRID_SPAN)));
                continue;
            }
            if (!children(rule).isEmpty()) {
                flushPendingFields(rows, pendingFields, pendingSpan);
                rows.addAll(buildRowsFromRules(children(rule), formData));
            }
        }
        flushPendingFields(rows, pendingFields, pendingSpan);
        return rows;
    }

    private PrintRow buildRowFromContainer(JsonNode rowRule, JsonNode formData, int rowIndex) {
        List<PrintField> fields = new ArrayList<>();
        for (JsonNode child : children(rowRule)) {
            if (!isVisibleRule(child)) {
                continue;
            }
            if (isColRule(child)) {
                int colSpan = resolveRuleSpan(child, GRID_SPAN);
                fields.addAll(collectPrintableFields(children(child), formData, colSpan));
                continue;
            }
            if (hasText(child, "field")) {
                PrintField field = toPrintField(child, formData, resolveRuleSpan(child, GRID_SPAN));
                if (field != null) {
                    fields.add(field);
                }
                continue;
            }
            fields.addAll(collectPrintableFields(children(child), formData, GRID_SPAN));
        }
        return new PrintRow(text(rowRule, "field", "row-" + rowIndex), fields);
    }

    private List<PrintField> collectPrintableFields(List<JsonNode> rules, JsonNode formData, int span) {
        List<PrintField> result = new ArrayList<>();
        for (JsonNode rule : rules) {
            if (!isVisibleRule(rule)) {
                continue;
            }
            if (hasText(rule, "field")) {
                PrintField field = toPrintField(rule, formData, resolveRuleSpan(rule, span));
                if (field != null) {
                    result.add(field);
                }
            }
            if (!children(rule).isEmpty()) {
                result.addAll(collectPrintableFields(children(rule), formData, span));
            }
        }
        return result;
    }

    private void appendField(List<PrintRow> rows, List<PrintField> pendingFields, int[] pendingSpan, PrintField field) {
        if (field == null) {
            return;
        }
        if (field.span() >= GRID_SPAN) {
            flushPendingFields(rows, pendingFields, pendingSpan);
            rows.add(new PrintRow(field.key(), List.of(field)));
            return;
        }
        if (pendingSpan[0] + field.span() > GRID_SPAN) {
            flushPendingFields(rows, pendingFields, pendingSpan);
        }
        pendingFields.add(field);
        pendingSpan[0] += field.span();
        if (pendingSpan[0] >= GRID_SPAN) {
            flushPendingFields(rows, pendingFields, pendingSpan);
        }
    }

    private void flushPendingFields(List<PrintRow> rows, List<PrintField> pendingFields, int[] pendingSpan) {
        if (pendingFields.isEmpty()) {
            return;
        }
        rows.add(new PrintRow(buildRowKey(rows.size(), pendingFields), List.copyOf(pendingFields)));
        pendingFields.clear();
        pendingSpan[0] = 0;
    }

    private List<PrintRow> splitRowsByGridSpan(List<PrintRow> rows) {
        List<PrintRow> result = new ArrayList<>();
        for (PrintRow row : rows) {
            List<PrintField> currentFields = new ArrayList<>();
            int currentSpan = 0;
            for (PrintField field : row.fields()) {
                int span = normalizeSpan(field.span());
                if (currentSpan > 0 && currentSpan + span > GRID_SPAN) {
                    result.add(new PrintRow(row.key() + "-" + result.size(), List.copyOf(currentFields)));
                    currentFields.clear();
                    currentSpan = 0;
                }
                currentFields.add(new PrintField(field.key(), field.label(), field.value(), span));
                currentSpan += span;
                if (currentSpan >= GRID_SPAN) {
                    result.add(new PrintRow(row.key() + "-" + result.size(), List.copyOf(currentFields)));
                    currentFields.clear();
                    currentSpan = 0;
                }
            }
            if (!currentFields.isEmpty()) {
                result.add(new PrintRow(row.key() + "-" + result.size(), List.copyOf(currentFields)));
            }
        }
        return result;
    }

    private PrintField toPrintField(JsonNode rule, JsonNode formData, int span) {
        String field = text(rule, "field", "");
        if (!StringUtils.hasText(field)) {
            return null;
        }
        JsonNode value = formData == null ? null : formData.get(field);
        int fieldSpan = isFullRowRule(rule, value) ? GRID_SPAN : span;
        return new PrintField(field, resolveLabel(rule, field), formatFieldValue(value, rule), fieldSpan);
    }

    private boolean isFullRowRule(JsonNode rule, JsonNode value) {
        String type = text(rule, "type", "").toLowerCase();
        if (isRangeValue(value)) {
            return false;
        }
        return type.contains("textarea")
                || type.contains("editor")
                || type.contains("upload")
                || type.contains("table")
                || (value != null && (value.isArray() || value.isObject()));
    }

    private String formatFieldValue(JsonNode value, JsonNode rule) {
        if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
            return "-";
        }
        String optionLabel = resolveOptionLabel(value, rule);
        if (StringUtils.hasText(optionLabel)) {
            return optionLabel;
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(formatLooseValue(item)));
            if (values.isEmpty()) {
                return "-";
            }
            return String.join(isRangeValue(value) ? " ～ " : "；", values);
        }
        return formatLooseValue(value);
    }

    private String formatLooseValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "-";
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "是" : "否";
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        return value.toString();
    }

    private String resolveOptionLabel(JsonNode value, JsonNode rule) {
        List<OptionItem> options = collectOptions(rule);
        if (options.isEmpty()) {
            return "";
        }
        List<JsonNode> values = value.isArray() ? toNodeList(value) : List.of(value);
        List<String> labels = new ArrayList<>();
        for (JsonNode item : values) {
            String rawValue = formatLooseValue(item);
            labels.add(options.stream()
                    .filter(option -> option.value().equals(rawValue))
                    .map(OptionItem::label)
                    .findFirst()
                    .orElse(rawValue));
        }
        return String.join("、", labels);
    }

    private List<OptionItem> collectOptions(JsonNode rule) {
        List<OptionItem> result = new ArrayList<>();
        collectOptionsFromNode(rule.get("options"), result);
        JsonNode props = rule.get("props");
        if (props != null) {
            collectOptionsFromNode(props.get("options"), result);
        }
        return result;
    }

    private void collectOptionsFromNode(JsonNode optionsNode, List<OptionItem> result) {
        if (optionsNode == null || !optionsNode.isArray()) {
            return;
        }
        for (JsonNode option : optionsNode) {
            String value = firstText(option, "value", "id", "key");
            String label = firstText(option, "label", "name", "title", "value");
            if (StringUtils.hasText(value) && StringUtils.hasText(label)) {
                result.add(new OptionItem(value, label));
            }
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        String configuredPath = System.getProperty("workflow.download.font-path");
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(configuredPath)) {
            candidates.add(configuredPath);
        }
        candidates.addAll(List.of(
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simkai.ttf",
                "C:/Windows/Fonts/simfang.ttf",
                "/Library/Fonts/Arial Unicode.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf"
        ));
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path) && candidate.toLowerCase().matches(".*\\.(ttf|otf)$")) {
                return PDType0Font.load(document, path.toFile());
            }
        }
        throw new IllegalStateException("未找到可用中文字体，请通过 workflow.download.font-path 指定 TTF/OTF 字体文件");
    }

    private void addZipEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(safeZipEntryName(name)));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private void addAttachmentEntry(ZipOutputStream zipOutputStream, AttachmentFile attachment,
            Set<String> usedNames) throws IOException {
        String entryName = uniqueZipEntryName("附件/" + attachment.fileName(), usedNames);
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        try (InputStream inputStream = sysFilesService.downloadFileContent(attachment.fileId())) {
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
        return name == null ? "未命名" : name.replace("\\", "/")
                .replace("../", "")
                .replace("\r", "_")
                .replace("\n", "_");
    }

    private String buildBaseFileName(ProcessInstance processInstance) {
        String title = StringUtils.hasText(processInstance.getInstanceTitle())
                ? processInstance.getInstanceTitle()
                : "审批单";
        String fileName = StringUtils.hasText(processInstance.getInstanceNo())
                ? title + "-" + processInstance.getInstanceNo()
                : title;
        return fileName.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
    }

    private String resolveLabel(JsonNode rule, String field) {
        if (hasText(rule, "title")) {
            return rule.get("title").asText();
        }
        JsonNode props = rule.get("props");
        if (props != null && hasText(props, "label")) {
            return props.get("label").asText();
        }
        return field;
    }

    private String formatAction(OperationRecord record) {
        return switch (String.valueOf(record.getAction())) {
            case WorkflowConstants.Action.APPROVE -> "同意";
            case WorkflowConstants.Action.REJECT -> "不通过";
            case WorkflowConstants.Action.RETURN -> "退回";
            case WorkflowConstants.Action.TRANSFER -> "转办";
            case WorkflowConstants.Action.ADD_SIGN -> "加签";
            case WorkflowConstants.Action.WITHDRAW -> "撤回";
            case WorkflowConstants.Action.CC -> "抄送";
            case WorkflowConstants.Action.TIMEOUT_REMIND -> "超时提醒";
            case WorkflowConstants.Action.TASK_CANCEL -> "任务取消";
            case WorkflowConstants.Action.SYSTEM_COMPLETE -> "系统完成";
            default -> StringUtils.hasText(record.getAction()) ? record.getAction() : "-";
        };
    }

    private String formatApprovalNode(OperationRecord record) {
        if (StringUtils.hasText(record.getNodeName())) {
            return record.getNodeName();
        }
        if ("-".equals(record.getNodeId())) {
            return formatAction(record);
        }
        return StringUtils.hasText(record.getNodeId()) ? record.getNodeId() : "-";
    }

    private String formatApprovalText(OperationRecord record) {
        return StringUtils.hasText(record.getComment()) ? record.getComment() : formatAction(record);
    }

    private String formatApprovalSignature(OperationRecord record) {
        String operator = StringUtils.hasText(record.getOperatorRealname())
                ? record.getOperatorRealname()
                : record.getOperatorUsername();
        String time = record.getOperateTime() == null ? "" : DATE_TIME_FORMATTER.format(record.getOperateTime());
        return List.of(operator == null ? "" : operator, time).stream()
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "  " + right)
                .orElse("");
    }

    private List<JsonNode> parseArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return node != null && node.isArray() ? toNodeList(node) : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    private JsonNode parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return node != null && node.isObject() ? node : OBJECT_MAPPER.createObjectNode();
        } catch (IOException e) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private List<JsonNode> children(JsonNode node) {
        JsonNode children = node == null ? null : node.get("children");
        return children != null && children.isArray() ? toNodeList(children) : List.of();
    }

    private List<JsonNode> toNodeList(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(result::add);
        }
        return result;
    }

    private boolean isVisibleRule(JsonNode rule) {
        return rule != null
                && !booleanValue(rule, "hidden", false)
                && !Boolean.FALSE.equals(booleanObject(rule, "display"));
    }

    private boolean isRowRule(JsonNode rule) {
        String type = text(rule, "type", "").toLowerCase();
        return "fcrow".equals(type) || "row".equals(type);
    }

    private boolean isColRule(JsonNode rule) {
        return "col".equalsIgnoreCase(text(rule, "type", ""));
    }

    private int resolveRuleSpan(JsonNode rule, int fallback) {
        JsonNode colSpan = rule.path("col").get("span");
        if (colSpan != null && colSpan.isNumber()) {
            return normalizeSpan(colSpan.asInt());
        }
        JsonNode propsSpan = rule.path("props").get("span");
        if (propsSpan != null && propsSpan.isNumber()) {
            return normalizeSpan(propsSpan.asInt());
        }
        return normalizeSpan(fallback);
    }

    private int normalizeSpan(int span) {
        return Math.min(GRID_SPAN, Math.max(1, span));
    }

    private boolean isRangeValue(JsonNode value) {
        if (value == null || !value.isArray() || value.size() != 2) {
            return false;
        }
        Iterator<JsonNode> iterator = value.elements();
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            if (!item.isTextual() || !item.asText().trim()
                    .matches("\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2}(?::\\d{2})?)?")) {
                return false;
            }
        }
        return true;
    }

    private String buildRowKey(int index, List<PrintField> fields) {
        return "row-" + index + "-" + fields.stream().map(PrintField::key).reduce("", String::concat);
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            if (hasText(node, fieldName)) {
                return node.get(fieldName).asText();
            }
        }
        return "";
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        return hasText(node, fieldName) ? node.get(fieldName).asText() : fallback;
    }

    private boolean hasText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isTextual() && StringUtils.hasText(value.asText());
    }

    private boolean booleanValue(JsonNode node, String fieldName, boolean fallback) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private Boolean booleanObject(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private record DownloadContext(ProcessInstance processInstance, FormInstance formInstance,
            List<OperationRecord> records, List<AttachmentFile> attachments) {
    }

    private record PrintField(String key, String label, String value, int span) {
    }

    private record PrintRow(String key, List<PrintField> fields) {
    }

    private record AttachmentFile(String fileId, String fileName) {
    }

    private record OptionItem(String value, String label) {
    }

    private class PdfCanvas implements AutoCloseable {
        private static final float PAGE_MARGIN = 54F;
        private static final float CELL_PADDING_X = 8F;
        private static final float CELL_PADDING_Y = 7F;
        private static final float BODY_FONT_SIZE = 9F;
        private static final float TITLE_FONT_SIZE = 17F;
        private static final float LINE_HEIGHT = 13F;
        private static final float MIN_ROW_HEIGHT = 30F;
        private static final int TABLE_GRID_COLS = 48;

        private final PDDocument document;
        private final PDFont font;
        private final float pageWidth = PDRectangle.A4.getWidth();
        private final float pageHeight = PDRectangle.A4.getHeight();
        private final float contentWidth = pageWidth - PAGE_MARGIN * 2;
        private PDPageContentStream contentStream;
        private float y;

        PdfCanvas(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        void drawTitle(String title) throws IOException {
            String resolvedTitle = StringUtils.hasText(title) ? title : "审批单";
            ensureSpace(48F);
            float titleWidth = textWidth(resolvedTitle, TITLE_FONT_SIZE);
            drawText(resolvedTitle, (pageWidth - titleWidth) / 2, y - 15F, TITLE_FONT_SIZE);
            y -= 32F;
        }

        void drawFormTable(List<PrintRow> rows) throws IOException {
            if (rows.isEmpty()) {
                drawEmpty("暂无表单内容");
                return;
            }
            for (PrintRow row : rows) {
                drawFormRow(row);
            }
        }

        void drawApprovalRecords(List<OperationRecord> records) throws IOException {
            if (records.isEmpty()) {
                drawEmpty("暂无审批记录");
                return;
            }
            for (OperationRecord record : records) {
                drawApprovalRecord(record);
            }
        }

        void drawAttachments(List<AttachmentFile> attachments) throws IOException {
            y -= 10F;
            drawText("附件清单：", PAGE_MARGIN, y, BODY_FONT_SIZE);
            y -= LINE_HEIGHT;
            if (attachments.isEmpty()) {
                drawText("无", PAGE_MARGIN + 24F, y, BODY_FONT_SIZE);
                y -= LINE_HEIGHT;
                return;
            }
            for (AttachmentFile attachment : attachments) {
                List<String> lines = wrapText(attachment.fileName(), contentWidth - 24F, BODY_FONT_SIZE);
                ensureSpace(lines.size() * LINE_HEIGHT + 4F);
                for (String line : lines) {
                    drawText(line, PAGE_MARGIN + 24F, y, BODY_FONT_SIZE);
                    y -= LINE_HEIGHT;
                }
            }
        }

        private void drawFormRow(PrintRow row) throws IOException {
            List<Cell> cells = buildCells(row);
            float rowHeight = resolveRowHeight(cells);
            ensureSpace(rowHeight);
            float x = PAGE_MARGIN;
            for (Cell cell : cells) {
                drawCell(cell, x, y, rowHeight);
                x += cell.width();
            }
            y -= rowHeight;
        }

        private List<Cell> buildCells(PrintRow row) {
            List<Cell> cells = new ArrayList<>();
            float usedWidth = 0F;
            for (PrintField field : row.fields()) {
                int fieldSpan = normalizeSpan(field.span());
                float fieldWidth = contentWidth * fieldSpan / GRID_SPAN;
                float labelWidth = resolveLabelWidth(fieldSpan);
                float valueWidth = fieldWidth - labelWidth;
                cells.add(new Cell(field.label(), labelWidth, true));
                cells.add(new Cell(field.value(), valueWidth, false));
                usedWidth += fieldWidth;
            }
            if (contentWidth - usedWidth > 0.5F) {
                cells.add(new Cell("", contentWidth - usedWidth, false));
            }
            return cells;
        }

        private float resolveLabelWidth(int fieldSpan) {
            int fieldGridCols = fieldSpan * 2;
            int labelGridCols = fieldGridCols <= 2
                    ? 1
                    : Math.min(8, Math.max(2, fieldGridCols / 3));
            return contentWidth * labelGridCols / TABLE_GRID_COLS;
        }

        private float resolveRowHeight(List<Cell> cells) throws IOException {
            int maxLines = 1;
            for (Cell cell : cells) {
                int lineCount = wrapText(cell.text(), cell.width() - CELL_PADDING_X * 2, BODY_FONT_SIZE).size();
                maxLines = Math.max(maxLines, lineCount);
            }
            return Math.max(MIN_ROW_HEIGHT, maxLines * LINE_HEIGHT + CELL_PADDING_Y * 2);
        }

        private void drawCell(Cell cell, float x, float topY, float height) throws IOException {
            if (cell.label()) {
                contentStream.setNonStrokingColor(new Color(250, 250, 250));
                contentStream.addRect(x, topY - height, cell.width(), height);
                contentStream.fill();
            }
            contentStream.setStrokingColor(new Color(115, 115, 115));
            contentStream.addRect(x, topY - height, cell.width(), height);
            contentStream.stroke();
            contentStream.setNonStrokingColor(Color.BLACK);
            List<String> lines = wrapText(cell.text(), cell.width() - CELL_PADDING_X * 2, BODY_FONT_SIZE);
            float textBlockHeight = lines.size() * LINE_HEIGHT;
            float textY = topY - (height - textBlockHeight) / 2F - BODY_FONT_SIZE;
            for (String line : lines) {
                float textX = cell.label()
                        ? x + cell.width() - CELL_PADDING_X - textWidth(line, BODY_FONT_SIZE)
                        : x + CELL_PADDING_X;
                drawText(line, textX, textY, BODY_FONT_SIZE);
                textY -= LINE_HEIGHT;
            }
        }

        private void drawApprovalRecord(OperationRecord record) throws IOException {
            String nodeText = formatApprovalNode(record) + "：";
            List<String> commentLines = wrapText(formatApprovalText(record), contentWidth - 56F, BODY_FONT_SIZE);
            String signature = formatApprovalSignature(record);
            float height = Math.max(86F, 48F + commentLines.size() * LINE_HEIGHT);
            ensureSpace(height);
            contentStream.setStrokingColor(new Color(115, 115, 115));
            contentStream.addRect(PAGE_MARGIN, y - height, contentWidth, height);
            contentStream.stroke();
            drawText(nodeText, PAGE_MARGIN + 12F, y - 18F, BODY_FONT_SIZE);
            float textY = y - 42F;
            for (String line : commentLines) {
                drawText(line, PAGE_MARGIN + 28F, textY, BODY_FONT_SIZE);
                textY -= LINE_HEIGHT;
            }
            if (StringUtils.hasText(signature)) {
                drawText(signature, PAGE_MARGIN + contentWidth - 12F - textWidth(signature, BODY_FONT_SIZE),
                        y - height + 18F, BODY_FONT_SIZE);
            }
            y -= height;
        }

        private void drawEmpty(String text) throws IOException {
            ensureSpace(36F);
            contentStream.setStrokingColor(new Color(115, 115, 115));
            contentStream.addRect(PAGE_MARGIN, y - 32F, contentWidth, 32F);
            contentStream.stroke();
            drawText(text, PAGE_MARGIN + 12F, y - 20F, BODY_FONT_SIZE);
            y -= 32F;
        }

        private void ensureSpace(float height) throws IOException {
            if (y - height < PAGE_MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            y = pageHeight - PAGE_MARGIN;
        }

        private List<String> wrapText(String text, float maxWidth, float fontSize) throws IOException {
            String resolvedText = StringUtils.hasText(text) ? text : "-";
            List<String> lines = new ArrayList<>();
            for (String paragraph : resolvedText.split("\\R", -1)) {
                if (paragraph.isEmpty()) {
                    lines.add("");
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (int offset = 0; offset < paragraph.length();) {
                    int codePoint = paragraph.codePointAt(offset);
                    String token = new String(Character.toChars(codePoint));
                    if (!current.isEmpty() && textWidth(current + token, fontSize) > maxWidth) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(token);
                    offset += Character.charCount(codePoint);
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private void drawText(String text, float x, float baselineY, float fontSize) throws IOException {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(x, baselineY);
            contentStream.showText(StringUtils.hasText(text) ? text : "-");
            contentStream.endText();
        }

        private float textWidth(String text, float fontSize) throws IOException {
            if (!StringUtils.hasText(text)) {
                return 0F;
            }
            return font.getStringWidth(text) / 1000F * fontSize;
        }

        @Override
        public void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }

        private record Cell(String text, float width, boolean label) {
        }
    }
}
