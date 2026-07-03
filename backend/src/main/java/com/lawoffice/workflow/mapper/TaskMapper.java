package com.lawoffice.workflow.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.req.TaskPageReq;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskMapper extends BaseMapper<Task> {

    @Select("""
            <script>
            select t.*
            from wf_task t
            where t.tenant_id = #{tenantId}
              and t.delete_flag = 0
              <choose>
                <when test="req != null and req.status != null and req.status != ''">
                  and t.status = #{req.status}
                </when>
                <otherwise>
                  and t.status in
                  <foreach collection="doneStatuses" item="status" open="(" separator="," close=")">
                    #{status}
                  </foreach>
                </otherwise>
              </choose>
              <if test="req != null and req.processInstanceId != null and req.processInstanceId != ''">
                and t.process_instance_id = #{req.processInstanceId}
              </if>
              <if test="req != null and req.taskType != null and req.taskType != ''">
                and t.task_type = #{req.taskType}
              </if>
              <if test="req != null and req.approvalMode != null and req.approvalMode != ''">
                and t.approval_mode = #{req.approvalMode}
              </if>
              <if test="req != null and req.taskName != null and req.taskName != ''">
                and t.task_name like concat('%', #{req.taskName}, '%')
              </if>
              <if test="req != null and req.assigneeRealname != null and req.assigneeRealname != ''">
                and t.assignee_realname like concat('%', #{req.assigneeRealname}, '%')
              </if>
              <if test="completeTimeGe != null">
                and t.complete_time &gt;= #{completeTimeGe}
              </if>
              <if test="completeTimeLe != null">
                and t.complete_time &lt;= #{completeTimeLe}
              </if>
              <if test="hasInstanceFilters">
                and exists (
                  select 1
                  from wf_process_instance pi
                  where pi.id = t.process_instance_id
                    and pi.tenant_id = t.tenant_id
                    and pi.delete_flag = 0
                    <if test="req != null and req.instanceTitle != null and req.instanceTitle != ''">
                      and pi.instance_title like concat('%', #{req.instanceTitle}, '%')
                    </if>
                    <if test="req != null and req.instanceNo != null and req.instanceNo != ''">
                      and pi.instance_no like concat('%', #{req.instanceNo}, '%')
                    </if>
                    <if test="req != null and req.starterRealname != null and req.starterRealname != ''">
                      and pi.starter_realname like concat('%', #{req.starterRealname}, '%')
                    </if>
                    <if test="startTimeGe != null">
                      and pi.start_time &gt;= #{startTimeGe}
                    </if>
                    <if test="startTimeLe != null">
                      and pi.start_time &lt;= #{startTimeLe}
                    </if>
                )
              </if>
              and (
                t.assignee_user_id = #{userId}
                or exists (
                  select 1
                  from wf_task_candidate tc
                  where tc.tenant_id = t.tenant_id
                    and tc.task_id = t.id
                    and tc.candidate_user_id = #{userId}
                    and tc.status = #{candidateStatus}
                    and tc.delete_flag = 0
                )
              )
              and not exists (
                select 1
                from wf_task newer
                where newer.tenant_id = t.tenant_id
                  and newer.process_instance_id = t.process_instance_id
                  and newer.delete_flag = 0
                  <choose>
                    <when test="req != null and req.status != null and req.status != ''">
                      and newer.status = #{req.status}
                    </when>
                    <otherwise>
                      and newer.status in
                      <foreach collection="doneStatuses" item="status" open="(" separator="," close=")">
                        #{status}
                      </foreach>
                    </otherwise>
                  </choose>
                  <if test="req != null and req.taskType != null and req.taskType != ''">
                    and newer.task_type = #{req.taskType}
                  </if>
                  <if test="req != null and req.approvalMode != null and req.approvalMode != ''">
                    and newer.approval_mode = #{req.approvalMode}
                  </if>
                  <if test="req != null and req.taskName != null and req.taskName != ''">
                    and newer.task_name like concat('%', #{req.taskName}, '%')
                  </if>
                  <if test="req != null and req.assigneeRealname != null and req.assigneeRealname != ''">
                    and newer.assignee_realname like concat('%', #{req.assigneeRealname}, '%')
                  </if>
                  <if test="completeTimeGe != null">
                    and newer.complete_time &gt;= #{completeTimeGe}
                  </if>
                  <if test="completeTimeLe != null">
                    and newer.complete_time &lt;= #{completeTimeLe}
                  </if>
                  and (
                    newer.assignee_user_id = #{userId}
                    or exists (
                      select 1
                      from wf_task_candidate newer_tc
                      where newer_tc.tenant_id = newer.tenant_id
                        and newer_tc.task_id = newer.id
                        and newer_tc.candidate_user_id = #{userId}
                        and newer_tc.status = #{candidateStatus}
                        and newer_tc.delete_flag = 0
                    )
                  )
                  and (
                    coalesce(newer.complete_time, newer.update_time, newer.create_time) &gt;
                      coalesce(t.complete_time, t.update_time, t.create_time)
                    or (
                      coalesce(newer.complete_time, newer.update_time, newer.create_time) =
                        coalesce(t.complete_time, t.update_time, t.create_time)
                      and newer.id &gt; t.id
                    )
                  )
              )
            order by ${orderBySql}
            </script>
            """)
    IPage<Task> selectLatestDonePage(Page<Task> page,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("candidateStatus") String candidateStatus,
            @Param("doneStatuses") List<String> doneStatuses,
            @Param("req") TaskPageReq req,
            @Param("startTimeGe") LocalDateTime startTimeGe,
            @Param("startTimeLe") LocalDateTime startTimeLe,
            @Param("completeTimeGe") LocalDateTime completeTimeGe,
            @Param("completeTimeLe") LocalDateTime completeTimeLe,
            @Param("hasInstanceFilters") boolean hasInstanceFilters,
            @Param("orderBySql") String orderBySql);
}
