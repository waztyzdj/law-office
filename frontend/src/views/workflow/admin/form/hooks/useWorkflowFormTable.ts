import { computed, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type {
  WorkflowCategoryInfo,
  WorkflowFormDefinitionInfo,
} from '#/api/workflow';

import {
  copyWorkflowFormAsDraft,
  deleteWorkflowForm,
  listWorkflowCategories,
  pageWorkflowForms,
  publishWorkflowForm,
} from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowFormTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageWorkflowForms,
    },
    storageConfig: {
      filtersKey: 'workflow_form_list_filters',
    },
  });

  const categoryMap = ref<Record<string, string>>({});
  const records = computed(
    () => table.dataSource.value as WorkflowFormDefinitionInfo[],
  );

  async function loadCategoryMap() {
    const categories = await listWorkflowCategories();
    categoryMap.value = buildCategoryMap(categories ?? []);
  }

  async function handleRefresh() {
    await Promise.all([table.loadData(), loadCategoryMap()]);
  }

  function handlePublish(record: WorkflowFormDefinitionInfo) {
    if (!record.id) {
      return;
    }
    if (record.status !== 'draft') {
      message.warning('只有草稿表单允许发布');
      return;
    }

    Modal.confirm({
      cancelText: '取消',
      content: `确认发布表单“${record.formName ?? ''}”吗？`,
      okText: '发布',
      onOk: async () => {
        await publishWorkflowForm(record.id!);
        message.success('表单已发布');
        await table.loadData();
      },
      title: '发布表单',
    });
  }

  async function handleCopyAsDraft(record: WorkflowFormDefinitionInfo) {
    if (!record.id) {
      return;
    }

    await copyWorkflowFormAsDraft(record.id);
    message.success('已复制为草稿');
    await table.loadData();
  }

  function handleDelete(record: WorkflowFormDefinitionInfo) {
    if (!record.id) {
      return;
    }
    if (record.status !== 'draft') {
      message.warning('只有草稿表单允许删除');
      return;
    }

    Modal.confirm({
      cancelText: '取消',
      content: `确认删除表单“${record.formName ?? ''}”吗？`,
      okText: '删除',
      okType: 'danger',
      onOk: async () => {
        await deleteWorkflowForm(record.id!);
        message.success('删除成功');
        await table.loadData();
      },
      title: '确认删除',
    });
  }

  return {
    activeFilters: table.activeFilters,
    categoryMap,
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

function buildCategoryMap(categories: WorkflowCategoryInfo[]) {
  return Object.fromEntries(
    categories
      .filter((item) => item.id)
      .map((item) => [item.id!, item.categoryName ?? item.categoryCode ?? item.id!]),
  );
}
