<script setup lang="ts">
import type {
  DocumentFileInfo,
  DocumentScope,
} from '#/api/system/document';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import {
  Card,
  Spin,
} from 'ant-design-vue';

import DocumentExplorer from './components/DocumentExplorer.vue';
import DocumentHeaderToolbar from './components/DocumentHeaderToolbar.vue';
import DocumentHistoryModal from './components/DocumentHistoryModal.vue';
import DocumentImagePreviewModal from './components/DocumentImagePreviewModal.vue';
import DocumentOnlyOfficePreviewModal from './components/DocumentOnlyOfficePreviewModal.vue';
import DocumentShareDrawer from './components/DocumentShareDrawer.vue';
import DocumentTreePanel from './components/DocumentTreePanel.vue';
import {
  DOCUMENT_UPLOAD_ACCEPT,
} from './constants';
import {
  buildDepartScopeOptions,
} from './tree';
import { useDocumentNavigation } from './hooks/useDocumentNavigation';
import { useDocumentActions } from './hooks/useDocumentActions';
import { useDocumentDataLoader } from './hooks/useDocumentDataLoader';
import { useDocumentSort } from './hooks/useDocumentSort';
import { useDocumentTree } from './hooks/useDocumentTree';
import { useDocumentTreeInteractions } from './hooks/useDocumentTreeInteractions';
import type {
  DocumentBatchAction,
  ScopeOption,
} from './types';

const {
  currentDocumentSortLabel,
  documentSortOptions,
  documentSortState,
  documentViewMode,
  handleChangeDocumentViewMode,
  handleChangeDocumentSort,
  handleChangeDocumentSortField,
  handleChangeDocumentSortOrder,
} = useDocumentSort();
const shareDrawerRef = ref<InstanceType<typeof DocumentShareDrawer>>();
const historyModalRef = ref<InstanceType<typeof DocumentHistoryModal>>();
const imagePreviewModalRef = ref<InstanceType<typeof DocumentImagePreviewModal>>();
const previewModalRef = ref<InstanceType<typeof DocumentOnlyOfficePreviewModal>>();
const treeInitialized = ref(false);
async function reloadCachedFolderTreesBridge() {
  await reloadCachedFolderTrees();
}
const {
  activeKeyword,
  batchLoadFolderTree,
  clearSearchKeyword: clearDocumentSearchKeyword,
  currentDeparts,
  currentTenant,
  dataSource,
  fetchDocuments,
  handleKeywordChange,
  handleSearch,
  keyword,
  loadData,
  loading,
  loadShareRootContext,
  prefetchFolderTree,
  reloadAll,
  resetAndLoad,
} = useDocumentDataLoader({
  getActiveScopeOption: () => activeScopeOption.value,
  getCurrentParentId: () => currentParentId.value,
  getScope: () => scope.value,
  reloadCachedFolderTrees: reloadCachedFolderTreesBridge,
});
const {
  activeRootKey,
  canGoBack,
  canGoParent,
  currentFolder,
  currentParentId,
  handleGoBack,
  handleGoBreadcrumb,
  handleGoParent,
  handleGoRoot,
  handleOpenFolder: handleNavigationOpenFolder,
  handleSelectTree: handleNavigationSelectTree,
  parentStack,
  pushNavigationHistory,
  resetCurrentRootNavigation,
  setTreeNavigationOptions,
  updateNavigationFolderRecord,
} = useDocumentNavigation({
  activateTreeShortcut: () => {
    activateTreeShortcut();
  },
  cancelInlineEditor,
  isGlobalSearch: () => isGlobalSearch.value,
  loadData,
  resetAndLoad,
});

function clearSearchKeyword() {
  clearDocumentSearchKeyword();
}

async function handleOpenFolder(record: DocumentFileInfo) {
  clearSearchKeyword();
  await handleNavigationOpenFolder(record);
}

async function handleSelectTree(...args: Parameters<typeof handleNavigationSelectTree>) {
  clearSearchKeyword();
  await handleNavigationSelectTree(...args);
}
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
  getActiveSelectedTreeKey,
  getRootKeyFromFolderNodeKey,
  getSelectedTreeKey,
  getTreeNodeIcon,
  handleTreeExpand,
  loadFolderTree,
  loadInitialFolderTrees,
  reloadCachedFolderTrees,
  refreshFolderTreeChildren,
  resolveExistingFolderTreePath,
  selectFolderTreeParent,
  selectedTreeKeys,
  treeData,
  treeLoading,
  treeRenderKey,
  updateCachedFolderTreeRecord,
} = useDocumentTree({
  activeRootKey,
  batchLoadFolderTree,
  currentParentId,
  fetchDocuments,
  prefetchFolderTree,
  scopeOptions,
});
setTreeNavigationOptions({
  ensureFolderTreePathLoaded,
  expandedTreeKeys,
  expandPathKeys,
  findCachedPath,
  findScopeOption,
  getActiveSelectedTreeKey,
  getSelectedTreeKey,
  loadFolderTree,
  resolveExistingFolderTreePath,
  selectedTreeKeys,
});
const activeScopeOption = computed(
  () => findScopeOption(activeRootKey.value) || scopeOptions.value[0],
);
const scope = computed<DocumentScope>(() => activeScopeOption.value?.scope || 'my');
const isGlobalSearch = computed(() => activeKeyword.value.length > 0);
const canManageCurrentScope = computed(() => scope.value === 'my');
const isSharedInboxScope = computed(
  () => scope.value === 'shared' && !activeScopeOption.value?.shareTargetType,
);
const isSharedReadonlyScope = computed(() => scope.value === 'shared' && isSharedInboxScope.value);
const isReadonlyCollectionScope = computed(() => scope.value === 'sharedByMe' || scope.value === 'starred');
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
    !isSharedReadonlyScope.value &&
    !isReadonlyCollectionScope.value &&
    !isBusinessScope.value &&
    (canManageCurrentScope.value ||
      (isSharedInboxScope.value &&
        (!currentFolder.value ||
          (Boolean(currentFolder.value.ownerFlag) &&
            currentFolder.value.storeType === 'shared_view'))) ||
      (Boolean(activeSharedTarget.value) && (!currentFolder.value || Boolean(currentFolder.value.canManage)))),
);
const canUploadCurrentScope = computed(
  () =>
    !isGlobalSearch.value &&
    !isSharedReadonlyScope.value &&
    !isReadonlyCollectionScope.value &&
    !isBusinessScope.value &&
    !isSharedInboxScope.value &&
    (canManageCurrentScope.value ||
      (Boolean(activeSharedTarget.value) && (!currentFolder.value || Boolean(currentFolder.value.canManage)))),
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
  return (
    scope.value !== 'trash' &&
    scope.value !== 'business' &&
    Boolean(record?.id) &&
    Boolean(record?.canManage)
  );
}

function canCreateInsideFolder(record?: DocumentFileInfo) {
  if (!record?.id || record.izFolder !== '1') {
    return false;
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
  refreshFolderTreeChildren,
  resetCurrentRootNavigation,
  selectFolderTreeParent,
  scope,
  shareDrawerRef,
  updateCachedFolderTreeRecord,
  updateNavigationFolderRecord,
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

function handleBatchMove(
  sourceIds: string[],
  targetParentId?: string,
  sourceParentIds?: Array<string | undefined>,
) {
  void documentActions.handleBatchMove(sourceIds, targetParentId, sourceParentIds);
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

function handleMove(sourceId: string, targetParentId?: string, sourceParentId?: string) {
  void documentActions.handleMove(sourceId, targetParentId, sourceParentId);
}

function handlePaste() {
  void documentActions.handlePaste();
}

function handlePasteToTreeFolder(record?: DocumentFileInfo) {
  void documentActions.handlePasteToTreeFolder(record);
}

function handleTreeMove(
  sourceIds: string[],
  sourceParentIds: Array<string | undefined>,
  targetParentId?: string,
) {
  void documentActions.handleTreeMove(sourceIds, sourceParentIds, targetParentId);
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

async function reloadAfterCancelShare() {
  if (scope.value === 'sharedByMe') {
    resetCurrentRootNavigation();
  }
  await reloadAll();
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
  getRootKeyFromFolderNodeKey,
  getScopeOptionByRootKey: (rootKey) => findScopeOption(rootKey),
  getScopeByRootKey: (rootKey) => findScopeOption(rootKey)?.scope,
  handleBatchAction,
  handleCreateFolder,
  handleDeleteFolder,
  handlePasteToTreeFolder,
  handleRenameFolder,
  handleTreeMove,
  inlineEditor,
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

function getTreeNodeScope(key: string) {
  const rootKey = getRootKeyFromFolderNodeKey(key);
  return findScopeOption(rootKey)?.scope || scope.value;
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

onMounted(async () => {
  window.addEventListener('keydown', handleTreeShortcutKeydown, true);
  const treeReadyPromise = (async () => {
    await loadShareRootContext();
    await loadInitialFolderTrees();
    treeInitialized.value = true;
  })();
  await Promise.all([loadData(), treeReadyPromise]);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleTreeShortcutKeydown, true);
});
</script>

<template>
  <div class="document-center">
    <div class="document-shell">
      <Card
        class="document-tree-card"
        :body-style="{ padding: '12px' }"
      >
        <DocumentTreePanel
          v-if="treeInitialized"
          :can-manage-tree-folder="canManageTreeFolder"
          :can-show-tree-context-menu="canShowTreeContextMenu"
          :expanded-keys="expandedTreeKeys"
          :find-folder-by-key="findFolderByKey"
          :get-tree-copyable-records="getTreeCopyableRecords"
          :get-tree-cuttable-records="getTreeCuttableRecords"
          :get-tree-deletable-records="getTreeDeletableRecords"
          :get-tree-downloadable-records="getTreeDownloadableRecords"
          :get-tree-node-icon="getTreeNodeIcon"
          :get-tree-node-scope="getTreeNodeScope"
          :inline-file-name="inlineFileName"
          :is-editing-tree-node="isEditingTreeNode"
          :keyword="keyword"
          :loading="treeLoading"
          :scope="scope"
          :selected-keys="selectedTreeKeys"
          :tree-data="treeData"
          :tree-render-key="treeRenderKey"
          @action="handleAction"
          @activate-shortcut="activateTreeShortcut"
          @batch-action="handleTreeMenuBatchAction"
          @drop-to-tree="handleDropToTree"
          @inline-cancel="cancelInlineEditor"
          @inline-change="handleInlineNameChange"
          @inline-submit="submitInlineName"
          @search="handleSearch"
          @tree-drag-over="handleTreeDragOver"
          @tree-drag-start="handleTreeDragStart"
          @tree-expand="handleTreeExpand"
          @tree-select="handleSelectTree"
          @update-expanded-keys="expandedTreeKeys = $event"
          @update-keyword="handleKeywordChange"
        />
        <div
          v-else
          class="document-tree-initializing"
        >
          <Spin />
        </div>
      </Card>

      <Card
        class="document-content-card"
        :body-style="{ padding: '16px' }"
        @mousedown.capture="deactivateTreeShortcut"
      >
        <DocumentExplorer
          v-if="treeInitialized"
          :current-folder="currentFolder"
          :data-source="dataSource"
          :inline-editor="inlineEditor"
          :is-global-search="isGlobalSearch"
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
        >
          <template #header>
            <DocumentHeaderToolbar
              :can-create="canCreateCurrentScope"
              :can-go-back="canGoBack"
              :can-go-parent="canGoParent"
              :can-upload="canUploadCurrentScope"
              :current-folder-title="currentFolderTitle"
              :current-scope-title="currentScopeTitle"
              :data-count="dataSource.length"
              :is-global-search="isGlobalSearch"
              :keyword="activeKeyword"
              :moving="moving"
              :parent-stack="parentStack"
              :scope="scope"
              :sort-label="currentDocumentSortLabel"
              :sort-options="documentSortOptions"
              :sort-state="documentSortState"
              :uploading="uploading"
              :view-mode="documentViewMode"
              @change-sort-field="handleChangeDocumentSortField"
              @change-sort-order="handleChangeDocumentSortOrder"
              @clear-trash="handleClearTrash"
              @create-folder="handleCreateFolder()"
              @go-back="handleGoBack"
              @go-breadcrumb="handleGoBreadcrumb"
              @go-parent="handleGoParent"
              @go-root="handleGoRoot"
              @upload="handleUploadClick"
              @view-mode-change="handleChangeDocumentViewMode"
            />
          </template>
        </DocumentExplorer>
        <div
          v-else
          class="document-tree-initializing"
        >
          <Spin />
        </div>
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
    <DocumentShareDrawer
      ref="shareDrawerRef"
      @cancel-share="reloadAfterCancelShare"
      @success="reloadAll"
    />
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
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.document-content-card :deep(.ant-card-body) {
  display: flex;
  overflow: hidden;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.document-tree-card :deep(.ant-card-body) {
  display: flex;
  overflow: hidden;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.document-tree-initializing {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  height: 100%;
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

  .document-tree-card {
    max-height: 260px;
  }
}

</style>
