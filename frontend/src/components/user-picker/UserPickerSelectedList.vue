<script setup lang="ts">
import type { UserInfo } from '#/api/system/user';

import { computed } from 'vue';

import { Button, Empty, List } from 'ant-design-vue';

import { IconifyIcon } from '@vben/icons';

interface Props {
  disabled?: boolean;
  users?: UserInfo[];
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  users: () => [],
});

const emit = defineEmits<{
  clear: [];
  remove: [userId: string];
}>();

const selectedUsers = computed(() => props.users.filter((user) => user.id));

function formatUserLabel(user: UserInfo) {
  const displayName = user.realname || user.username || user.id || '';
  return user.workNo ? `${displayName}（${user.workNo}）` : displayName;
}
</script>

<template>
  <div class="user-picker-selected">
    <div class="user-picker-selected-header">
      <span>已选人员</span>
      <Button
        v-if="selectedUsers.length > 0"
        :disabled="disabled"
        size="small"
        type="link"
        @click="emit('clear')"
      >
        清空
      </Button>
    </div>
    <div
      v-if="selectedUsers.length > 0"
      class="user-picker-selected-list"
    >
      <List
        :data-source="selectedUsers"
        item-layout="horizontal"
      >
        <template #renderItem="{ item }">
          <List.Item class="user-picker-selected-item">
            <List.Item.Meta>
              <template #title>
                {{ formatUserLabel(item) }}
              </template>
            </List.Item.Meta>
            <Button
              :disabled="disabled"
              class="user-picker-selected-delete"
              danger
              size="small"
              type="primary"
              @click="emit('remove', item.id!)"
            >
              <template #icon>
                <IconifyIcon icon="lucide:trash-2" />
              </template>
            </Button>
          </List.Item>
        </template>
      </List>
    </div>
    <Empty
      v-else
      description="未选择人员"
    />
  </div>
</template>

<style scoped>
.user-picker-selected {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.user-picker-selected-header {
  align-items: center;
  display: flex;
  font-weight: 600;
  height: 32px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.user-picker-selected-list {
  display: block;
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.user-picker-selected-list :deep(.ant-list-items) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-picker-selected :deep(.ant-empty) {
  align-items: center;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: flex-start;
  min-height: 0;
  padding-top: 120px;
}

.user-picker-selected-item {
  border-radius: 6px;
  min-height: 38px;
  padding: 4px 8px;
}

.user-picker-selected-item:hover {
  background: #fff1f0;
}

.user-picker-selected-item :deep(.ant-list-item-meta) {
  align-items: center;
}

.user-picker-selected-item :deep(.ant-list-item-meta-title) {
  margin-bottom: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-picker-selected-delete {
  align-items: center;
  display: inline-flex;
  flex: 0 0 auto;
  justify-content: center;
  padding-inline: 8px;
}
</style>
