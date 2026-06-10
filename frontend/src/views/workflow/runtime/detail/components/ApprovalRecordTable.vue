<script setup lang="ts">
import { Table, Tag } from 'ant-design-vue';

import type { TableColumnsType } from 'ant-design-vue';

import type { OperationRecordInfo } from '#/api/workflow';

import { getWorkflowActionMeta } from '../../../components/status';

defineProps<{
  records?: OperationRecordInfo[];
}>();

const recordColumns: TableColumnsType<OperationRecordInfo> = [
  { dataIndex: 'operateTime', title: '操作时间', width: 180 },
  { dataIndex: 'nodeName', title: '节点', width: 160 },
  { dataIndex: 'action', title: '动作', width: 120 },
  { dataIndex: 'operatorRealname', title: '操作人', width: 140 },
  { dataIndex: 'targetRealname', title: '目标人', width: 140 },
  { dataIndex: 'comment', ellipsis: true, title: '意见' },
];
</script>

<template>
  <Table
    :columns="recordColumns"
    :data-source="records ?? []"
    :pagination="false"
    :scroll="{ x: 920 }"
    bordered
    row-key="id"
    size="small"
  >
    <template #bodyCell="{ column, record }">
      <Tag
        v-if="column.dataIndex === 'action'"
        :color="getWorkflowActionMeta(record.action).color"
      >
        {{ getWorkflowActionMeta(record.action).label }}
      </Tag>
      <span v-else-if="column.dataIndex === 'operatorRealname'">
        {{ record.operatorRealname ?? record.operatorUsername ?? '-' }}
      </span>
      <span v-else-if="column.dataIndex === 'targetRealname'">
        {{ record.targetRealname ?? record.targetUsername ?? '-' }}
      </span>
      <span v-else-if="column.dataIndex === 'nodeName'">
        {{ record.nodeName ?? record.nodeId ?? '-' }}
      </span>
      <span v-else-if="column.dataIndex === 'comment'">
        {{ record.comment ?? '-' }}
      </span>
    </template>
  </Table>
</template>
