package com.lawoffice.workflow.service.impl;

import com.lawoffice.workflow.mapper.ProcessModelMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BpmnSecurityServiceImplTest {

    private final BpmnSecurityServiceImpl service = new BpmnSecurityServiceImpl(mock(ProcessModelMapper.class));

    @Test
    void shouldAllowWhitelistedBpmnElements() {
        assertDoesNotThrow(() -> service.validateBpmnXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                    xmlns:di="http://www.omg.org/spec/DD/20100524/DI">
                    <process id="leave" isExecutable="true">
                        <startEvent id="start" />
                        <sequenceFlow id="flow1" sourceRef="start" targetRef="approve" />
                        <userTask id="approve" name="审批" />
                        <sequenceFlow id="flow2" sourceRef="approve" targetRef="end" />
                        <endEvent id="end" />
                    </process>
                    <bpmndi:BPMNDiagram id="diagram">
                        <bpmndi:BPMNPlane id="plane" bpmnElement="leave">
                            <bpmndi:BPMNShape id="shape_start" bpmnElement="start">
                                <dc:Bounds x="100" y="100" width="36" height="36" />
                            </bpmndi:BPMNShape>
                            <bpmndi:BPMNEdge id="edge_flow1" bpmnElement="flow1">
                                <di:waypoint x="136" y="118" />
                                <di:waypoint x="200" y="118" />
                            </bpmndi:BPMNEdge>
                        </bpmndi:BPMNPlane>
                    </bpmndi:BPMNDiagram>
                </definitions>
                """));
    }

    @Test
    void shouldRejectForbiddenScriptTask() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.validateBpmnXml("""
                        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                            <process id="leave">
                                <scriptTask id="script" />
                            </process>
                        </definitions>
                        """));

        assertEquals("BPMN暂不允许使用元素: scriptTask", exception.getMessage());
    }

    @Test
    void shouldRejectFlowableExtensionAttribute() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.validateBpmnXml("""
                        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                            xmlns:flowable="http://flowable.org/bpmn">
                            <process id="leave">
                                <userTask id="approve" flowable:class="com.example.Unsafe" />
                            </process>
                        </definitions>
                        """));

        assertEquals("BPMN暂不允许使用Flowable/Activiti扩展属性: class", exception.getMessage());
    }
}
