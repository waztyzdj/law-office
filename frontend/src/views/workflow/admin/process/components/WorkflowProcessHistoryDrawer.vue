<script setup lang="ts">
import type { WorkflowProcessModelInfo } from '#/api/workflow';

import { computed, h, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Space, Tag, message } from 'ant-design-vue';

import { listWorkflowProcessHistory } from '#/api/workflow';
import { BaseTable } from '#/components/BaseTable';
import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import {
  designerTypeMap,
  processModelStatusOptions,
} from '../../../components/status';
import { useWorkflowHistoryTable } from '../../hooks/useWorkflowHistoryTable';

interface Props {
  categoryMap: Record<string, string>;
  formMap: Record<string, string>;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  viewDesign: [record: WorkflowProcessModelInfo];
}>();

const currentProcess = ref<WorkflowProcessModelInfo>();
const records = ref<WorkflowProcessModelInfo[]>([]);
const loading = ref(false);
const {
  activeFilters,
  displayedRecords,
  handleColumnEmit,
  handleTableChange,
  resetHistoryTable,
  setHistoryTotal,
  tablePagination,
} = useWorkflowHistoryTable(records);

const drawerTitle = computed(() =>
  currentProcess.value?.processName
    ? `历史版本 - ${currentProcess.value.processName}`
    : '历史版本',
);
const currentDesignerTypeLabel = computed(() =>
  currentProcess.value ? resolveDesignerType(currentProcess.value) : '-',
);
const currentCategoryLabel = computed(() =>
  currentProcess.value ? resolveCategory(currentProcess.value) : '-',
);

const tableConfig = computed(() =>
  defineTableColumns<WorkflowProcessModelInfo>(
    [
      {
        dataIndex: 'version',
        title: '版本',
        options: {
          align: 'center',
          customRender: ({ record }) => `v${record.version ?? 1}`,
          width: 90,
        },
      },
      {
        dataIndex: 'status',
        title: '状态',
        options: {
          align: 'center',
          columnType: 'select',
          customRender: ({ record }) =>
            h(WorkflowStatusTag, { status: record.status }),
          selectOptions: processModelStatusOptions,
          width: 110,
        },
      },
      {
        dataIndex: 'formDefinitionId',
        title: '绑定表单',
        options: {
          columnType: 'select',
          customRender: ({ record }) => resolveForm(record),
          selectOptions: Object.entries(props.formMap).map(([value, label]) => ({
            label,
            value,
          })),
          width: 240,
        },
      },
      {
        dataIndex: 'publishedTime',
        title: '发布时间',
        options: {
          align: 'center',
          columnType: 'datetime',
          customRender: ({ record }) => record.publishedTime ?? '-',
          width: 180,
        },
      },
      {
        dataIndex: 'historyAction',
        title: '操作',
        options: {
          align: 'center',
          fixed: 'right',
          hasFilter: false,
          width: 120,
          customRender: ({ record }) =>
            h(Space, { class: 'process-history__action', size: 'middle' }, () => [
              h('a', { onClick: () => emit('viewDesign', record) }, '查看设计'),
            ]),
        },
      },
    ],
    activeFilters,
    handleColumnEmit,
    tablePagination,
    { minTableWidth: 760, tableKey: 'workflow_process_history' },
  ),
);
const tableScroll = computed(() => tableConfig.value.scroll ?? { x: 760 });

function resolveCategory(record: WorkflowProcessModelInfo) {
  return props.categoryMap[record.categoryId ?? ''] ?? record.categoryId ?? '-';
}

function resolveForm(record: WorkflowProcessModelInfo) {
  return props.formMap[record.formDefinitionId ?? ''] ?? record.formDefinitionId ?? '-';
}

function resolveDesignerType(record: WorkflowProcessModelInfo) {
  return designerTypeMap[record.designerType ?? ''] ?? record.designerType ?? '-';
}

async function loadData(record: WorkflowProcessModelInfo) {
  if (!record.id) {
    message.warning('请选择流程版本');
    return;
  }

  loading.value = true;
  try {
    records.value = await listWorkflowProcessHistory(record.id);
    setHistoryTotal(records.value.length);
  } finally {
    loading.value = false;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[60%]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'px-5 py-4 sm:px-6',
  footer: false,
  title: drawerTitle.value,
  zIndex: 1000,
});

async function open(record: WorkflowProcessModelInfo) {
  currentProcess.value = record;
  records.value = [];
  resetHistoryTable();
  drawerApi.setState({ loading: true, title: drawerTitle.value }).open();

  try {
    await loadData(record);
  } finally {
    drawerApi.setState({ loading: false, title: drawerTitle.value });
  }
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="process-history">
      <div class="process-history__summary">
        <Space
          class="process-history__summary-main"
          wrap
        >
          <Tag color="processing">
            {{ currentProcess?.processKey ?? '-' }}
          </Tag>
          <span>{{ currentProcess?.processName ?? '-' }}</span>
        </Space>
        <Space
          class="process-history__summary-meta"
          wrap
        >
          <span class="process-history__meta-item">
            <span class="process-history__meta-label">设计器</span>
            <Tag>{{ currentDesignerTypeLabel }}</Tag>
          </span>
          <span class="process-history__meta-item">
            <span class="process-history__meta-label">流程分类</span>
            <span>{{ currentCategoryLabel }}</span>
          </span>
        </Space>
      </div>

      <BaseTable
        :columns="tableConfig.columns"
        :data-source="displayedRecords"
        :loading="loading"
        :pagination="false"
        :scroll="tableScroll"
        :show-card="false"
        :show-toolbar="false"
        row-key="id"
        size="small"
        @change="handleTableChange"
      />
    </div>
  </Drawer>
</template>

<style scoped>
.process-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.process-history__summary {
  align-items: center;
  color: #374151;
  display: flex;
  font-size: 14px;
  gap: 12px 24px;
  justify-content: space-between;
}

.process-history__summary-main,
.process-history__summary-meta {
  min-width: 0;
}

.process-history__meta-item {
  align-items: center;
  display: inline-flex;
  gap: 6px;
}

.process-history__meta-label {
  color: #6b7280;
}

.process-history__action {
  justify-content: center;
  width: 100%;
}
</style>
