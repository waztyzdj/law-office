<script setup lang="ts">
import type {
  InstanceDetailInfo,
  InstanceDiagramInfo,
  WorkflowCcRecordInfo,
} from '#/api/workflow';
import type { ProcessProgressNode } from './runtimeTypes';

import { Button, Empty, Tabs, Tag, Timeline } from 'ant-design-vue';

import {
  ccTriggerActionMap,
  getApprovalModeMeta,
  getStatusMeta,
  getWorkflowActionMeta,
} from '../../components/status';
import RuntimeProcessDiagram from './RuntimeProcessDiagram.vue';

interface Props {
  activeKey: string;
  adminReassignable?: boolean;
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

.cc-record-item {
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

.cc-record-receiver {
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

.cc-record-remark {
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
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
