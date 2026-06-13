<script setup lang="ts">
import type { WorkflowFormDefinitionInfo } from '#/api/workflow';

import { computed, h, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Space, message } from 'ant-design-vue';

import { listWorkflowFormHistory } from '#/api/workflow';
import { BaseTable } from '#/components/BaseTable';
import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import { formDefinitionStatusOptions } from '../../../components/status';
import { useWorkflowHistoryTable } from '../../hooks/useWorkflowHistoryTable';

interface Props {
  categoryMap: Record<string, string>;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  viewDesign: [record: WorkflowFormDefinitionInfo];
}>();

const currentForm = ref<WorkflowFormDefinitionInfo>();
const records = ref<WorkflowFormDefinitionInfo[]>([]);
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
  currentForm.value?.formName
    ? `历史版本 - ${currentForm.value.formName}`
    : '历史版本',
);
const currentCategoryLabel = computed(() =>
  currentForm.value ? resolveCategory(currentForm.value) : '-',
);

const tableConfig = computed(() =>
  defineTableColumns<WorkflowFormDefinitionInfo>(
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
          selectOptions: formDefinitionStatusOptions,
          width: 110,
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
        dataIndex: 'remark',
        title: '备注',
        options: {
          sorter: false,
          width: 240,
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
            h(Space, { class: 'form-history__action', size: 'middle' }, () => [
              h('a', { onClick: () => emit('viewDesign', record) }, '查看设计'),
            ]),
        },
      },
    ],
    activeFilters,
    handleColumnEmit,
    tablePagination,
    { minTableWidth: 760, tableKey: 'workflow_form_history' },
  ),
);
const tableScroll = computed(() => tableConfig.value.scroll ?? { x: 760 });

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[60%]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'px-5 py-4 sm:px-6',
  footer: false,
  title: drawerTitle.value,
  zIndex: 1000,
});

function resolveCategory(record: WorkflowFormDefinitionInfo) {
  return props.categoryMap[record.categoryId ?? ''] ?? record.categoryId ?? '-';
}

async function loadData(record: WorkflowFormDefinitionInfo) {
  if (!record.id) {
    message.warning('请选择表单版本');
    return;
  }

  loading.value = true;
  try {
    records.value = await listWorkflowFormHistory(record.id);
    setHistoryTotal(records.value.length);
  } finally {
    loading.value = false;
  }
}

async function open(record: WorkflowFormDefinitionInfo) {
  currentForm.value = record;
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
    <div class="form-history">
      <div class="form-history__summary">
        <Space
          class="form-history__summary-main"
          wrap
        >
          <span>{{ currentForm?.formKey ?? '-' }}</span>
          <span>{{ currentForm?.formName ?? '-' }}</span>
        </Space>
        <Space
          class="form-history__summary-meta"
          wrap
        >
          <span class="form-history__meta-item">
            <span class="form-history__meta-label">流程分类</span>
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
.form-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-history__summary {
  align-items: center;
  color: #374151;
  display: flex;
  font-size: 14px;
  gap: 12px 24px;
  justify-content: space-between;
}

.form-history__summary-main,
.form-history__summary-meta {
  min-width: 0;
}

.form-history__meta-item {
  align-items: center;
  display: inline-flex;
  gap: 6px;
}

.form-history__meta-label {
  color: #6b7280;
}

.form-history__action {
  justify-content: center;
  width: 100%;
}
</style>
