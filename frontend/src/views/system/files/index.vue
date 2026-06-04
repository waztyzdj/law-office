<script setup lang="ts">
import type {
  DocumentFileInfo,
  DocumentScope,
} from '#/api/system/document';
import type {
  CurrentUserOrganization,
  CurrentUserTenant,
} from '#/api/system/user';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Card,
  Dropdown,
  Input,
  InputSearch,
  Menu,
  Radio,
  Spin,
  Tag,
  Tree,
} from 'ant-design-vue';

import {
  pageDocuments,
} from '#/api/system/document';
import {
  getCurrentUserOrganization,
  getCurrentUserTenantOptions,
} from '#/api/system/user';

import DocumentExplorer from './components/DocumentExplorer.vue';
import DocumentHistoryModal from './components/DocumentHistoryModal.vue';
import DocumentImagePreviewModal from './components/DocumentImagePreviewModal.vue';
import DocumentItemActionMenu from './components/DocumentItemActionMenu.vue';
import DocumentOnlyOfficePreviewModal from './components/DocumentOnlyOfficePreviewModal.vue';
import DocumentShareDrawer from './components/DocumentShareDrawer.vue';
import {
  BUSINESS_RECORD_VIEW_STORE_TYPE,
  BUSINESS_VIEW_STORE_TYPE,
  DOCUMENT_UPLOAD_ACCEPT,
} from './constants';
import {
  buildDepartScopeOptions,
} from './tree';
import { useDocumentNavigation } from './hooks/useDocumentNavigation';
import { useDocumentActions } from './hooks/useDocumentActions';
import { useDocumentSort } from './hooks/useDocumentSort';
import { useDocumentTree } from './hooks/useDocumentTree';
import { useDocumentTreeInteractions } from './hooks/useDocumentTreeInteractions';
import type {
  DocumentBatchAction,
  ScopeOption,
} from './types';

const PAGE_SIZE = 500;

const loading = ref(false);
const keyword = ref('');
const dataSource = ref<DocumentFileInfo[]>([]);
const {
  currentDocumentSortLabel,
  documentSortOptions,
  documentSortState,
  documentViewMode,
  documentViewModeModel,
  handleChangeDocumentSort,
  handleChangeDocumentSortField,
  handleChangeDocumentSortOrder,
  isActiveDocumentSort,
} = useDocumentSort();
const currentDeparts = ref<CurrentUserOrganization['departs']>([]);
const currentTenant = ref<CurrentUserTenant>();
const shareDrawerRef = ref<InstanceType<typeof DocumentShareDrawer>>();
const historyModalRef = ref<InstanceType<typeof DocumentHistoryModal>>();
const imagePreviewModalRef = ref<InstanceType<typeof DocumentImagePreviewModal>>();
const previewModalRef = ref<InstanceType<typeof DocumentOnlyOfficePreviewModal>>();
const {
  activeRootKey,
  canGoBack,
  canGoParent,
  clearTreeRenameTimer,
  currentFolder,
  currentParentId,
  handleGoBack,
  handleGoBreadcrumb,
  handleGoParent,
  handleGoRoot,
  handleOpenFolder,
  handleSelectTree,
  parentStack,
  pushNavigationHistory,
  setTreeNavigationOptions,
} = useDocumentNavigation({
  activateTreeShortcut: () => {
    activateTreeShortcut();
  },
  cancelInlineEditor,
  canManageTreeFolder,
  isEditingTreeNode,
  isGlobalSearch: () => isGlobalSearch.value,
  loadData,
  renameFolder: handleRenameFolder,
  resetAndLoad,
});
const scopeOptions = computed<ScopeOption[]>(() => {
  const departChildren = buildDepartScopeOptions(currentDeparts.value);

  const roots: ScopeOption[] = [
    {
      icon: 'lucide:folder',
      key: 'my',
      scope: 'my',
      title: '我的文档',
    },
    {
      icon: 'lucide:briefcase-business',
      key: 'business',
      scope: 'business',
      title: '业务文档',
    },
    {
      icon: 'lucide:building',
      key: 'tenant',
      scope: 'shared',
      shareTargetId: currentTenant.value?.id,
      shareTargetType: 'tenant',
      title: '租户共享',
    },
  ];

  if (departChildren.length > 0) {
    roots.push({
      children: departChildren,
      icon: 'lucide:network',
      key: 'depart',
      selectable: false,
      title: '部门共享',
    });
  }

  roots.push(
    { icon: 'lucide:users', key: 'shared', scope: 'shared', title: '共享给我' },
    { icon: 'lucide:share-2', key: 'sharedByMe', scope: 'sharedByMe', title: '我的共享' },
    { icon: 'lucide:star', key: 'starred', scope: 'starred', title: '我的收藏' },
    { icon: 'lucide:trash-2', key: 'trash', scope: 'trash', title: '回收站' },
  );

  return roots;
});
const {
  ensureFolderTreePathLoaded,
  expandedTreeKeys,
  expandPathKeys,
  findCachedPath,
  findFolderByKey,
  findScopeOption,
  folderTree,
  folderTreeCache,
  getActiveSelectedTreeKey,
  getSelectedTreeKey,
  getTreeNodeIcon,
  handleTreeExpand,
  loadFolderTree,
  loadInitialFolderTrees,
  reloadCachedFolderTrees,
  selectedTreeKeys,
  treeData,
  treeLoading,
} = useDocumentTree({
  activeRootKey,
  currentParentId,
  fetchDocuments,
  scopeOptions,
});
setTreeNavigationOptions({
  ensureFolderTreePathLoaded,
  expandedTreeKeys,
  expandPathKeys,
  findCachedPath,
  findFolderByKey,
  findScopeOption,
  folderTree,
  folderTreeCache,
  getActiveSelectedTreeKey,
  getSelectedTreeKey,
  loadFolderTree,
  selectedTreeKeys,
});
const activeScopeOption = computed(
  () => findScopeOption(activeRootKey.value) || scopeOptions.value[0],
);
const scope = computed<DocumentScope>(() => activeScopeOption.value?.scope || 'my');
const isGlobalSearch = computed(() => keyword.value.trim().length > 0);
const canManageCurrentScope = computed(() => scope.value === 'my');
const isSharedInboxScope = computed(
  () => scope.value === 'shared' && !activeScopeOption.value?.shareTargetType,
);
const isBusinessScope = computed(() => scope.value === 'business');
const documentActionContext = computed(() => ({
  personalizeShared: isSharedInboxScope.value,
  scope: scope.value,
}));
const activeSharedTarget = computed(() => {
  const option = activeScopeOption.value;
  if (
    scope.value === 'shared' &&
    (option?.shareTargetType === 'tenant' || option?.shareTargetType === 'depart') &&
    option.shareTargetId
  ) {
    return {
      targetId: option.shareTargetId,
      targetType: option.shareTargetType,
    };
  }
  return undefined;
});
const canCreateCurrentScope = computed(
  () =>
    !isGlobalSearch.value &&
    (canManageCurrentScope.value ||
      (isBusinessScope.value && canCreateInsideFolder(currentFolder.value)) ||
      scope.value === 'sharedByMe' ||
      (scope.value === 'starred' && Boolean(currentFolder.value?.ownerFlag)) ||
      (isSharedInboxScope.value &&
        (!currentFolder.value ||
          (Boolean(currentFolder.value.ownerFlag) &&
            currentFolder.value.storeType === 'shared_view'))) ||
      (Boolean(activeSharedTarget.value) && (!currentFolder.value || Boolean(currentFolder.value.ownerFlag)))),
);
const canUploadCurrentScope = computed(
  () =>
    !isGlobalSearch.value &&
    !isBusinessScope.value &&
    !isSharedInboxScope.value &&
    (canManageCurrentScope.value ||
      scope.value === 'sharedByMe' ||
      (scope.value === 'starred' && Boolean(currentFolder.value?.ownerFlag)) ||
      (Boolean(activeSharedTarget.value) && (!currentFolder.value || Boolean(currentFolder.value.ownerFlag)))),
);
const currentScopeTitle = computed(
  () => activeScopeOption.value?.title || '根目录',
);
const currentFolderTitle = computed(
  () => (isGlobalSearch.value ? '搜索结果' : parentStack.value.at(-1)?.fileName || currentScopeTitle.value),
);
const inlineFileName = computed({
  get: () => inlineEditor.value?.fileName || '',
  set: (value: string) => handleInlineNameChange(value),
});

function canManageFolder(record?: DocumentFileInfo) {
  if (isBusinessScope.value) {
    return (
      Boolean(record?.id) &&
      Boolean(record?.ownerFlag) &&
      record?.storeType === BUSINESS_VIEW_STORE_TYPE
    );
  }
  return (
    scope.value !== 'trash' &&
    Boolean(record?.id) &&
    Boolean(record?.ownerFlag)
  );
}

function canCreateInsideFolder(record?: DocumentFileInfo) {
  if (!record?.id || record.izFolder !== '1') {
    return false;
  }
  if (isBusinessScope.value) {
    return (
      record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE ||
      (Boolean(record.ownerFlag) && record.storeType === BUSINESS_VIEW_STORE_TYPE)
    );
  }
  return canManageFolder(record);
}

const documentActions = useDocumentActions({
  activeScopeOption,
  activeSharedTarget,
  canCreateCurrentScope,
  canDropToTreeTarget,
  canManageFolder,
  canUploadCurrentScope,
  currentParentId,
  dataSource,
  handleOpenFolder,
  historyModalRef,
  imagePreviewModalRef,
  isGlobalSearch,
  loadData,
  previewModalRef,
  reloadAll,
  reloadTrashData: async () => {
    await Promise.all([loadData(), reloadCachedFolderTrees()]);
  },
  scope,
  shareDrawerRef,
});
const {
  canPasteCurrentScope,
  cuttingDocumentIds,
  inlineEditor,
  moving,
  savingName,
  uploading,
} = documentActions;

function cancelInlineEditor() {
  documentActions.cancelInlineEditor();
}

function handleAction(event: string, record: DocumentFileInfo) {
  documentActions.handleAction(event, record);
}

function handleBatchAction(event: DocumentBatchAction, records: DocumentFileInfo[]) {
  documentActions.handleBatchAction(event, records);
}

function handleBatchMove(sourceIds: string[], targetParentId?: string) {
  void documentActions.handleBatchMove(sourceIds, targetParentId);
}

function handleClearTrash() {
  documentActions.handleClearTrash();
}

function handleCreateFolder(parentId?: string) {
  documentActions.handleCreateFolder(parentId);
}

function handleDeleteFolder(record?: DocumentFileInfo) {
  documentActions.handleDeleteFolder(record);
}

function handleFilesSelected(event: Event) {
  void documentActions.handleFilesSelected(event);
}

function handleInlineNameChange(value: string) {
  documentActions.handleInlineNameChange(value);
}

function handleMove(sourceId: string, targetParentId?: string) {
  void documentActions.handleMove(sourceId, targetParentId);
}

function handlePaste() {
  void documentActions.handlePaste();
}

function handlePasteToTreeFolder(record?: DocumentFileInfo) {
  void documentActions.handlePasteToTreeFolder(record);
}

function handleRenameFolder(record?: DocumentFileInfo) {
  documentActions.handleRenameFolder(record);
}

function handleUploadClick() {
  documentActions.handleUploadClick();
}

function setFileInputRef(element: unknown) {
  documentActions.fileInputRef.value = element instanceof HTMLInputElement ? element : undefined;
}

function rememberDocumentClipboard(
  mode: Extract<DocumentBatchAction, 'copy' | 'cut'>,
  records: DocumentFileInfo[],
) {
  documentActions.rememberDocumentClipboard(mode, records);
}

function submitInlineName() {
  void documentActions.submitInlineName();
}

const documentTreeInteractions = useDocumentTreeInteractions({
  activeRootKey,
  canCreateInsideFolder,
  canManageFolder,
  documentActionContext,
  ensureFolderTreePathLoaded,
  expandPathKeys,
  findCachedPath,
  findFolderByKey,
  getActiveSelectedTreeKey,
  handleBatchAction,
  handleBatchMove,
  handleCreateFolder,
  handleDeleteFolder,
  handleMove,
  handlePasteToTreeFolder,
  handleRenameFolder,
  inlineEditor,
  isBusinessScope,
  isSharedInboxScope,
  loadData,
  loading,
  moving,
  parentStack,
  pushNavigationHistory,
  rememberDocumentClipboard,
  scope,
  selectedTreeKeys,
  treeLoading,
});
function activateTreeShortcut() {
  documentTreeInteractions.activateTreeShortcut();
}

function canDropToTreeTarget(key: string) {
  return documentTreeInteractions.canDropToTreeTarget(key);
}

function canManageTreeFolder(key: string) {
  return documentTreeInteractions.canManageTreeFolder(key);
}

function canShowTreeContextMenu(key: string) {
  return documentTreeInteractions.canShowTreeContextMenu(key);
}

function deactivateTreeShortcut() {
  documentTreeInteractions.deactivateTreeShortcut();
}

function getTreeCopyableRecords(record?: DocumentFileInfo) {
  return documentTreeInteractions.getTreeCopyableRecords(record);
}

function getTreeCuttableRecords(record?: DocumentFileInfo) {
  return documentTreeInteractions.getTreeCuttableRecords(record);
}

function getTreeDeletableRecords(record?: DocumentFileInfo) {
  return documentTreeInteractions.getTreeDeletableRecords(record);
}

function getTreeDownloadableRecords(record?: DocumentFileInfo) {
  return documentTreeInteractions.getTreeDownloadableRecords(record);
}

function handleCreateFolderIn(record?: DocumentFileInfo) {
  void documentTreeInteractions.handleCreateFolderIn(record);
}

function handleDropToTree(event: DragEvent, targetKey: string) {
  documentTreeInteractions.handleDropToTree(event, targetKey);
}

function handleTreeDragOver(event: DragEvent, targetKey: string) {
  documentTreeInteractions.handleTreeDragOver(event, targetKey);
}

function handleTreeDragStart(event: DragEvent, key: string) {
  documentTreeInteractions.handleTreeDragStart(event, key);
}

function handleTreeMenuBatchAction(event: DocumentBatchAction, record?: DocumentFileInfo) {
  documentTreeInteractions.handleTreeMenuBatchAction(event, record);
}

function handleTreeShortcutKeydown(event: KeyboardEvent) {
  documentTreeInteractions.handleTreeShortcutKeydown(event);
}

function isEditingTreeNode(key: string) {
  return documentTreeInteractions.isEditingTreeNode(key);
}

async function fetchDocuments(
  parentId?: string,
  searchKeyword?: string,
  option: ScopeOption | undefined = activeScopeOption.value,
) {
  const records: DocumentFileInfo[] = [];
  let pageNum = 1;
  let total = 0;
  const normalizedKeyword = searchKeyword?.trim();
  const globalSearch = Boolean(normalizedKeyword);

  do {
    const result = await pageDocuments({
      keyword: normalizedKeyword || undefined,
      pageNum,
      pageSize: PAGE_SIZE,
      parentId: globalSearch ? undefined : parentId,
      scope: globalSearch ? 'all' : option?.scope || scope.value,
      shareTargetId: globalSearch ? undefined : option?.shareTargetId,
      shareTargetType: globalSearch ? undefined : option?.shareTargetType,
    });
    const pageRecords = result.records || [];
    records.push(...pageRecords);
    total = result.total || records.length;
    pageNum += 1;
  } while (records.length < total && pageNum < 20);

  return records;
}

async function loadData() {
  loading.value = true;
  try {
    dataSource.value = await fetchDocuments(currentParentId.value, keyword.value);
  } finally {
    loading.value = false;
  }
}

async function reloadAll() {
  await Promise.all([loadData(), reloadCachedFolderTrees()]);
}

function resetAndLoad() {
  void loadData();
}

function handleSearch(value: string) {
  keyword.value = value;
  resetAndLoad();
}

async function loadShareRootContext() {
  const [organization, tenants] = await Promise.all([
    getCurrentUserOrganization(),
    getCurrentUserTenantOptions(),
  ]);
  const departMap = new Map<string, CurrentUserOrganization['departs'][number]>();
  for (const depart of organization.departs || []) {
    if (depart.id) {
      departMap.set(depart.id, depart);
    }
  }
  currentDeparts.value = [...departMap.values()];
  currentTenant.value = tenants.find((item) => item.current) || tenants[0];
}

onMounted(async () => {
  window.addEventListener('keydown', handleTreeShortcutKeydown, true);
  await loadShareRootContext();
  await Promise.all([loadData(), loadInitialFolderTrees()]);
});

onBeforeUnmount(() => {
  clearTreeRenameTimer();
  window.removeEventListener('keydown', handleTreeShortcutKeydown, true);
});
</script>

<template>
  <div class="document-center">
    <div class="document-shell">
      <Card
        class="document-tree-card"
        :body-style="{ padding: '12px' }"
        @mousedown.capture="activateTreeShortcut"
      >
        <InputSearch
          v-model:value="keyword"
          class="document-tree-search"
          allow-clear
          placeholder="搜索文件名"
          @search="handleSearch"
        />
        <Spin :spinning="treeLoading">
          <Tree
            v-model:expanded-keys="expandedTreeKeys"
            block-node
            :selected-keys="selectedTreeKeys"
            :tree-data="treeData"
            @expand="handleTreeExpand"
            @select="handleSelectTree"
          >
            <template #title="{ key, title }">
              <Dropdown
                v-if="canShowTreeContextMenu(String(key))"
                :trigger="['contextmenu']"
              >
                <span
                  class="document-tree-node"
                  :class="{ 'document-tree-node--draggable': canManageTreeFolder(String(key)) }"
                  :draggable="canManageTreeFolder(String(key))"
                  @dragstart="handleTreeDragStart($event, String(key))"
                  @dragover="handleTreeDragOver($event, String(key))"
                  @drop="handleDropToTree($event, String(key))"
                >
                  <IconifyIcon
                    :icon="getTreeNodeIcon(String(key))"
                    class="document-tree-node__icon"
                  />
                  <Input
                    v-if="isEditingTreeNode(String(key))"
                    v-model:value="inlineFileName"
                    class="document-tree-node__input"
                    size="small"
                    @blur="submitInlineName"
                    @click.stop
                    @keydown.esc.stop.prevent="cancelInlineEditor"
                    @press-enter="submitInlineName"
                  />
                  <span v-else>{{ title }}</span>
                </span>
                <template #overlay>
                  <DocumentItemActionMenu
                    :can-edit="canManageTreeFolder(String(key))"
                    :context-copyable-count="getTreeCopyableRecords(findFolderByKey(String(key))).length"
                    :context-cuttable-count="getTreeCuttableRecords(findFolderByKey(String(key))).length"
                    :context-deletable-count="getTreeDeletableRecords(findFolderByKey(String(key))).length"
                    :context-downloadable-count="getTreeDownloadableRecords(findFolderByKey(String(key))).length"
                    :record="findFolderByKey(String(key))"
                    :scope="scope"
                    @action="handleAction"
                    @batch-action="handleTreeMenuBatchAction($event, findFolderByKey(String(key)))"
                  />
                </template>
              </Dropdown>
              <span
                v-else
                class="document-tree-node"
                @dragover="handleTreeDragOver($event, String(key))"
                @drop="handleDropToTree($event, String(key))"
              >
                <IconifyIcon
                  :icon="getTreeNodeIcon(String(key))"
                  class="document-tree-node__icon"
                />
                <Input
                  v-if="isEditingTreeNode(String(key))"
                  v-model:value="inlineFileName"
                  class="document-tree-node__input"
                  size="small"
                  @blur="submitInlineName"
                  @click.stop
                  @keydown.esc.stop.prevent="cancelInlineEditor"
                  @press-enter="submitInlineName"
                />
                <span v-else>{{ title }}</span>
              </span>
            </template>
          </Tree>
        </Spin>
      </Card>

      <Card
        class="document-content-card"
        :body-style="{ padding: '16px' }"
        @mousedown.capture="deactivateTreeShortcut"
      >
        <div class="document-header">
          <div class="document-header__main">
            <div class="document-header__nav">
              <Button :disabled="!canGoBack" size="small" type="text" @click="handleGoBack">
                <template #icon>
                  <IconifyIcon icon="lucide:arrow-left" />
                </template>
                后退
              </Button>
              <Button :disabled="!canGoParent" size="small" type="text" @click="handleGoParent">
                <template #icon>
                  <IconifyIcon icon="lucide:arrow-up" />
                </template>
                返回上一级
              </Button>
            </div>
            <div class="document-path-title">{{ currentFolderTitle }}</div>
            <Breadcrumb class="document-path-breadcrumb">
              <BreadcrumbItem v-if="isGlobalSearch">
                全局搜索：{{ keyword.trim() }}
              </BreadcrumbItem>
              <BreadcrumbItem v-else>
                <a @click="handleGoRoot">{{ currentScopeTitle }}</a>
              </BreadcrumbItem>
              <template v-if="!isGlobalSearch">
                <BreadcrumbItem
                  v-for="(item, index) in parentStack"
                  :key="item.id"
                >
                  <a @click="handleGoBreadcrumb(index)">{{ item.fileName }}</a>
                </BreadcrumbItem>
              </template>
            </Breadcrumb>
            <span class="document-header__count">{{ dataSource.length }} 项</span>
            <Tag v-if="scope === 'trash'" color="red">回收站</Tag>
          </div>
          <div class="document-header__actions">
            <Dropdown trigger="click">
              <Button class="document-sort-button" size="small" type="text">
                <template #icon>
                  <IconifyIcon icon="lucide:arrow-up-down" />
                </template>
                {{ currentDocumentSortLabel }}{{ documentSortState.order === 'asc' ? '升序' : '降序' }}
              </Button>
              <template #overlay>
                <Menu>
                  <Menu.Item
                    v-for="option in documentSortOptions"
                    :key="`toolbar-${option.field}`"
                    @click="handleChangeDocumentSortField(option.field)"
                  >
                    <IconifyIcon
                      v-if="isActiveDocumentSort(option.field)"
                      class="document-menu-icon"
                      icon="lucide:check"
                    />
                    <span v-else class="document-menu-icon document-menu-icon--placeholder" />
                    {{ option.label }}
                  </Menu.Item>
                  <Menu.Divider />
                  <Menu.Item key="toolbar-sort-asc" @click="handleChangeDocumentSortOrder('asc')">
                    <IconifyIcon
                      v-if="documentSortState.order === 'asc'"
                      class="document-menu-icon"
                      icon="lucide:check"
                    />
                    <span v-else class="document-menu-icon document-menu-icon--placeholder" />
                    升序
                  </Menu.Item>
                  <Menu.Item key="toolbar-sort-desc" @click="handleChangeDocumentSortOrder('desc')">
                    <IconifyIcon
                      v-if="documentSortState.order === 'desc'"
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
                v-model:value="documentViewModeModel"
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
              :disabled="dataSource.length === 0"
              type="primary"
              @click="handleClearTrash"
            >
              <template #icon>
                <IconifyIcon icon="lucide:trash-2" />
              </template>
              清空回收站
            </Button>
            <Button
              v-if="canCreateCurrentScope"
              :loading="moving"
              type="primary"
              @click="handleCreateFolder()"
            >
              <template #icon>
                <IconifyIcon icon="lucide:folder-plus" />
              </template>
              新建文件夹
            </Button>
            <Button
              v-if="canUploadCurrentScope"
              :loading="uploading"
              type="primary"
              @click="handleUploadClick"
            >
              <template #icon>
                <IconifyIcon icon="lucide:upload" />
              </template>
              上传文件
            </Button>
          </div>
        </div>

        <DocumentExplorer
          :current-folder="currentFolder"
          :data-source="dataSource"
          :inline-editor="inlineEditor"
          :loading="loading || uploading"
          :moving="moving"
          :saving-name="savingName"
          :can-create="canCreateCurrentScope"
          :can-paste="canPasteCurrentScope"
          :can-upload="canUploadCurrentScope"
          :cutting-ids="cuttingDocumentIds"
          :personalize-shared="isSharedInboxScope"
          :scope="scope"
          :sort-state="documentSortState"
          :view-mode="documentViewMode"
          @action="handleAction"
          @batch-action="handleBatchAction"
          @batch-move="handleBatchMove"
          @create-folder="handleCreateFolder"
          @create-folder-in="handleCreateFolderIn"
          @inline-cancel="cancelInlineEditor"
          @inline-change="handleInlineNameChange"
          @inline-submit="submitInlineName"
          @move="handleMove"
          @paste="handlePaste"
          @sort-change="handleChangeDocumentSort"
          @upload="handleUploadClick"
        />
      </Card>
    </div>

    <input
      :ref="setFileInputRef"
      :accept="DOCUMENT_UPLOAD_ACCEPT"
      class="hidden-file-input"
      multiple
      type="file"
      @change="handleFilesSelected"
    />
    <DocumentShareDrawer ref="shareDrawerRef" @success="reloadAll" />
    <DocumentHistoryModal
      ref="historyModalRef"
      @preview="(version) => previewModalRef?.openHistoryVersion(version)"
      @restored="reloadAll"
    />
    <DocumentImagePreviewModal ref="imagePreviewModalRef" />
    <DocumentOnlyOfficePreviewModal ref="previewModalRef" />
  </div>
</template>

<style scoped>
.document-menu-icon {
  display: inline-flex;
  width: 16px;
  margin-right: 8px;
  vertical-align: -2px;
}

.document-menu-icon--active {
  color: hsl(var(--primary));
}

.document-menu-icon--placeholder {
  flex: 0 0 auto;
}

.document-center {
  box-sizing: border-box;
  display: flex;
  height: calc(100vh - 88px);
  flex-direction: column;
  padding: 16px;
}

.document-shell {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
}

.document-tree-card,
.document-content-card {
  display: flex;
  overflow: hidden;
  min-height: 0;
  flex-direction: column;
}

.document-tree-search {
  width: 100%;
  margin-bottom: 12px;
}

.document-tree-card :deep(.ant-tree-treenode) {
  align-items: center;
  min-height: 28px;
}

.document-tree-card :deep(.ant-tree-switcher) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 28px;
  line-height: 28px;
}

.document-tree-card :deep(.ant-tree-indent-unit) {
  width: 22px;
}

.document-tree-card :deep(.ant-tree-node-content-wrapper) {
  display: inline-flex;
  align-items: center;
  cursor: default;
  min-height: 28px;
  line-height: 28px;
}

.document-tree-card :deep(.ant-tree-title) {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  width: 100%;
}

.document-tree-node {
  display: inline-flex;
  align-items: center;
  cursor: default;
  gap: 6px;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-tree-node--draggable {
  cursor: default;
}

.document-tree-node--draggable:active {
  cursor: default;
}

.document-tree-node__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
  line-height: 1;
}

.document-tree-node__input {
  width: 150px;
}

.document-content-card :deep(.ant-card-body) {
  display: flex;
  overflow: hidden;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.document-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.document-header__main {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.document-header__nav {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.document-path-title {
  flex: 0 0 auto;
  font-weight: 600;
  font-size: 16px;
}

.document-path-breadcrumb,
.document-header__count {
  flex: 0 1 auto;
  min-width: 0;
  color: hsl(var(--muted-foreground));
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

.hidden-file-input {
  display: none;
}

.document-center :deep(.ant-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.document-center :deep(.ant-btn .ant-btn-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.document-center :deep(.ant-btn .ant-btn-icon svg) {
  display: block;
}

@media (max-width: 900px) {
  .document-shell {
    grid-template-columns: 1fr;
  }

  .document-header {
    align-items: stretch;
    flex-direction: column;
  }

  .document-header__actions {
    justify-content: flex-start;
  }

  .document-tree-card {
    max-height: 260px;
    overflow: auto;
  }
}

</style>
