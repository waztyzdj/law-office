package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IReminderRuntimeService;
import com.lawoffice.workflow.vo.ReminderRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ReminderRuntimeServiceImpl implements IReminderRuntimeService {

    private final TaskMapper taskMapper;
    private final ReminderRecordMapper reminderRecordMapper;

    public ReminderRuntimeServiceImpl(TaskMapper taskMapper, ReminderRecordMapper reminderRecordMapper) {
        this.taskMapper = taskMapper;
        this.reminderRecordMapper = reminderRecordMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ReminderRecordVO> urgeTask(String taskId, String remark, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            RuntimeSupport.requireUserId(context);
            Task task = requireTodoTask(taskId, tenantId);

            ReminderRecord record = new ReminderRecord();
            record.setTenantId(tenantId);
            record.setProcessInstanceId(task.getProcessInstanceId());
            record.setTaskId(task.getId());
            record.setFlowableTaskId(task.getFlowableTaskId());
            record.setRemindType(WorkflowConstants.RemindType.URGE);
            record.setSenderUserId(context.getUserId());
            record.setSenderUsername(context.getUsername());
            record.setReceiverUserId(task.getAssigneeUserId());
            record.setReceiverUsername(task.getAssigneeUsername());
            record.setReceiverRealname(task.getAssigneeRealname());
            record.setRemindRound(1);
            record.setOperateTime(LocalDateTime.now());
            record.setRemark(remark);
            EntityFillUtils.fillAuditFields(record, context, true);
            reminderRecordMapper.insert(record);
            return BaseResult.success(BeanUtil.toBean(record, ReminderRecordVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("催办任务失败: " + e.getMessage());
        }
    }

    private Task requireTodoTask(String taskId, String tenantId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        Task task = taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("id", taskId)
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0));
        if (task == null) {
            throw new IllegalArgumentException("待办任务不存在");
        }
        if (!StringUtils.hasText(task.getAssigneeUserId())) {
            throw new IllegalArgumentException("候选待办尚未认领，不能直接催办");
        }
        return task;
    }
}
