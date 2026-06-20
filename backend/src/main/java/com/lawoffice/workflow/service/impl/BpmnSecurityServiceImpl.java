package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IBpmnSecurityService;
import com.lawoffice.workflow.vo.ProcessModelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BpmnSecurityServiceImpl implements IBpmnSecurityService {

    private static final int MAX_BPMN_XML_LENGTH = 200_000;
    private static final Set<String> ALLOWED_ELEMENT_NAMES = Set.of(
            "definitions",
            "process",
            "startEvent",
            "endEvent",
            "userTask",
            "sequenceFlow",
            "conditionExpression",
            "exclusiveGateway",
            "parallelGateway",
            "incoming",
            "outgoing",
            "BPMNDiagram",
            "BPMNPlane",
            "BPMNShape",
            "BPMNEdge",
            "BPMNLabel",
            "Bounds",
            "waypoint"
    );
    private static final Set<String> FORBIDDEN_ELEMENT_NAMES = Set.of(
            "scriptTask",
            "serviceTask",
            "sendTask",
            "receiveTask",
            "businessRuleTask",
            "callActivity",
            "subProcess",
            "intermediateCatchEvent",
            "intermediateThrowEvent",
            "boundaryEvent",
            "eventBasedGateway",
            "complexGateway",
            "extensionElements",
            "executionListener",
            "taskListener"
    );
    private static final Set<String> FORBIDDEN_ATTRIBUTE_NAMES = Set.of(
            "class",
            "delegateExpression",
            "expression",
            "resultVariable",
            "scriptFormat"
    );
    private static final Set<String> FORBIDDEN_ATTRIBUTE_NAMESPACES = Set.of(
            "http://flowable.org/bpmn",
            "http://activiti.org/bpmn"
    );
    private static final Pattern SAFE_BRANCH_EXPRESSION = Pattern.compile(
            "^\\$\\{branch == '[A-Za-z0-9_-]+'}$"
    );

    private final ProcessModelMapper processModelMapper;

    public BpmnSecurityServiceImpl(ProcessModelMapper processModelMapper) {
        this.processModelMapper = processModelMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> validateModel(String processModelId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processModelId)) {
                throw new IllegalArgumentException("流程模型ID不能为空");
            }
            ProcessModel model = processModelMapper.selectOne(new QueryWrapper<ProcessModel>()
                    .eq("id", processModelId)
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0));
            if (model == null) {
                throw new IllegalArgumentException("流程模型不存在");
            }
            String message = validateBpmnXml(model.getBpmnXml());
            model.setBpmnSecurityStatus(WorkflowConstants.BpmnSecurityStatus.PASSED);
            model.setBpmnSecurityMessage(message);
            EntityFillUtils.fillAuditFields(model, context, false);
            processModelMapper.updateById(model);
            return BaseResult.success(BeanUtil.toBean(model, ProcessModelVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("BPMN安全校验失败: " + e.getMessage());
        }
    }

    @Override
    public String validateBpmnXml(String bpmnXml) {
        if (!StringUtils.hasText(bpmnXml)) {
            throw new IllegalArgumentException("BPMN XML不能为空");
        }
        if (bpmnXml.length() > MAX_BPMN_XML_LENGTH) {
            throw new IllegalArgumentException("BPMN XML超过大小限制");
        }
        Document document = parseSecurely(bpmnXml);
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            validateElement(element);
            validateAttributes(element);
        }
        return "BPMN安全校验通过";
    }

    /**
     * 使用 JDK XML 安全解析能力，禁止外部实体和 DTD，避免解析阶段访问外部资源。
     */
    private Document parseSecurely(String bpmnXml) {
        try {
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
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("BPMN XML解析失败");
        }
    }

    private void validateElement(Element element) {
        String elementName = element.getLocalName();
        if (!StringUtils.hasText(elementName)) {
            elementName = element.getNodeName();
        }
        if (FORBIDDEN_ELEMENT_NAMES.contains(elementName)) {
            throw new IllegalArgumentException("BPMN暂不允许使用元素: " + elementName);
        }
        if (!ALLOWED_ELEMENT_NAMES.contains(elementName)) {
            throw new IllegalArgumentException("BPMN元素不在二期白名单内: " + elementName);
        }
        if ("conditionExpression".equals(elementName)) {
            validateConditionExpression(element);
        }
    }

    /**
     * 条件分支只允许使用系统约定的分支变量表达式，避免用户注入任意 JUEL/脚本逻辑。
     */
    private void validateConditionExpression(Element element) {
        String expression = element.getTextContent();
        if (!StringUtils.hasText(expression) || !SAFE_BRANCH_EXPRESSION.matcher(expression.trim()).matches()) {
            throw new IllegalArgumentException("BPMN条件表达式只能使用系统分支变量格式");
        }
    }

    private void validateAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String namespaceUri = attribute.getNamespaceURI();
            String attributeName = attribute.getLocalName();
            if (!StringUtils.hasText(attributeName)) {
                attributeName = attribute.getNodeName();
            }
            if (StringUtils.hasText(namespaceUri) && FORBIDDEN_ATTRIBUTE_NAMESPACES.contains(namespaceUri)) {
                throw new IllegalArgumentException("BPMN暂不允许使用Flowable/Activiti扩展属性: " + attributeName);
            }
            if (FORBIDDEN_ATTRIBUTE_NAMES.contains(attributeName)) {
                throw new IllegalArgumentException("BPMN暂不允许使用执行型属性: " + attributeName);
            }
        }
    }
}
