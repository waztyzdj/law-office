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
  Modal,
  Radio,
  Spin,
  Tag,
  Tree,
  message,
} from 'ant-design-vue';

import {
  batchDeleteDocuments,
  batchMoveDocuments,
  clearDocumentTrash,
  copyDocuments,
  createDocumentFolder,
  deleteDocument,
  downloadDocument,
  moveDocument,
  pageDocuments,
  purgeDocument,
  renameDocument,
  restoreDocument,
  shareDocument,
  toggleDocumentStar,
  uploadDocument,
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
  canMove as canMoveDocument,
  isVirtualBusinessItem,
} from './components/documentExplorerUtils';
import {
  BUSINESS_RECORD_VIEW_STORE_TYPE,
  BUSINESS_VIEW_STORE_TYPE,
  DOCUMENT_UPLOAD_ACCEPT,
  IMAGE_PREVIEW_EXTENSIONS,
} from './constants';
import {
  buildDepartScopeOptions,
  getScopeRootKey,
  isScopeRootKey,
} from './tree';
import { useDocumentNavigation } from './hooks/useDocumentNavigation';
import { useDocumentSort } from './hooks/useDocumentSort';
import { useDocumentTree } from './hooks/useDocumentTree';
import type {
  DocumentBatchAction,
  InlineEditorState,
  ScopeOption,
} from './types';

const PAGE_SIZE = 500;

const loading = ref(false);
const uploading = ref(false);
const moving = ref(false);
const savingName = ref(false);
const keyword = ref('');
const dataSource = ref<DocumentFileInfo[]>([]);
const inlineEditor = ref<InlineEditorState>();
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
const treeShortcutActive = ref(false);
const currentDeparts = ref<CurrentUserOrganization['departs']>([]);
const currentTenant = ref<CurrentUserTenant>();
const fileInputRef = ref<HTMLInputElement>();
const shareDrawerRef = ref<InstanceType<typeof DocumentShareDrawer>>();
const historyModalRef = ref<InstanceType<typeof DocumentHistoryModal>>();
const imagePreviewModalRef = ref<InstanceType<typeof DocumentImagePreviewModal>>();
const previewModalRef = ref<InstanceType<typeof DocumentOnlyOfficePreviewModal>>();
const documentClipboard = ref<{
  ids: string[];
  mode: Extract<DocumentBatchAction, 'copy' | 'cut'>;
}>();
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
    treeShortcutActive.value = true;
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
const canPasteCurrentScope = computed(() => {
  const clipboard = documentClipboard.value;
  if (!clipboard || clipboard.ids.length === 0 || isGlobalSearch.value || scope.value === 'trash') {
    return false;
  }
  if (clipboard.mode === 'copy') {
    return canUploadCurrentScope.value;
  }
  return true;
});
const cuttingDocumentIds = computed(() =>
  documentClipboard.value?.mode === 'cut' ? documentClipboard.value.ids : [],
);
const selectedTreeFolder = computed(() => {
  const key = selectedTreeKeys.value[0];
  return key && !isScopeRootKey(key) ? findFolderByKey(key) : undefined;
});
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

function canManageTreeFolder(key: string) {
  const record = findFolderByKey(key);
  return !isScopeRootKey(key) && record?.izFolder === '1' && canManageFolder(record);
}

function canCreateInTreeFolder(key: string) {
  return !isScopeRootKey(key) && canCreateInsideFolder(findFolderByKey(key));
}

function canShowTreeContextMenu(key: string) {
  return canManageTreeFolder(key) || canCreateInTreeFolder(key);
}

function getTreeContextRecords(record?: DocumentFileInfo) {
  return record?.id ? [record] : [];
}

function getTreeCopyableRecords(record?: DocumentFileInfo) {
  return getTreeContextRecords(record).filter(
    (item) => item.id && scope.value !== 'trash' && !isVirtualBusinessItem(item),
  );
}

function getTreeCuttableRecords(record?: DocumentFileInfo) {
  return getTreeContextRecords(record).filter((item) =>
    canMoveDocument(item, documentActionContext.value),
  );
}

function getTreeDownloadableRecords(record?: DocumentFileInfo) {
  return getTreeContextRecords(record).filter((item) => item.canDownload && item.izFolder !== '1');
}

function getTreeDeletableRecords(record?: DocumentFileInfo) {
  return getTreeContextRecords(record).filter(
    (item) => canManageFolder(item) && scope.value !== 'trash' && scope.value !== 'business',
  );
}

function canDropToTreeTarget(key: string) {
  if (scope.value === 'trash') {
    return false;
  }
  if (isScopeRootKey(key)) {
    return !isBusinessScope.value && key === getScopeRootKey(activeRootKey.value);
  }
  const target = findFolderByKey(key);
  if (!target?.id) {
    return false;
  }
  if (isSharedInboxScope.value) {
    return Boolean(target.ownerFlag) && target.storeType === 'shared_view';
  }
  if (isBusinessScope.value) {
    return (
      target.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE ||
      (Boolean(target.ownerFlag) && target.storeType === BUSINESS_VIEW_STORE_TYPE)
    );
  }
  return Boolean(target.ownerFlag);
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

function handleCreateFolder(parentId = currentParentId.value) {
  if (scope.value === 'trash' || !canCreateCurrentScope.value) {
    return;
  }
  inlineEditor.value = {
    fileName: '新建文件夹',
    mode: 'create',
    parentId,
  };
}

async function shareRootFolderIfNeeded(record: DocumentFileInfo, parentId?: string) {
  const target = activeSharedTarget.value;
  if (!record.id || parentId || !target) {
    return;
  }
  await shareDocument({
    enableDown: '1',
    enableUpdat: '0',
    fileId: record.id,
    targets: [
      {
        permission: 'download',
        targetId: target.targetId,
        targetType: target.targetType,
      },
    ],
  });
}

async function handleCreateFolderIn(record?: DocumentFileInfo) {
  if (!record?.id || !canCreateInsideFolder(record)) {
    return;
  }
  pushNavigationHistory();
  parentStack.value = [...parentStack.value, record];
  selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  expandPathKeys(parentStack.value);
  await Promise.all([loadData(), ensureFolderTreePathLoaded(activeRootKey.value, parentStack.value)]);
  handleCreateFolder(record.id);
}

function handleUploadClick() {
  if (!canUploadCurrentScope.value || uploading.value) {
    return;
  }
  fileInputRef.value?.click();
}

async function handleFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (files.length === 0) {
    return;
  }
  uploading.value = true;
  try {
    for (const file of files) {
      const uploaded = await uploadDocument(file, currentParentId.value, {
        scope: scope.value,
        shareTargetType: activeScopeOption.value?.shareTargetType,
      });
      await shareRootFolderIfNeeded(uploaded, currentParentId.value);
    }
    message.success(files.length > 1 ? '文件已上传' : '文件上传成功');
    await loadData();
  } finally {
    uploading.value = false;
  }
}

async function handleDownload(record: DocumentFileInfo) {
  if (!record.id || record.izFolder === '1') {
    return;
  }
  const blob = await downloadDocument(record.id);
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = record.fileName || 'download';
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

function handleRename(record: DocumentFileInfo) {
  const nameParts = splitEditableFileName(record);
  inlineEditor.value = {
    extension: nameParts.extension,
    fileName: nameParts.name,
    mode: 'rename',
    record,
  };
}

function handleRenameFolder(record?: DocumentFileInfo) {
  if (!record?.id || !canManageFolder(record)) {
    return;
  }
  handleRename(record);
}

function handleInlineNameChange(value: string) {
  if (inlineEditor.value) {
    inlineEditor.value.fileName = value;
  }
}

function cancelInlineEditor() {
  inlineEditor.value = undefined;
}

function isEditingTreeNode(key: string) {
  const record = findFolderByKey(key);
  return (
    inlineEditor.value?.mode === 'rename' &&
    inlineEditor.value.record?.id === record?.id
  );
}

async function submitInlineName() {
  if (!inlineEditor.value || savingName.value) {
    return;
  }
  const editor = inlineEditor.value;
  const editableName = editor.fileName.trim();
  if (!editableName) {
    message.warning('请输入名称');
    return;
  }
  const fileName = buildSubmittedFileName(editor, editableName);
  if (fileName.length > 255) {
    message.warning('名称不能超过255个字符');
    return;
  }
  savingName.value = true;
  try {
    if (editor.mode === 'create') {
      const createdFolder = await createDocumentFolder({
        fileName,
        parentId: editor.parentId,
        scope: scope.value,
        shareTargetType: activeScopeOption.value?.shareTargetType,
      });
      await shareRootFolderIfNeeded(createdFolder, editor.parentId);
      message.success('文件夹已创建');
    } else if (editor.record?.id) {
      if (fileName === (editor.record.fileName || '').trim()) {
        cancelInlineEditor();
        return;
      }
      await renameDocument({
        fileName,
        id: editor.record.id,
      });
      message.success('名称已更新');
    }
    cancelInlineEditor();
    await reloadAll();
  } finally {
    savingName.value = false;
  }
}

function splitEditableFileName(record: DocumentFileInfo) {
  const fileName = record.fileName || '';
  if (record.izFolder === '1') {
    return { extension: '', name: fileName };
  }
  const dotIndex = fileName.lastIndexOf('.');
  if (dotIndex <= 0 || dotIndex === fileName.length - 1) {
    return { extension: '', name: fileName };
  }
  return {
    extension: fileName.slice(dotIndex),
    name: fileName.slice(0, dotIndex),
  };
}

function buildSubmittedFileName(editor: InlineEditorState, editableName: string) {
  if (editor.mode === 'rename' && editor.record?.izFolder !== '1') {
    return `${editableName}${editor.extension || ''}`;
  }
  return editableName;
}

function handleShare(record: DocumentFileInfo) {
  shareDrawerRef.value?.open({ record });
}

function handleCancelShare(record: DocumentFileInfo) {
  if (!record.id || !record.sharedFlag || !record.ownerFlag) {
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: `确认取消“${record.fileName || ''}”的全部共享吗？`,
    okButtonProps: { danger: true },
    okText: '取消共享',
    title: '取消共享',
    async onOk() {
      await shareDocument({
        enableDown: '1',
        enableUpdat: '0',
        fileId: record.id || '',
        targets: [],
      });
      message.success('共享已取消');
      await reloadAll();
    },
  });
}

function handleDelete(record: DocumentFileInfo) {
  if (!record.id) {
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: `确认删除“${record.fileName || ''}”吗？删除后可在回收站恢复。`,
    okText: '确定',
    title: '确认删除',
    async onOk() {
      await deleteDocument(record.id || '');
      message.success('已移入回收站');
      await reloadAll();
    },
  });
}

function handleDeleteFolder(record?: DocumentFileInfo) {
  if (!record?.id || !canManageFolder(record)) {
    return;
  }
  handleDelete(record);
}

function handleBatchDelete(fileIds: string[]) {
  const ids = Array.from(new Set(fileIds.filter(Boolean)));
  if (ids.length === 0) {
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: `确认删除选中的 ${ids.length} 个文档吗？删除后可在回收站恢复。`,
    okText: '确定',
    title: '确认删除',
    async onOk() {
      await batchDeleteDocuments(ids);
      message.success('已移入回收站');
      await reloadAll();
    },
  });
}

async function handleBatchDownload(records: DocumentFileInfo[]) {
  const files = records.filter((record) => record.id && record.canDownload && record.izFolder !== '1');
  if (files.length === 0) {
    message.warning('请选择可下载的文件');
    return;
  }
  for (const file of files) {
    await handleDownload(file);
  }
}

async function writeDocumentClipboardText(records: DocumentFileInfo[]) {
  const text = records
    .map((record) => record.fileName)
    .filter((fileName): fileName is string => Boolean(fileName))
    .join('\n');
  if (!text || !navigator.clipboard?.writeText) {
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // 浏览器可能因非安全上下文或权限限制拒绝写入系统剪贴板，不影响文档中心内部剪贴板。
  }
}

function rememberDocumentClipboard(
  mode: Extract<DocumentBatchAction, 'copy' | 'cut'>,
  records: DocumentFileInfo[],
) {
  const uniqueRecords = Array.from(
    new Map(records.filter((record) => record.id).map((record) => [record.id || '', record])).values(),
  );
  if (uniqueRecords.length === 0) {
    return;
  }
  documentClipboard.value = {
    ids: uniqueRecords.map((record) => record.id || ''),
    mode,
  };
  const clipboard = documentClipboard.value;
  void writeDocumentClipboardText(uniqueRecords);
  message.success(`${clipboard.mode === 'copy' ? '已复制' : '已剪切'} ${clipboard.ids.length} 项`);
}

function handleBatchAction(event: DocumentBatchAction, records: DocumentFileInfo[]) {
  if (event === 'download') {
    void handleBatchDownload(records);
    return;
  }
  if (event === 'delete') {
    handleBatchDelete(records.map((record) => record.id || ''));
    return;
  }
  if (event === 'copy' || event === 'cut') {
    rememberDocumentClipboard(event, records);
  }
}

function handleTreeMenuBatchAction(event: DocumentBatchAction, record?: DocumentFileInfo) {
  const records =
    event === 'download'
      ? getTreeDownloadableRecords(record)
      : event === 'delete'
        ? getTreeDeletableRecords(record)
        : event === 'cut'
          ? getTreeCuttableRecords(record)
          : getTreeCopyableRecords(record);
  handleBatchAction(event, records);
}

async function handleRestore(record: DocumentFileInfo) {
  if (!record.id) {
    return;
  }
  await restoreDocument(record.id);
  message.success('文档已恢复');
  await reloadAll();
}

function handlePurge(record: DocumentFileInfo) {
  if (!record.id) {
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: `彻底删除后无法恢复，确认删除“${record.fileName || ''}”吗？`,
    okButtonProps: { danger: true },
    okText: '彻底删除',
    title: '彻底删除',
    async onOk() {
      await purgeDocument(record.id || '');
      message.success('已彻底删除');
      await Promise.all([loadData(), reloadCachedFolderTrees()]);
    },
  });
}

function handleClearTrash() {
  if (scope.value !== 'trash' || dataSource.value.length === 0) {
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: '清空后所有回收站文件都无法恢复，确认清空回收站吗？',
    okText: '清空回收站',
    title: '清空回收站',
    async onOk() {
      await clearDocumentTrash();
      message.success('回收站已清空');
      await reloadAll();
    },
  });
}

async function handleStar(record: DocumentFileInfo) {
  if (!record.id) {
    return;
  }
  await toggleDocumentStar(record.id);
  message.success(record.izStar === '1' ? '已取消收藏' : '已收藏');
  await reloadAll();
}

async function handleMove(sourceId: string, targetParentId?: string) {
  if (!sourceId || moving.value || scope.value === 'trash') {
    return;
  }
  moving.value = true;
  try {
    await moveDocument({
      id: sourceId,
      parentId: targetParentId,
      scope: scope.value,
      shareTargetType: activeScopeOption.value?.shareTargetType,
    });
    message.success('已移动');
    await reloadAll();
  } finally {
    moving.value = false;
  }
}

async function handleBatchMove(sourceIds: string[], targetParentId?: string) {
  const ids = Array.from(new Set(sourceIds.filter(Boolean)));
  if (ids.length === 0 || moving.value || scope.value === 'trash') {
    return;
  }
  moving.value = true;
  try {
    await batchMoveDocuments({
      ids,
      parentId: targetParentId,
      scope: scope.value,
      shareTargetType: activeScopeOption.value?.shareTargetType,
    });
    message.success(`已移动 ${ids.length} 项`);
    await reloadAll();
  } finally {
    moving.value = false;
  }
}

async function handlePaste() {
  const clipboard = documentClipboard.value;
  if (!clipboard || clipboard.ids.length === 0 || moving.value || !canPasteCurrentScope.value) {
    return;
  }
  const targetParentId = currentParentId.value;
  if (clipboard.mode === 'cut') {
    await handleBatchMove(clipboard.ids, targetParentId);
    documentClipboard.value = undefined;
    return;
  }
  moving.value = true;
  try {
    const copiedFiles = await copyDocuments({
      ids: clipboard.ids,
      parentId: targetParentId,
      scope: scope.value,
      shareTargetType: activeScopeOption.value?.shareTargetType,
    });
    for (const copiedFile of copiedFiles) {
      await shareRootFolderIfNeeded(copiedFile, targetParentId);
    }
    message.success(`已粘贴 ${copiedFiles.length} 项`);
    await reloadAll();
  } finally {
    moving.value = false;
  }
}

async function handlePasteToTreeFolder(record?: DocumentFileInfo) {
  const clipboard = documentClipboard.value;
  if (!record?.id || !clipboard || clipboard.ids.length === 0 || moving.value) {
    return;
  }
  if (!canDropToTreeTarget(record.id)) {
    return;
  }
  if (clipboard.ids.includes(record.id)) {
    message.warning('不能粘贴到自身');
    return;
  }
  if (clipboard.mode === 'cut') {
    await handleBatchMove(clipboard.ids, record.id);
    documentClipboard.value = undefined;
    return;
  }
  moving.value = true;
  try {
    const copiedFiles = await copyDocuments({
      ids: clipboard.ids,
      parentId: record.id,
      scope: scope.value,
      shareTargetType: activeScopeOption.value?.shareTargetType,
    });
    for (const copiedFile of copiedFiles) {
      await shareRootFolderIfNeeded(copiedFile, record.id);
    }
    message.success(`已粘贴 ${copiedFiles.length} 项`);
    await reloadAll();
  } finally {
    moving.value = false;
  }
}

function isEditableShortcutTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tagName = target.tagName.toLowerCase();
  return (
    target.isContentEditable ||
    tagName === 'input' ||
    tagName === 'textarea' ||
    tagName === 'select' ||
    Boolean(target.closest('[contenteditable="true"], .ant-input, .ant-select'))
  );
}

function handleTreeShortcutKeydown(event: KeyboardEvent) {
  if (
    !treeShortcutActive.value ||
    loading.value ||
    moving.value ||
    treeLoading.value ||
    isEditableShortcutTarget(event.target)
  ) {
    return;
  }
  const folder = selectedTreeFolder.value;
  if (!folder?.id) {
    return;
  }
  const key = event.key.toLowerCase();
  if (key === 'f2') {
    event.preventDefault();
    event.stopImmediatePropagation();
    handleRenameFolder(folder);
    return;
  }
  if (key === 'delete' || (event.metaKey && key === 'backspace')) {
    event.preventDefault();
    event.stopImmediatePropagation();
    handleDeleteFolder(folder);
    return;
  }
  const shortcutPressed = event.ctrlKey || event.metaKey;
  if (!shortcutPressed || event.altKey) {
    return;
  }
  if (event.shiftKey && key === 'n') {
    event.preventDefault();
    event.stopImmediatePropagation();
    void handleCreateFolderIn(folder);
    return;
  }
  if (key === 'x') {
    if (!canManageFolder(folder)) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    rememberDocumentClipboard('cut', [folder]);
    return;
  }
  if (key === 'c') {
    if (!canManageFolder(folder)) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    rememberDocumentClipboard('copy', [folder]);
    return;
  }
  if (key === 'v') {
    event.preventDefault();
    event.stopImmediatePropagation();
    void handlePasteToTreeFolder(folder);
  }
}

function getDragSourceIds(event: DragEvent) {
  const rawIds = event.dataTransfer?.getData('application/x-document-ids');
  if (rawIds) {
    try {
      const parsed = JSON.parse(rawIds);
      if (Array.isArray(parsed)) {
        return parsed.filter((id): id is string => typeof id === 'string' && id.length > 0);
      }
    } catch {
      // Fallback to the single-item payload below.
    }
  }
  const sourceId = event.dataTransfer?.getData('application/x-document-id');
  return sourceId ? [sourceId] : [];
}

function handleDropToTree(event: DragEvent, targetKey: string) {
  event.preventDefault();
  const targetFolder = isScopeRootKey(targetKey) ? undefined : findFolderByKey(targetKey);
  const targetParentId = isScopeRootKey(targetKey) ? undefined : targetFolder?.id;
  const sourceIds = getDragSourceIds(event).filter((sourceId) => sourceId !== targetParentId);
  if (sourceIds.length === 0 || scope.value === 'trash') {
    return;
  }
  if (!canDropToTreeTarget(targetKey)) {
    return;
  }
  const targetPath = isScopeRootKey(targetKey)
    ? []
    : findCachedPath(targetKey)?.path || [];
  if (targetPath.some((item) => item.id && sourceIds.includes(item.id))) {
    message.warning('不能移动到自身或子文件夹');
    return;
  }
  if (sourceIds.length === 1) {
    const sourceId = sourceIds[0];
    if (!sourceId) {
      return;
    }
    void handleMove(sourceId, targetParentId);
    return;
  }
  void handleBatchMove(sourceIds, targetParentId);
}

function handleTreeDragStart(event: DragEvent, key: string) {
  const folder = findFolderByKey(key);
  if (!folder?.id || !canManageTreeFolder(key) || moving.value) {
    event.preventDefault();
    return;
  }
  event.dataTransfer?.setData('application/x-document-id', folder.id);
  event.dataTransfer?.setData('text/plain', folder.fileName || '');
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
  }
}

function isDocumentDrag(event: DragEvent) {
  const types = Array.from(event.dataTransfer?.types || []);
  return types.includes('application/x-document-id') || types.includes('application/x-document-ids');
}

function getFileExtension(record: DocumentFileInfo) {
  const fileName = record.fileName || '';
  const dotIndex = fileName.lastIndexOf('.');
  return dotIndex >= 0 ? fileName.slice(dotIndex + 1).toLowerCase() : '';
}

function isImagePreviewFile(record: DocumentFileInfo) {
  const fileType = String(record.fileType || '').toLowerCase();
  const extension = getFileExtension(record);
  if (extension === 'svg') {
    return false;
  }
  return (
    record.izFolder !== '1' &&
    (fileType === 'image' ||
      fileType.startsWith('image/') ||
      IMAGE_PREVIEW_EXTENSIONS.has(extension))
  );
}

function handleTreeDragOver(event: DragEvent, targetKey: string) {
  if (!isDocumentDrag(event) || scope.value === 'trash') {
    return;
  }
  if (!canDropToTreeTarget(targetKey)) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
}

function handleAction(event: string, record: DocumentFileInfo) {
  if (event === 'open') {
    void handleOpenFolder(record);
    return;
  }
  if (event === 'preview') {
    if (isImagePreviewFile(record)) {
      imagePreviewModalRef.value?.open(record);
      return;
    }
    previewModalRef.value?.open(record);
    return;
  }
  if (event === 'edit') {
    previewModalRef.value?.open(record, 'edit');
    return;
  }
  if (event === 'history') {
    historyModalRef.value?.open(record);
    return;
  }
  if (event === 'download') {
    void handleDownload(record);
    return;
  }
  if (event === 'rename') {
    handleRename(record);
    return;
  }
  if (event === 'share') {
    handleShare(record);
    return;
  }
  if (event === 'cancelShare') {
    handleCancelShare(record);
    return;
  }
  if (event === 'delete') {
    handleDelete(record);
    return;
  }
  if (event === 'restore') {
    void handleRestore(record);
    return;
  }
  if (event === 'purge') {
    handlePurge(record);
    return;
  }
  if (event === 'star') {
    void handleStar(record);
  }
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
        @mousedown.capture="treeShortcutActive = true"
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
        @mousedown.capture="treeShortcutActive = false"
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
      ref="fileInputRef"
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
