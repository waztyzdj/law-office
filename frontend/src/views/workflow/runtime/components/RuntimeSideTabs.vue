<script setup lang="ts">
import type {
  AdminOperationRecordInfo,
  InstanceDetailInfo,
  InstanceDiagramInfo,
  WorkflowCcRecordInfo,
} from '#/api/workflow';
import type { ProcessProgressNode } from './runtimeTypes';

import { Button, Empty, Tabs, Tag, Timeline } from 'ant-design-vue';

import {
  adminOperationTypeMap,
  ccTriggerActionMap,
  getAdminOperationStatusMeta,
  getApprovalModeMeta,
  getStatusMeta,
  getWorkflowActionMeta,
} from '../../components/status';
import RuntimeProcessDiagram from './RuntimeProcessDiagram.vue';

interface Props {
  activeKey: string;
  adminMonitorMode?: boolean;
  adminReassignable?: boolean;
  adminOperationRecords?: AdminOperationRecordInfo[];
  ccRecords: WorkflowCcRecordInfo[];
  detail?: InstanceDetailInfo;
  diagram?: InstanceDiagramInfo;
  nodes: ProcessProgressNode[];
}

defineProps<Props>();

const emit = defineEmits<{
  adminReassign: [node: ProcessProgressNode];
  'update:activeKey': [value: string];
}>();

function getTimelineColor(action?: string) {
  if (action === 'reject' || action === 'return') {
    return 'red';
  }
  return 'green';
}

function getProgressNodeColor(node: ProcessProgressNode) {
  if (node.status === 'current') {
    return 'blue';
  }
  if (node.status === 'end') {
    if (node.resultStatus === 'withdrawn') {
      return 'gray';
    }
    if (node.resultStatus === 'rejected' || node.resultStatus === 'terminated') {
      return 'red';
    }
    return 'green';
  }
  return getTimelineColor(node.action);
}

function getCcStatusMeta(status?: string) {
  if (status === 'read') {
    return { color: 'success', label: '已读' };
  }
  if (status === 'unread') {
    return { color: 'processing', label: '未读' };
  }
  if (status === 'canceled') {
    return { color: 'default', label: '已取消' };
  }
  return { color: 'default', label: status || '-' };
}

function getCcTriggerLabel(triggerAction?: string) {
  return ccTriggerActionMap[triggerAction ?? ''] ?? triggerAction ?? '-';
}

function getAdminOperationLabel(operationType?: string) {
  return adminOperationTypeMap[operationType ?? ''] ?? operationType ?? '-';
}

function formatAdminOperator(record: AdminOperationRecordInfo) {
  return record.operatorRealname || record.operatorUsername || '-';
}

function parseRecordSnapshot(snapshotJson?: string) {
  if (!snapshotJson) {
    return {};
  }
  try {
    const value = JSON.parse(snapshotJson);
    return value && typeof value === 'object' ? (value as Record<string, any>) : {};
  } catch {
    return {};
  }
}

function getAdminChangeLabel(record: AdminOperationRecordInfo) {
  return record.operationType === 'reassign' ? '处理人' : '结果';
}

function formatAdminChange(record: AdminOperationRecordInfo) {
  const before = parseRecordSnapshot(record.beforeSnapshotJson);
  const after = parseRecordSnapshot(record.afterSnapshotJson);
  if (record.operationType === 'reassign') {
    const beforeName = before.assigneeRealname || before.assigneeUsername || '-';
    const afterName = after.assigneeRealname || after.assigneeUsername || '-';
    return `${beforeName} -> ${afterName}`;
  }
  if (record.operationType === 'terminate') {
    return '流程已终止，当前待办已取消';
  }
  if (record.operationType === 'resend_notice') {
    const taskIds = Array.isArray(before.taskIds) ? before.taskIds.length : 0;
    return taskIds > 0 ? `已补发 ${taskIds} 个当前待办通知` : '已补发当前待办通知';
  }
  return '';
}
</script>

<template>
  <Tabs
    :active-key="activeKey"
    class="runtime-side-tabs"
    @update:active-key="(nextKey) => emit('update:activeKey', String(nextKey))"
  >
    <Tabs.TabPane
      key="records"
      tab="审批意见"
    >
      <div
        v-if="nodes.length"
        class="process-progress-panel"
      >
        <Timeline class="process-progress-timeline">
          <Timeline.Item
            v-for="node in nodes"
            :key="node.id"
            :color="getProgressNodeColor(node)"
          >
            <div class="progress-node">
              <div class="progress-node-head">
                <span class="progress-node-name">{{ node.name }}</span>
                <Tag
                  v-if="node.status === 'current'"
                  color="processing"
                >
                  当前
                </Tag>
                <Tag
                  v-if="node.status === 'current' && node.approvalMode"
                  :color="getApprovalModeMeta(node.approvalMode).color"
                >
                  {{ getApprovalModeMeta(node.approvalMode).label }}
                </Tag>
                <Tag
                  v-else-if="node.action"
                  :color="getWorkflowActionMeta(node.action).color"
                >
                  {{ getWorkflowActionMeta(node.action).label }}
                </Tag>
                <Tag
                  v-if="node.status === 'end' && node.resultStatus"
                  :color="getStatusMeta(node.resultStatus).color"
                >
                  {{ getStatusMeta(node.resultStatus).label }}
                </Tag>
                <Button
                  v-if="adminReassignable && node.status === 'current' && node.taskId"
                  class="progress-node-reassign"
                  size="small"
                  type="primary"
                  @click="emit('adminReassign', node)"
                >
                  改派
                </Button>
              </div>
              <div
                v-if="node.status !== 'end' || node.time"
                class="progress-node-meta"
              >
                <span v-if="node.status !== 'end'">处理人：{{ node.actor || '-' }}</span>
                <span v-if="node.groupProgress">进度：{{ node.groupProgress }}</span>
                <span>时间：{{ node.time || '-' }}</span>
              </div>
              <div
                v-if="node.comment"
                class="progress-node-comment"
              >
                {{ node.comment }}
              </div>
            </div>
          </Timeline.Item>
        </Timeline>
      </div>
      <div
        v-else
        class="runtime-empty-panel"
      >
        <Empty description="暂无审批意见" />
      </div>
    </Tabs.TabPane>

    <Tabs.TabPane
      key="diagram"
      tab="流程图"
    >
      <RuntimeProcessDiagram
        :detail="detail"
        :diagram="diagram"
      />
    </Tabs.TabPane>

    <Tabs.TabPane
      key="circulate"
      tab="抄送"
    >
      <div
        v-if="ccRecords.length"
        class="cc-record-list"
      >
        <div
          v-for="record in ccRecords"
          :key="record.id"
          class="cc-record-item"
        >
          <div class="cc-record-head">
            <span class="cc-record-receiver">
              {{ record.receiverRealname || record.receiverUsername || '-' }}
            </span>
            <Tag :color="getCcStatusMeta(record.status).color">
              {{ getCcStatusMeta(record.status).label }}
            </Tag>
          </div>
          <div class="cc-record-meta">
            <span>触发：{{ getCcTriggerLabel(record.triggerAction) }}</span>
            <span>节点：{{ record.nodeName || '-' }}</span>
            <span>时间：{{ record.createTime || '-' }}</span>
          </div>
          <div
            v-if="record.readTime"
            class="cc-record-meta"
          >
            <span>阅读时间：{{ record.readTime }}</span>
          </div>
          <div
            v-if="record.remark"
            class="cc-record-remark"
          >
            {{ record.remark }}
          </div>
        </div>
      </div>
      <div
        v-else
        class="runtime-empty-panel circulate-empty-panel"
      >
        <Empty description="暂无抄送记录" />
      </div>
    </Tabs.TabPane>

    <Tabs.TabPane
      v-if="adminMonitorMode"
      key="adminRecords"
      tab="维护记录"
    >
      <div
        v-if="adminOperationRecords?.length"
        class="admin-record-list"
      >
        <div
          v-for="record in adminOperationRecords"
          :key="record.id"
          class="admin-record-item"
        >
          <div class="admin-record-head">
            <span class="admin-record-title">
              {{ getAdminOperationLabel(record.operationType) }}
            </span>
            <Tag :color="getAdminOperationStatusMeta(record.status).color">
              {{ getAdminOperationStatusMeta(record.status).label }}
            </Tag>
          </div>
          <div class="admin-record-fields">
            <div class="admin-record-field">
              <span class="admin-record-label">操作人</span>
              <span class="admin-record-value">{{ formatAdminOperator(record) }}</span>
            </div>
            <div class="admin-record-field">
              <span class="admin-record-label">时间</span>
              <span class="admin-record-value">{{ record.operateTime || '-' }}</span>
            </div>
            <div
              v-if="record.operationReason"
              class="admin-record-field"
            >
              <span class="admin-record-label">原因</span>
              <span class="admin-record-value">{{ record.operationReason }}</span>
            </div>
            <div
              v-if="formatAdminChange(record)"
              class="admin-record-field"
            >
              <span class="admin-record-label">{{ getAdminChangeLabel(record) }}</span>
              <span class="admin-record-value">{{ formatAdminChange(record) }}</span>
            </div>
            <div
              v-if="record.status === 'failed' && record.errorMessage"
              class="admin-record-field admin-record-field--error"
            >
              <span class="admin-record-label">失败原因</span>
              <span class="admin-record-value">{{ record.errorMessage }}</span>
            </div>
          </div>
        </div>
      </div>
      <div
        v-else
        class="runtime-empty-panel"
      >
        <Empty description="暂无维护记录" />
      </div>
    </Tabs.TabPane>
  </Tabs>
</template>

<style scoped>
.runtime-side-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.runtime-side-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.runtime-side-tabs :deep(.ant-tabs-content),
.runtime-side-tabs :deep(.ant-tabs-tabpane) {
  min-height: 100%;
}

.runtime-empty-panel {
  align-items: center;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  justify-content: center;
  min-height: 180px;
}

.empty-description {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}

.empty-description > div {
  color: #4b5563;
  font-size: 14px;
}

.circulate-empty-panel {
  min-height: 180px;
}

.cc-record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 4px 0;
}

.admin-record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 4px 0;
}

.cc-record-item {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px 12px;
}

.admin-record-item {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px 12px;
}

.cc-record-head {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  margin-bottom: 6px;
}

.admin-record-head {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  margin-bottom: 6px;
}

.cc-record-receiver {
  color: #111827;
  font-weight: 500;
}

.admin-record-title {
  color: #111827;
  font-weight: 500;
}

.cc-record-meta {
  color: #6b7280;
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 6px 14px;
  line-height: 1.7;
}

.admin-record-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-record-field {
  align-items: flex-start;
  color: #6b7280;
  display: grid;
  font-size: 13px;
  grid-template-columns: 64px minmax(0, 1fr);
  line-height: 1.6;
}

.admin-record-label {
  color: #6b7280;
  padding-right: 8px;
  text-align: right;
  white-space: nowrap;
}

.admin-record-label::after {
  content: '：';
}

.admin-record-value {
  color: #4b5563;
  min-width: 0;
  overflow-wrap: anywhere;
}

.cc-record-remark {
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-record-field--error,
.admin-record-field--error .admin-record-label,
.admin-record-field--error .admin-record-value {
  color: #cf1322;
}

.process-progress-panel {
  padding: 8px 4px 0;
}

.process-progress-timeline {
  padding: 4px 4px 0;
}

.progress-node {
  min-width: 0;
}

.progress-node-head {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

.progress-node-reassign {
  margin-left: auto;
}

.progress-node-name {
  color: #111827;
  font-weight: 500;
}

.progress-node-meta {
  color: #6b7280;
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 8px 16px;
}

.progress-node-comment {
  background: #fafafa;
  border-radius: 6px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 8px;
  padding: 8px 10px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
