package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRuntimeLookupServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "user-1";

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private FormDefinitionMapper formDefinitionMapper;
    @Mock
    private ProcessStartPermissionMapper processStartPermissionMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private FieldPermissionMapper fieldPermissionMapper;
    @Mock
    private IUserService userService;

    private WorkflowRuntimeLookupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowRuntimeLookupServiceImpl(
                taskMapper,
                processModelMapper,
                formDefinitionMapper,
                processStartPermissionMapper,
                processInstanceMapper,
                formInstanceMapper,
                fieldPermissionMapper,
                userService
        );
    }

    @Test
    void shouldRejectEmptyTenantId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireTenantId(RequestContext.builder().userId(USER_ID).build())
        );

        assertEquals("租户ID不能为空", exception.getMessage());
    }

    @Test
    void shouldRejectEmptyUserId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireUserId(RequestContext.builder().tenantId(TENANT_ID).build())
        );

        assertEquals("当前用户ID不能为空", exception.getMessage());
    }

    @Test
    void shouldRequireTodoTaskWithTenantStatusAndDeleteFlag() {
        Task task = new Task();
        task.setId("task-1");
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        Task result = service.requireTodoTask("task-1", TENANT_ID);

        assertSame(task, result);
        QueryWrapper<Task> wrapper = captureTaskQueryWrapper();
        assertSqlContains(wrapper, "id", "tenant_id", "status", "delete_flag");
    }

    @Test
    void shouldRejectMissingTodoTask() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireTodoTask("task-1", TENANT_ID)
        );

        assertEquals("任务不存在或已处理", exception.getMessage());
    }

    @Test
    void shouldRejectEmptyTaskIdBeforeQuery() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireTodoTask("", TENANT_ID)
        );

        assertEquals("任务ID不能为空", exception.getMessage());
        verifyNoInteractions(taskMapper);
    }

    @Test
    void shouldRequireProcessInstanceAndFormInstance() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId("instance-1");
        FormInstance formInstance = new FormInstance();
        formInstance.setId("form-instance-1");
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(formInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(formInstance);

        assertSame(processInstance, service.requireProcessInstance("instance-1", TENANT_ID));
        assertSame(formInstance, service.requireFormInstance("form-instance-1", TENANT_ID));
    }

    @Test
    void shouldRejectMissingProcessInstanceAndFormInstance() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(formInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        IllegalArgumentException processException = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireProcessInstance("instance-1", TENANT_ID)
        );
        IllegalArgumentException formException = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireFormInstance("form-instance-1", TENANT_ID)
        );

        assertEquals("审批实例不存在", processException.getMessage());
        assertEquals("表单实例不存在", formException.getMessage());
    }

    @Test
    void shouldRequirePublishedModelAndRejectNewerPublishedVersion() {
        ProcessModel model = buildPublishedModel();
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(processModelMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertSame(model, service.requirePublishedModel("model-1", TENANT_ID));

        when(processModelMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requirePublishedModel("model-1", TENANT_ID)
        );

        assertEquals("流程已有新发布版本，请使用最新版本发起", exception.getMessage());
    }

    @Test
    void shouldRejectUnpublishedOrUndeployedModel() {
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        IllegalArgumentException missingException = assertThrows(
                IllegalArgumentException.class,
                () -> service.requirePublishedModel("model-1", TENANT_ID)
        );
        assertEquals("流程不存在或未发布", missingException.getMessage());

        ProcessModel model = buildPublishedModel();
        model.setFlowableProcessDefinitionId("");
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        IllegalArgumentException undeployedException = assertThrows(
                IllegalArgumentException.class,
                () -> service.requirePublishedModel("model-1", TENANT_ID)
        );
        assertEquals("流程未部署到Flowable，不能发起", undeployedException.getMessage());
    }

    @Test
    void shouldRequirePublishedForm() {
        FormDefinition form = new FormDefinition();
        form.setId("form-1");
        when(formDefinitionMapper.selectOne(any(Wrapper.class))).thenReturn(form);

        assertSame(form, service.requirePublishedForm("form-1", TENANT_ID));

        when(formDefinitionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.requirePublishedForm("form-1", TENANT_ID)
        );
        assertEquals("流程绑定的表单不存在或未发布", exception.getMessage());
    }

    @Test
    void shouldSkipStartPermissionWhenScopeIsAll() {
        ProcessModel model = buildPublishedModel();
        model.setStartScopeType(WorkflowConstants.StartScopeType.ALL);

        assertDoesNotThrow(() -> service.checkStartPermission(model, context()));

        verify(processStartPermissionMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void shouldAllowStartPermissionByUserTenantRoleAndDepart() {
        when(userService.getUserRoleIds(USER_ID)).thenReturn(List.of("role-1"));
        when(userService.getUserDeparts(USER_ID)).thenReturn(List.of(depart("depart-1")));

        assertDoesNotThrow(() -> checkStartPermission(permission(WorkflowConstants.TargetType.USER, USER_ID)));
        assertDoesNotThrow(() -> checkStartPermission(permission(WorkflowConstants.TargetType.TENANT, TENANT_ID)));
        assertDoesNotThrow(() -> checkStartPermission(permission(WorkflowConstants.TargetType.ROLE, "role-1")));
        assertDoesNotThrow(() -> checkStartPermission(permission(WorkflowConstants.TargetType.DEPART, "depart-1")));
    }

    @Test
    void shouldRejectWhenStartPermissionDoesNotMatchCurrentUser() {
        when(userService.getUserRoleIds(USER_ID)).thenReturn(List.of("role-other"));
        when(processStartPermissionMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(permission(WorkflowConstants.TargetType.ROLE, "role-1")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.checkStartPermission(buildSpecifiedModel(), context())
        );

        assertEquals("当前用户无权发起该流程", exception.getMessage());
    }

    private QueryWrapper<Task> captureTaskQueryWrapper() {
        ArgumentCaptor<QueryWrapper<Task>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(taskMapper).selectOne(captor.capture());
        return captor.getValue();
    }

    private void assertSqlContains(QueryWrapper<?> wrapper, String... snippets) {
        String sqlSegment = wrapper.getSqlSegment();
        for (String snippet : snippets) {
            org.junit.jupiter.api.Assertions.assertTrue(sqlSegment.contains(snippet), sqlSegment);
        }
    }

    private void checkStartPermission(ProcessStartPermission permission) {
        when(processStartPermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(permission));
        service.checkStartPermission(buildSpecifiedModel(), context());
    }

    private ProcessModel buildPublishedModel() {
        ProcessModel model = new ProcessModel();
        model.setId("model-1");
        model.setTenantId(TENANT_ID);
        model.setProcessKey("process-key");
        model.setVersion(1);
        model.setStatus(WorkflowConstants.Status.PUBLISHED);
        model.setStartScopeType(WorkflowConstants.StartScopeType.SPECIFIED);
        model.setFlowableProcessDefinitionId("flowable-definition-1");
        return model;
    }

    private ProcessModel buildSpecifiedModel() {
        ProcessModel model = buildPublishedModel();
        model.setStartScopeType(WorkflowConstants.StartScopeType.SPECIFIED);
        return model;
    }

    private ProcessStartPermission permission(String targetType, String targetId) {
        ProcessStartPermission permission = new ProcessStartPermission();
        permission.setTenantId(TENANT_ID);
        permission.setProcessModelId("model-1");
        permission.setTargetType(targetType);
        permission.setTargetId(targetId);
        permission.setStatus(WorkflowConstants.Status.ENABLED);
        return permission;
    }

    private SysDepart depart(String departId) {
        SysDepart depart = new SysDepart();
        depart.setId(departId);
        return depart;
    }

    private RequestContext context() {
        return RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .username("tester")
                .build();
    }
}
