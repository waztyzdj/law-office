<script setup lang="ts">
import type { DepartInfo, DepartRoleInfo } from '#/api/system/depart';
import type { RoleInfo } from '#/api/system/role';
import type { UserInfo } from '#/api/system/user';

import { computed, onMounted, ref, watch } from 'vue';

import { FormItem, Select, Tag } from 'ant-design-vue';

import { getDepartRoles } from '#/api/system/depart';
import {
  listPickerDeparts,
  listPickerRoles,
  listPickerUsers,
} from '#/api/system/picker';

interface AssigneeJson {
  departRoleIds?: string[];
  roleIds?: string[];
  userIds?: string[];
}

interface SelectOption {
  label: string;
  value: string;
}

interface DepartRoleOption extends SelectOption {
  departId?: string;
}

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
    modelValue?: Record<string, unknown>;
    type?: string;
  }>(),
  {
    disabled: false,
    modelValue: () => ({}),
    type: 'starter',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: AssigneeJson];
  'update:type': [value: string];
}>();

const assigneeTypeOptions = [
  { label: '发起人本人', value: 'starter' },
  { label: '指定人员', value: 'user' },
  { label: '指定角色', value: 'role' },
  { label: '部门负责人', value: 'depart_leader' },
  { label: '部门岗位', value: 'depart_role' },
  { label: '审批人自选', value: 'starter_select' },
];

const users = ref<UserInfo[]>([]);
const roles = ref<RoleInfo[]>([]);
const departs = ref<DepartInfo[]>([]);
const departRoles = ref<DepartRoleOption[]>([]);
const selectedUserIds = ref<string[]>([]);
const selectedRoleIds = ref<string[]>([]);
const selectedDepartRoleIds = ref<string[]>([]);
const syncing = ref(false);
const selectDropdownStyle = { zIndex: 3101 };

const userOptions = computed<SelectOption[]>(() =>
  users.value
    .filter((user) => user.id)
    .map((user) => ({
      label: formatUserLabel(user),
      value: user.id!,
    })),
);
const roleOptions = computed<SelectOption[]>(() =>
  roles.value
    .filter((role) => role.id)
    .map((role) => ({
      label: [role.roleName, role.roleCode ? `(${role.roleCode})` : undefined]
        .filter(Boolean)
        .join(' '),
      value: role.id!,
    })),
);
watch(
  () => [props.type, props.modelValue],
  () => syncFromModel(),
  { deep: true, immediate: true },
);

onMounted(loadOptions);

function formatUserLabel(user: UserInfo) {
  return [user.realname || user.username || user.id, user.username ? `(${user.username})` : undefined]
    .filter(Boolean)
    .join(' ');
}

function readIds(value: Record<string, unknown> | undefined, ...keys: string[]) {
  if (!value) {
    return [];
  }
  for (const key of keys) {
    const raw = value[key];
    if (Array.isArray(raw)) {
      return raw.map(String).filter(Boolean);
    }
    if (typeof raw === 'string' && raw.trim()) {
      return raw
        .split(/[,\n，]/)
        .map((item) => item.trim())
        .filter(Boolean);
    }
  }
  return [];
}

function syncFromModel() {
  syncing.value = true;
  const value = props.modelValue || {};
  selectedUserIds.value = readIds(value, 'userIds', 'users', 'ids');
  selectedRoleIds.value = readIds(value, 'roleIds', 'roles', 'ids');
  selectedDepartRoleIds.value = readIds(value, 'departRoleIds', 'departRoles');
  syncing.value = false;
}

async function loadOptions() {
  const [userList, roleList, departList] = await Promise.all([
    listPickerUsers(),
    listPickerRoles(),
    listPickerDeparts(),
  ]);
  users.value = userList ?? [];
  roles.value = roleList ?? [];
  departs.value = departList ?? [];
  await loadDepartRoles();
}

async function loadDepartRoles() {
  const departIds = departs.value.map((depart) => depart.id).filter(Boolean) as string[];
  const roleGroups = await Promise.all(
    departIds.map(async (departId) => ({
      departId,
      roles: await getDepartRoles(departId),
    })),
  );
  const departNameMap = Object.fromEntries(
    departs.value
      .filter((depart) => depart.id)
      .map((depart) => [depart.id!, depart.departName ?? depart.orgCode ?? depart.id!]),
  );
  departRoles.value = roleGroups.flatMap(({ departId, roles }) =>
    (roles ?? [])
      .filter((role: DepartRoleInfo) => role.id)
      .map((role: DepartRoleInfo) => ({
        departId,
        label: `${departNameMap[departId] ?? departId} / ${role.roleName ?? role.roleCode ?? role.id}`,
        value: role.id!,
      })),
  );
}

function emitAssigneeJson(type = props.type) {
  if (syncing.value) {
    return;
  }
  if (type === 'user') {
    emit('update:modelValue', { userIds: selectedUserIds.value });
    return;
  }
  if (type === 'starter_select') {
    emit('update:modelValue', {});
    return;
  }
  if (type === 'role') {
    emit('update:modelValue', { roleIds: selectedRoleIds.value });
    return;
  }
  if (type === 'depart_leader') {
    emit('update:modelValue', {});
    return;
  }
  if (type === 'depart_role') {
    emit('update:modelValue', { departRoleIds: selectedDepartRoleIds.value });
    return;
  }
  emit('update:modelValue', {});
}

function normalizeSelectValues(value: unknown) {
  if (Array.isArray(value)) {
    return value.map(String).filter(Boolean);
  }
  if (typeof value === 'string' && value) {
    return [value];
  }
  return [];
}

function handleTypeChange(value: unknown) {
  const nextType = typeof value === 'string' ? value : 'starter';
  selectedUserIds.value = [];
  selectedRoleIds.value = [];
  selectedDepartRoleIds.value = [];
  emit('update:type', nextType);
  emitAssigneeJson(nextType);
}

function handleUserChange(value: unknown) {
  selectedUserIds.value = normalizeSelectValues(value);
  emitAssigneeJson(props.type);
}

function handleRoleChange(value: unknown) {
  selectedRoleIds.value = normalizeSelectValues(value);
  emitAssigneeJson('role');
}

function handleDepartRoleChange(value: unknown) {
  selectedDepartRoleIds.value = normalizeSelectValues(value);
  emitAssigneeJson('depart_role');
}
</script>

<template>
  <div class="workflow-assignee-selector">
    <FormItem
      label="审批人类型"
      required
    >
      <Select
        :disabled="disabled"
        :dropdown-style="selectDropdownStyle"
        :options="assigneeTypeOptions"
        :value="type"
        popup-class-name="workflow-assignee-select-popup"
        @change="handleTypeChange"
      />
    </FormItem>

    <FormItem
      v-if="type === 'user'"
      label="指定人员"
      required
    >
      <Select
        :disabled="disabled"
        :dropdown-style="selectDropdownStyle"
        :options="userOptions"
        :value="selectedUserIds"
        mode="multiple"
        option-filter-prop="label"
        placeholder="请选择审批人员"
        show-search
        @change="handleUserChange"
      />
    </FormItem>

    <FormItem
      v-else-if="type === 'starter_select'"
      label="选择方式"
    >
      <Tag color="blue">运行时选择</Tag>
      <div class="assignee-tip">流程到达该节点前，由上一环节办理人从本单位人员中选择下一审批人。</div>
    </FormItem>

    <FormItem
      v-else-if="type === 'role'"
      label="指定角色"
      required
    >
      <Select
        :disabled="disabled"
        :dropdown-style="selectDropdownStyle"
        :options="roleOptions"
        :value="selectedRoleIds"
        mode="multiple"
        option-filter-prop="label"
        placeholder="请选择系统角色"
        show-search
        @change="handleRoleChange"
      />
    </FormItem>

    <FormItem
      v-else-if="type === 'depart_leader'"
      label="负责人范围"
    >
      <Tag color="blue">发起人当前部门负责人</Tag>
      <div class="assignee-tip">运行时按发起人主部门解析唯一部门负责人。</div>
    </FormItem>

    <FormItem
      v-else-if="type === 'depart_role'"
      label="部门岗位"
      required
    >
      <Select
        :disabled="disabled"
        :dropdown-style="selectDropdownStyle"
        :options="departRoles"
        :value="selectedDepartRoleIds"
        mode="multiple"
        option-filter-prop="label"
        placeholder="请选择组织机构中的部门岗位"
        show-search
        @change="handleDepartRoleChange"
      />
    </FormItem>

    <div
      v-else
      class="starter-tip"
    >
      <Tag color="default">发起人本人审批</Tag>
    </div>
  </div>
</template>

<style scoped>
.workflow-assignee-selector {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.assignee-tip {
  color: #6b7280;
  font-size: 12px;
  line-height: 22px;
  margin-top: 4px;
}

.starter-tip {
  margin-bottom: 16px;
}
</style>
