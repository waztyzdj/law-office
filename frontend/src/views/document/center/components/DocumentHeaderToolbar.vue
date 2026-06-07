<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/document';
import type {
  DocumentSortField,
  DocumentSortOption,
  DocumentSortOrder,
  DocumentSortState,
} from '#/constants/document';
import type { DocumentViewMode } from '../types';

import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Dropdown,
  Menu,
  Radio,
  Tag,
} from 'ant-design-vue';

interface Props {
  canCreate: boolean;
  canGoBack: boolean;
  canGoParent: boolean;
  canUpload: boolean;
  currentFolderTitle: string;
  currentScopeTitle: string;
  dataCount: number;
  isGlobalSearch: boolean;
  keyword: string;
  moving: boolean;
  parentStack: DocumentFileInfo[];
  scope: DocumentScope;
  sortLabel: string;
  sortOptions: DocumentSortOption[];
  sortState: DocumentSortState;
  uploading: boolean;
  viewMode: DocumentViewMode;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  changeSortField: [field: DocumentSortField];
  changeSortOrder: [order: DocumentSortOrder];
  clearTrash: [];
  createFolder: [];
  goBack: [];
  goBreadcrumb: [index: number];
  goParent: [];
  goRoot: [];
  upload: [];
  viewModeChange: [mode: DocumentViewMode];
}>();

const viewModeModel = computed({
  get: () => props.viewMode,
  set: (mode: DocumentViewMode) => emit('viewModeChange', mode),
});

function isActiveSort(field: DocumentSortField) {
  return props.sortState.field === field;
}

const documentPathTitle = computed(() => {
  if (props.isGlobalSearch) {
    return `全局搜索：${props.keyword.trim()}`;
  }

  const names = [props.currentScopeTitle, ...props.parentStack.map((item) => item.fileName || '-')];
  return names.join(' / ');
});
</script>

<template>
  <div class="document-header">
    <div class="document-header__path-row">
      <div class="document-path-title" :title="currentFolderTitle">
        {{ currentFolderTitle }}
      </div>
      <div class="document-path-breadcrumb" :title="documentPathTitle">
        <template v-if="isGlobalSearch">
          <span>全局搜索：{{ keyword.trim() }}</span>
        </template>
        <template v-if="!isGlobalSearch">
          <a :title="currentScopeTitle" @click="emit('goRoot')">{{ currentScopeTitle }}</a>
          <template
            v-for="(item, index) in parentStack"
            :key="item.id"
          >
            <span class="document-path-separator">/</span>
            <a :title="item.fileName" @click="emit('goBreadcrumb', index)">{{ item.fileName }}</a>
          </template>
        </template>
      </div>
    </div>
    <div class="document-header__toolbar-row">
      <div class="document-header__main">
        <div class="document-header__nav">
          <Button :disabled="!canGoBack" size="small" type="text" @click="emit('goBack')">
            <template #icon>
              <IconifyIcon icon="lucide:arrow-left" />
            </template>
            后退
          </Button>
          <Button :disabled="!canGoParent" size="small" type="text" @click="emit('goParent')">
            <template #icon>
              <IconifyIcon icon="lucide:arrow-up" />
            </template>
            返回上一级
          </Button>
        </div>
        <span class="document-header__count">{{ dataCount }} 项</span>
        <Tag v-if="scope === 'trash'" color="red">回收站</Tag>
      </div>
      <div class="document-header__actions">
        <Dropdown trigger="click">
          <Button class="document-sort-button" size="small" type="text">
            <template #icon>
              <IconifyIcon icon="lucide:arrow-up-down" />
            </template>
            {{ sortLabel }}{{ sortState.order === 'asc' ? '升序' : '降序' }}
          </Button>
          <template #overlay>
            <Menu>
              <Menu.Item
                v-for="option in sortOptions"
                :key="`toolbar-${option.field}`"
                @click="emit('changeSortField', option.field)"
              >
                <IconifyIcon
                  v-if="isActiveSort(option.field)"
                  class="document-menu-icon"
                  icon="lucide:check"
                />
                <span v-else class="document-menu-icon document-menu-icon--placeholder" />
                {{ option.label }}
              </Menu.Item>
              <Menu.Divider />
              <Menu.Item key="toolbar-sort-asc" @click="emit('changeSortOrder', 'asc')">
                <IconifyIcon
                  v-if="sortState.order === 'asc'"
                  class="document-menu-icon"
                  icon="lucide:check"
                />
                <span v-else class="document-menu-icon document-menu-icon--placeholder" />
                升序
              </Menu.Item>
              <Menu.Item key="toolbar-sort-desc" @click="emit('changeSortOrder', 'desc')">
                <IconifyIcon
                  v-if="sortState.order === 'desc'"
                  class="document-menu-icon"
                  icon="lucide:check"
                />
                <span v-else class="document-menu-icon document-menu-icon--placeholder" />
                降序
              </Menu.Item>
            </Menu>
          </template>
        </Dropdown>
        <div class="document-view-switch">
          <Radio.Group
            v-model:value="viewModeModel"
            class="document-view-radio"
          >
            <Radio.Button value="list">
              <span class="document-view-radio__item">
                <IconifyIcon icon="lucide:list" />
                列表
              </span>
            </Radio.Button>
            <Radio.Button value="grid">
              <span class="document-view-radio__item">
                <IconifyIcon icon="lucide:grid-2x2" />
                图标
              </span>
            </Radio.Button>
          </Radio.Group>
        </div>
        <Button
          v-if="scope === 'trash'"
          :disabled="dataCount === 0"
          type="primary"
          @click="emit('clearTrash')"
        >
          <template #icon>
            <IconifyIcon icon="lucide:trash-2" />
          </template>
          清空回收站
        </Button>
        <Button
          v-if="canCreate"
          :loading="moving"
          type="primary"
          @click="emit('createFolder')"
        >
          <template #icon>
            <IconifyIcon icon="lucide:folder-plus" />
          </template>
          新建文件夹
        </Button>
        <Button
          v-if="canUpload"
          :loading="uploading"
          type="primary"
          @click="emit('upload')"
        >
          <template #icon>
            <IconifyIcon icon="lucide:upload" />
          </template>
          上传文件
        </Button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.document-menu-icon {
  display: inline-flex;
  width: 16px;
  margin-right: 8px;
  vertical-align: -2px;
}

.document-menu-icon--placeholder {
  flex: 0 0 auto;
}

.document-header {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.document-header__path-row,
.document-header__toolbar-row {
  display: flex;
  min-width: 0;
  width: 100%;
}

.document-header__path-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.document-header__toolbar-row {
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.document-header__main {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-wrap: nowrap;
  align-items: center;
  gap: 10px;
}

.document-header__nav {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.document-path-title {
  overflow: hidden;
  flex: 0 1 auto;
  max-width: 40%;
  min-width: 0;
  font-weight: 600;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-path-breadcrumb,
.document-header__count {
  flex: 0 1 auto;
  min-width: 0;
  color: hsl(var(--muted-foreground));
}

.document-path-breadcrumb {
  overflow: hidden;
  flex: 1 1 auto;
  min-width: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-path-breadcrumb a,
.document-path-breadcrumb span {
  color: inherit;
  white-space: nowrap;
}

.document-path-breadcrumb a {
  cursor: pointer;
}

.document-path-separator {
  margin: 0 8px;
}

.document-header__count {
  flex: 0 0 auto;
  white-space: nowrap;
}

.document-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.document-sort-button {
  color: hsl(var(--muted-foreground));
}

.document-view-switch {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  margin-right: 4px;
}

.document-view-radio {
  display: inline-flex;
  align-items: center;
}

.document-view-radio__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .document-header__main {
    flex-wrap: wrap;
  }

  .document-header__toolbar-row {
    align-items: stretch;
    flex-direction: column;
  }

  .document-path-title,
  .document-path-breadcrumb {
    max-width: 100%;
  }

  .document-header__actions {
    justify-content: flex-start;
  }
}
</style>
