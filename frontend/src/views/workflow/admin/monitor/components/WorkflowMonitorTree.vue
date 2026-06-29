<script setup lang="ts">
import type { EventDataNode, Key } from 'ant-design-vue/es/vc-tree/interface';

import { computed, ref } from 'vue';

import { Empty, InputSearch, Spin, Tree } from 'ant-design-vue';

import type { WorkflowMonitorScope } from '../hooks/useWorkflowMonitorTable';
import type { WorkflowMonitorTreeDataNode } from '../hooks/useWorkflowMonitorTree';

interface Props {
  expandedKeys: Key[];
  loading: boolean;
  selectedKeys: Key[];
  treeData: WorkflowMonitorTreeDataNode[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  expand: [keys: Key[]];
  select: [scope: WorkflowMonitorScope, key: Key];
}>();

const keyword = ref('');

const filteredTreeData = computed(() =>
  filterTreeData(props.treeData, keyword.value.trim().toLowerCase()),
);

function handleSelect(keys: Key[], info: { node?: EventDataNode }) {
  const node = info.node as undefined | WorkflowMonitorTreeDataNode;
  const key = keys[0] ?? node?.key;
  if (!node?.scope || key === undefined) {
    return;
  }
  emit('select', node.scope, key);
}

function filterTreeData(
  nodes: WorkflowMonitorTreeDataNode[],
  searchText: string,
): WorkflowMonitorTreeDataNode[] {
  if (!searchText) {
    return nodes;
  }
  return nodes
    .map((node) => {
      const matched = node.searchText.toLowerCase().includes(searchText);
      const children = filterTreeData(node.children ?? [], searchText);
      if (!matched && children.length === 0) {
        return undefined;
      }
      return {
        ...node,
        ...(children.length > 0 ? { children } : {}),
      };
    })
    .filter(Boolean) as WorkflowMonitorTreeDataNode[];
}
</script>

<template>
  <div class="workflow-monitor-tree">
    <InputSearch
      v-model:value="keyword"
      allow-clear
      class="workflow-monitor-tree__search"
      placeholder="搜索流程"
    />
    <Spin :spinning="loading">
      <Empty
        v-if="filteredTreeData.length === 0"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
      />
      <Tree
        v-else
        :expanded-keys="expandedKeys"
        :selected-keys="selectedKeys"
        :tree-data="filteredTreeData"
        block-node
        class="workflow-monitor-tree__tree"
        @expand="(keys) => $emit('expand', keys as Key[])"
        @select="handleSelect"
      />
    </Spin>
  </div>
</template>

<style scoped>
.workflow-monitor-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
  background: var(--ant-color-bg-container, #fff);
  border: 1px solid var(--ant-color-border, #f0f0f0);
  border-radius: 6px;
}

.workflow-monitor-tree__search {
  flex: 0 0 auto;
  width: 100%;
  height: 40px;
  margin-bottom: 12px;
}

.workflow-monitor-tree :deep(.ant-spin-nested-loading),
.workflow-monitor-tree :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
}

.workflow-monitor-tree :deep(.ant-spin-container) {
  display: flex;
  flex-direction: column;
}

.workflow-monitor-tree__tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
