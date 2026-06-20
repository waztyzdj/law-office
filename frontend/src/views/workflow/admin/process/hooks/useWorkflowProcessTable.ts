import { computed, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type {
  WorkflowCategoryInfo,
  WorkflowFormDefinitionInfo,
  WorkflowProcessModelInfo,
} from '#/api/workflow';

import {
  copyWorkflowProcessAsDraft,
  deleteWorkflowProcess,
  pageLatestWorkflowProcesses,
  pageLatestWorkflowForms,
  listWorkflowCategories,
  publishWorkflowProcess,
} from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowProcessTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageLatestWorkflowProcesses,
    },
    storageConfig: {
      filtersKey: 'workflow_process_list_filters',
    },
  });

  const formMap = ref<Record<string, string>>({});
  const categoryMap = ref<Record<string, string>>({});
  const records = computed(
    () => table.dataSource.value as WorkflowProcessModelInfo[],
  );

  async function loadFormMap() {
    const formPage = await pageLatestWorkflowForms({
      pageNum: 1,
      pageSize: 500,
      sortField: 'form_key',
      sortOrder: 'asc',
    });
    formMap.value = buildFormMap(formPage.records ?? []);
  }

  async function loadCategoryOptions() {
    const categories = await listWorkflowCategories();
    categoryMap.value = buildCategoryMap(categories ?? []);
  }

  async function loadReferenceData() {
    await Promise.all([loadFormMap(), loadCategoryOptions()]);
  }

  async function handleRefresh() {
    await Promise.all([table.loadData(), loadReferenceData()]);
  }

  async function handlePublish(record: WorkflowProcessModelInfo) {
    if (!record.id) {
      return;
    }
    if (record.status !== 'draft') {
      message.warning('只有草稿流程允许发布');
      return;
    }

    Modal.confirm({
      cancelText: '取消',
      content: `确认发布流程“${record.processName ?? ''}”吗？`,
      okText: '发布',
      onOk: async () => {
        await publishWorkflowProcess(record.id!);
        message.success('流程已发布');
        await table.loadData();
      },
      title: '发布流程',
    });
  }

  async function handleCopyAsDraft(record: WorkflowProcessModelInfo) {
    if (!record.id) {
      return;
    }

    await copyWorkflowProcessAsDraft(record.id);
    message.success('已新建版本');
    await table.loadData();
  }

  function handleDelete(record: WorkflowProcessModelInfo) {
    if (!record.id) {
      return;
    }
    if (record.status !== 'draft') {
      message.warning('只有草稿流程允许删除');
      return;
    }

    Modal.confirm({
      cancelText: '取消',
      content: `确认删除流程“${record.processName ?? ''}”吗？`,
      okText: '删除',
      okType: 'danger',
      onOk: async () => {
        await deleteWorkflowProcess(record.id!);
        message.success('删除成功');
        await table.loadData();
      },
      title: '确认删除',
    });
  }

  return {
    activeFilters: table.activeFilters,
    categoryMap,
    formMap,
    handleCopyAsDraft,
    handleDelete,
    handlePublish,
    handleRefresh,
    handleTableChange: table.handleTableChange,
    loadData: table.loadData,
    loading: table.loading,
    pagination: table.pagination,
    records,
  };
}

function buildFormMap(forms: WorkflowFormDefinitionInfo[]) {
  return Object.fromEntries(
    forms
      .filter((item) => item.id)
      .map((item) => [
        item.id!,
        `${item.formName ?? item.formKey ?? item.id} v${item.version ?? 1}`,
      ]),
  );
}

function buildCategoryMap(categories: WorkflowCategoryInfo[]) {
  return Object.fromEntries(
    categories
      .filter((item) => item.id)
      .map((item) => [item.id!, item.categoryName ?? item.categoryCode ?? item.id!]),
  );
}
