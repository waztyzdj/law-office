<script setup lang="ts">
import type { ProcessProgressNode } from './runtimeTypes';

import { Empty, Tabs, Tag, Timeline } from 'ant-design-vue';

import {
  getApprovalModeMeta,
  getWorkflowActionMeta,
} from '../../components/status';

interface Props {
  activeKey: string;
  nodes: ProcessProgressNode[];
}

defineProps<Props>();

const emit = defineEmits<{
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
    return 'green';
  }
  return getTimelineColor(node.action);
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
              </div>
              <div
                v-if="node.status !== 'end'"
                class="progress-node-meta"
              >
                <span>处理人：{{ node.actor || '-' }}</span>
                <span v-if="node.groupProgress">进度：{{ node.groupProgress }}</span>
                <span>时间：{{ node.time || '-' }}</span>
              </div>
              <div
                v-if="node.comment && node.status !== 'end'"
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
      key="circulate"
      tab="传阅"
    >
      <div class="runtime-empty-panel circulate-empty-panel">
        <Empty description="暂无传阅记录">
          <template #description>
            <div class="empty-description">
              <div>暂无传阅记录</div>
              <span>传阅/抄送属于二期能力，后续会在这里展示传阅人、传阅时间和阅读状态。</span>
            </div>
          </template>
        </Empty>
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
