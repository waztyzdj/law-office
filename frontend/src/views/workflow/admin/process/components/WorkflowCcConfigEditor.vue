<script setup lang="ts">
import type { DepartInfo } from '#/api/system/depart';
import type { RoleInfo } from '#/api/system/role';
import type { UserInfo } from '#/api/system/user';

import { computed, onMounted, ref, watch } from 'vue';

import { Button, CheckboxGroup, FormItem, Select, Space } from 'ant-design-vue';

import {
  listPickerDeparts,
  listPickerRoles,
  listPickerUsers,
} from '#/api/system/picker';

type CcTargetType = 'depart' | 'role' | 'starter_supervisor' | 'user';

interface CcTarget {
  targetIds?: string[];
  targetType: string;
}

interface CcConfig {
  events?: string[];
  targets?: CcTarget[];
}

interface SelectOption {
  label: string;
  value: string;
}

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
    modelValue?: CcConfig;
  }>(),
  {
    disabled: false,
    modelValue: () => ({ events: [], targets: [] }),
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: CcConfig];
}>();

const eventOptions = [
  { label: '发起后', value: 'start' },
  { label: '节点通过后', value: 'node_approved' },
  { label: '流程结束后', value: 'process_finished' },
];
const targetTypeOptions = [
  { label: '指定人员', value: 'user' },
  { label: '指定角色', value: 'role' },
  { label: '指定部门', value: 'depart' },
  { label: '发起人直属上级', value: 'starter_supervisor' },
];

const users = ref<UserInfo[]>([]);
const roles = ref<RoleInfo[]>([]);
const departs = ref<DepartInfo[]>([]);
const events = ref<string[]>([]);
const targets = ref<CcTarget[]>([]);
const syncing = ref(false);
const selectDropdownStyle = { zIndex: 1002 };

const userOptions = computed<SelectOption[]>(() =>
  users.value
    .filter((user) => user.id)
    .map((user) => ({
      label: [user.realname || user.username || user.id, user.username ? `(${user.username})` : undefined]
        .filter(Boolean)
        .join(' '),
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
const departOptions = computed<SelectOption[]>(() =>
  departs.value
    .filter((depart) => depart.id)
    .map((depart) => ({
      label: depart.departName ?? depart.orgCode ?? depart.id!,
      value: depart.id!,
    })),
);

watch(
  () => props.modelValue,
  () => syncFromModel(),
  { deep: true, immediate: true },
);

onMounted(async () => {
  const [userList, roleList, departList] = await Promise.all([
    listPickerUsers(),
    listPickerRoles(),
    listPickerDeparts(),
  ]);
  users.value = userList ?? [];
  roles.value = roleList ?? [];
  departs.value = departList ?? [];
});

function syncFromModel() {
  syncing.value = true;
  events.value = Array.isArray(props.modelValue?.events)
    ? props.modelValue.events.map(String).filter(Boolean)
    : [];
  targets.value = Array.isArray(props.modelValue?.targets)
    ? props.modelValue.targets.map((target) => ({
        targetIds: Array.isArray(target.targetIds)
          ? target.targetIds.map(String).filter(Boolean)
          : [],
        targetType: normalizeTargetType(target.targetType),
      }))
    : [];
  syncing.value = false;
}

function normalizeTargetType(value: unknown): CcTargetType {
  return value === 'role' || value === 'depart' || value === 'starter_supervisor'
    ? value
    : 'user';
}

function emitConfig() {
  if (syncing.value) {
    return;
  }
  emit('update:modelValue', {
    events: events.value,
    targets: targets.value.map((target) => ({
      targetIds:
        target.targetType === 'starter_supervisor'
          ? []
          : (target.targetIds ?? []).filter(Boolean),
      targetType: target.targetType,
    })),
  });
}

function addTarget() {
  targets.value.push({ targetIds: [], targetType: 'user' });
  emitConfig();
}

function removeTarget(index: number) {
  targets.value.splice(index, 1);
  emitConfig();
}

function handleTargetTypeChange(target: CcTarget, value: unknown) {
  target.targetType = normalizeTargetType(value);
  target.targetIds = [];
  emitConfig();
}

function handleTargetIdsChange(target: CcTarget, value: unknown) {
  target.targetIds = Array.isArray(value) ? value.map(String).filter(Boolean) : [];
  emitConfig();
}

function optionsForTarget(target: CcTarget) {
  if (target.targetType === 'role') {
    return roleOptions.value;
  }
  if (target.targetType === 'depart') {
    return departOptions.value;
  }
  return userOptions.value;
}
</script>

<template>
  <div class="workflow-cc-config">
    <FormItem label="抄送触发">
      <CheckboxGroup
        v-model:value="events"
        :disabled="disabled"
        :options="eventOptions"
        @change="emitConfig"
      />
    </FormItem>

    <FormItem label="抄送对象">
      <div class="cc-target-list">
        <div
          v-for="(target, index) in targets"
          :key="index"
          class="cc-target-row"
        >
          <Select
            :disabled="disabled"
            :dropdown-style="selectDropdownStyle"
            :options="targetTypeOptions"
            :value="target.targetType"
            class="cc-target-type"
            @change="(value) => handleTargetTypeChange(target, value)"
          />
          <Select
            v-if="target.targetType !== 'starter_supervisor'"
            :disabled="disabled"
            :dropdown-style="selectDropdownStyle"
            :options="optionsForTarget(target)"
            :value="target.targetIds"
            class="cc-target-users"
            mode="multiple"
            option-filter-prop="label"
            placeholder="请选择抄送对象"
            show-search
            @change="(value) => handleTargetIdsChange(target, value)"
          />
          <div
            v-else
            class="cc-target-rule"
          >
            运行时按发起人主部门关系解析
          </div>
          <Button
            :disabled="disabled"
            danger
            size="small"
            type="link"
            @click="removeTarget(index)"
          >
            删除
          </Button>
        </div>
        <Space>
          <Button
            :disabled="disabled"
            size="small"
            type="dashed"
            @click="addTarget"
          >
            添加抄送对象
          </Button>
        </Space>
      </div>
    </FormItem>
  </div>
</template>

<style scoped>
.workflow-cc-config {
  display: flex;
  flex-direction: column;
}

.cc-target-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cc-target-row {
  align-items: center;
  display: flex;
  gap: 8px;
}

.cc-target-type {
  flex: 0 0 150px;
}

.cc-target-users {
  min-width: 220px;
  flex: 1;
}

.cc-target-rule {
  color: #6b7280;
  flex: 1;
  font-size: 12px;
}
</style>
