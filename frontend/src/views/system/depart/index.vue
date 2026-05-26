<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type { DepartInfo } from '#/api/system/depart';

import {
  deleteDepart,
  getDepartPermissionIds,
  getDepartRoles,
  getDepartUserIds,
} from '#/api/system/depart';

import DepartFormDrawer from './components/DepartFormDrawer.vue';
import DepartMemberDrawer from './components/DepartMemberDrawer.vue';
import DepartRoleDrawer from './components/DepartRoleDrawer.vue';
import DepartTable from './components/DepartTable.vue';
import { useDepartTable } from './hooks/useDepartTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loadOrgTypeOptions,
  loading,
  orgTypeOptions,
  orgTypeSelectOptions,
  pagination,
  treeData,
  treeOptions,
} = useDepartTable();

const departFormDrawerRef = ref();
const departMemberDrawerRef = ref();
const departRoleDrawerRef = ref();
const DEFAULT_ROLE_DESCRIPTION = '部门默认角色';

function isDefaultDepartRole(role: { defaultRole?: boolean; description?: string }) {
  return role.defaultRole === true || role.description === DEFAULT_ROLE_DESCRIPTION;
}

function handleAdd() {
  departFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: DepartInfo) {
  departFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: DepartInfo) {
  departFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleMembers(record: DepartInfo) {
  departMemberDrawerRef.value?.open(record);
}

function handleRoles(record: DepartInfo) {
  departRoleDrawerRef.value?.open(record);
}

async function getDeleteRelationWarnings(record: DepartInfo) {
  if (!record.id) {
    return [];
  }

  const [userIds, roles, permissionIds] = await Promise.all([
    getDepartUserIds(record.id),
    getDepartRoles(record.id),
    getDepartPermissionIds(record.id),
  ]);
  const customRoleCount = roles.filter((role) => !isDefaultDepartRole(role)).length;
  const warnings: string[] = [];

  if (userIds.length > 0) {
    warnings.push(`${userIds.length} 名人员`);
  }
  if (customRoleCount > 0) {
    warnings.push(`${customRoleCount} 个部门角色`);
  }
  if (permissionIds.length > 0) {
    warnings.push(`${permissionIds.length} 项部门权限`);
  }

  return warnings;
}

async function handleDelete(record: DepartInfo) {
  const departId = record.id;
  if (!departId) {
    return;
  }

  const relationWarnings = await getDeleteRelationWarnings(record);
  const hasRelations = relationWarnings.length > 0;

  Modal.confirm({
    title: hasRelations ? '确认删除并清理关联关系' : '确认删除',
    content: hasRelations
      ? `机构"${record.departName}"下存在${relationWarnings.join('、')}，删除后将同步逻辑删除这些关联关系，是否继续？`
      : `确定要删除机构"${record.departName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      await deleteDepart(departId);
      await loadData();
    },
  });
}

function handleSaveSuccess() {
  loadData();
}

async function initPage() {
  await Promise.all([loadOrgTypeOptions(), loadData()]);

  if (orgTypeOptions.value.length === 0) {
    message.warning('机构类型字典为空，请先维护 sys_depart_org_type');
  }
}

onMounted(initPage);
</script>

<template>
  <div class="system-depart-container">
    <DepartTable
      :active-filters="activeFilters"
      :data-source="treeData"
      :loading="loading"
      :org-type-select-options="orgTypeSelectOptions"
      :pagination="pagination"
      @add="handleAdd"
      @add-child="handleAddChild"
      @change="handleTableChange"
      @delete="handleDelete"
      @edit="handleEdit"
      @members="handleMembers"
      @roles="handleRoles"
    />

    <DepartFormDrawer
      ref="departFormDrawerRef"
      :org-type-options="orgTypeOptions"
      :tree-options="treeOptions"
      @success="handleSaveSuccess"
    />

    <DepartMemberDrawer ref="departMemberDrawerRef" @success="handleSaveSuccess" />
    <DepartRoleDrawer ref="departRoleDrawerRef" @success="handleSaveSuccess" />
  </div>
</template>

<style scoped>
.system-depart-container {
  padding: 16px;
}
</style>
