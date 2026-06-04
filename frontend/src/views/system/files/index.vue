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

import {
  Card,
} from 'ant-design-vue';

import {
  pageDocuments,
} from '#/api/system/document';
import {
  getCurrentUserOrganization,
  getCurrentUserTenantOptions,
} from '#/api/system/user';

import DocumentExplorer from './components/DocumentExplorer.vue';
import DocumentHeaderToolbar from './components/DocumentHeaderToolbar.vue';
import DocumentHistoryModal from './components/DocumentHistoryModal.vue';
import DocumentImagePreviewModal from './components/DocumentImagePreviewModal.vue';
import DocumentOnlyOfficePreviewModal from './components/DocumentOnlyOfficePreviewModal.vue';
import DocumentShareDrawer from './components/DocumentShareDrawer.vue';
import DocumentTreePanel from './components/DocumentTreePanel.vue';
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
  handleChangeDocumentViewMode,
  handleChangeDocumentSort,
  handleChangeDocumentSortField,
  handleChangeDocumentSortOrder,
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
      >
        <DocumentTreePanel
          :can-manage-tree-folder="canManageTreeFolder"
          :can-show-tree-context-menu="canShowTreeContextMenu"
          :expanded-keys="expandedTreeKeys"
          :find-folder-by-key="findFolderByKey"
          :get-tree-copyable-records="getTreeCopyableRecords"
          :get-tree-cuttable-records="getTreeCuttableRecords"
          :get-tree-deletable-records="getTreeDeletableRecords"
          :get-tree-downloadable-records="getTreeDownloadableRecords"
          :get-tree-node-icon="getTreeNodeIcon"
          :inline-file-name="inlineFileName"
          :is-editing-tree-node="isEditingTreeNode"
          :keyword="keyword"
          :loading="treeLoading"
          :scope="scope"
          :selected-keys="selectedTreeKeys"
          :tree-data="treeData"
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
          @update-keyword="keyword = $event"
        />
      </Card>

      <Card
        class="document-content-card"
        :body-style="{ padding: '16px' }"
        @mousedown.capture="deactivateTreeShortcut"
      >
        <DocumentHeaderToolbar
          :can-create="canCreateCurrentScope"
          :can-go-back="canGoBack"
          :can-go-parent="canGoParent"
          :can-upload="canUploadCurrentScope"
          :current-folder-title="currentFolderTitle"
          :current-scope-title="currentScopeTitle"
          :data-count="dataSource.length"
          :is-global-search="isGlobalSearch"
          :keyword="keyword"
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

.document-content-card :deep(.ant-card-body) {
  display: flex;
  overflow: hidden;
  min-height: 0;
  flex: 1;
  flex-direction: column;
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
    overflow: auto;
  }
}

</style>
