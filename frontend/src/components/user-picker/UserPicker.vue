<script setup lang="ts">
import type { UserInfo } from '#/api/system/user';
import type {
  UserPickerChangePayload,
  UserPickerMode,
  UserPickerValue,
} from './types';

import { computed, onMounted, ref, watch } from 'vue';

import { Button, Input, Modal, Tag } from 'ant-design-vue';

import { listPickerUsers } from '#/api/system/picker';

import UserPickerPanel from './UserPickerPanel.vue';

interface Props {
  disabled?: boolean;
  excludeUserIds?: string[];
  maxCount?: number;
  mode?: UserPickerMode;
  orgOnly?: boolean;
  placeholder?: string;
  value?: UserPickerValue;
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  excludeUserIds: () => [],
  maxCount: undefined,
  mode: 'single',
  orgOnly: false,
  placeholder: '请选择人员',
  value: undefined,
});

const emit = defineEmits<{
  change: [value: UserPickerValue, payload: UserPickerChangePayload];
  'update:value': [value: UserPickerValue];
}>();

const open = ref(false);
const allUsers = ref<UserInfo[]>([]);
const selectedUsers = ref<UserInfo[]>([]);
const draftSelectedUsers = ref<UserInfo[]>([]);

const isMultiple = computed(() => props.mode === 'multiple');
const selectedLabels = computed(() => selectedUsers.value.map(formatUserLabel));
const displayText = computed(() => selectedLabels.value.join('、'));

watch(
  () => props.value,
  () => {
    syncSelectedUsersFromValue();
  },
  { deep: true },
);

onMounted(async () => {
  allUsers.value = await listPickerUsers();
  syncSelectedUsersFromValue();
});

function normalizeValue(value: UserPickerValue) {
  if (Array.isArray(value)) {
    return value.filter(Boolean);
  }
  return value ? [value] : [];
}

function toEmitValue(users: UserInfo[]): UserPickerValue {
  const ids = users.map((user) => user.id).filter(Boolean) as string[];
  return isMultiple.value ? ids : ids[0];
}

function syncSelectedUsersFromValue() {
  const ids = normalizeValue(props.value);
  selectedUsers.value = ids
    .map((id) => allUsers.value.find((user) => user.id === id) || ({ id } as UserInfo))
    .filter((user) => user.id);
}

function formatUserLabel(user: UserInfo) {
  const displayName = user.realname || user.username || user.id || '';
  return user.workNo ? `${displayName}（${user.workNo}）` : displayName;
}

function handleOpen() {
  if (props.disabled) {
    return;
  }
  draftSelectedUsers.value = [...selectedUsers.value];
  open.value = true;
}

function handleClear() {
  selectedUsers.value = [];
  emitChange([]);
}

function handleConfirm() {
  selectedUsers.value = [...draftSelectedUsers.value];
  emitChange(selectedUsers.value);
  open.value = false;
}

function emitChange(users: UserInfo[]) {
  const value = toEmitValue(users);
  emit('update:value', value);
  emit('change', value, {
    users,
    value,
  });
}
</script>

<template>
  <div class="user-picker">
    <Input
      :disabled="disabled"
      :placeholder="placeholder"
      :value="displayText"
      readonly
      @click="handleOpen"
    >
      <template #suffix>
        <Button
          v-if="selectedUsers.length > 0 && !disabled"
          size="small"
          type="link"
          @click.stop="handleClear"
        >
          清空
        </Button>
      </template>
    </Input>
    <div
      v-if="isMultiple && selectedUsers.length > 0"
      class="user-picker-tags"
    >
      <Tag
        v-for="user in selectedUsers"
        :key="user.id"
      >
        {{ formatUserLabel(user) }}
      </Tag>
    </div>

    <Modal
      v-model:open="open"
      :destroy-on-close="false"
      :width="960"
      cancel-text="取消"
      ok-text="确定"
      title="选择人员"
      wrap-class-name="user-picker-modal-wrap"
      @ok="handleConfirm"
    >
      <UserPickerPanel
        v-model:selected-users="draftSelectedUsers"
        :exclude-user-ids="excludeUserIds"
        :max-count="maxCount"
        :mode="mode"
        :org-only="orgOnly"
      />
    </Modal>
  </div>
</template>

<style scoped>
.user-picker {
  width: 100%;
}

.user-picker :deep(.ant-input) {
  cursor: pointer;
}

.user-picker-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

:global(.user-picker-modal-wrap .ant-modal-body) {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:global(.user-picker-modal-wrap .ant-modal-body > .user-picker-panel) {
  flex: 1;
}

:global(.user-picker-modal-wrap .ant-modal) {
  max-width: 960px;
  top: 0;
}

:global(.user-picker-modal-wrap .ant-modal-content) {
  display: flex;
  flex-direction: column;
  height: 640px;
}

:global(.user-picker-modal-wrap) {
  align-items: center;
  display: flex;
  justify-content: center;
}
</style>
