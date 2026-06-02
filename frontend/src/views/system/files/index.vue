<script setup lang="ts">
import type {
  DocumentFileInfo,
  DocumentScope,
  DocumentShareTargetType,
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
import DocumentImagePreviewModal from './components/DocumentImagePreviewModal.vue';
import DocumentOnlyOfficePreviewModal from './components/DocumentOnlyOfficePreviewModal.vue';
import DocumentShareDrawer from './components/DocumentShareDrawer.vue';

const SCOPE_ROOT_PREFIX = 'scope:';
const TREE_NODE_KEY_SEPARATOR = '::';
const PAGE_SIZE = 500;
const BUSINESS_VIEW_STORE_TYPE = 'business_view';
const BUSINESS_MODULE_VIEW_STORE_TYPE = 'business_module_view';
const BUSINESS_RECORD_VIEW_STORE_TYPE = 'business_record_view';
const DOCUMENT_VIEW_MODE_STORAGE_KEY = 'document_center_view_mode';
const IMAGE_PREVIEW_EXTENSIONS = new Set(['bmp', 'gif', 'jpeg', 'jpg', 'png', 'webp']);
const DOCUMENT_UPLOAD_ACCEPT = [
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.pdf',
  '.txt',
  '.csv',
  '.rtf',
  '.md',
  '.wps',
  '.et',
  '.dps',
  '.odt',
  '.ods',
  '.odp',
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.bmp',
  '.webp',
  '.mp4',
  '.mov',
  '.avi',
  '.mkv',
  '.flv',
  '.wmv',
].join(',');

type SharedRootTargetType = Extract<DocumentShareTargetType, 'depart' | 'tenant'>;

interface ScopeOption {
  children?: ScopeOption[];
  icon: string;
  key: string;
  scope?: DocumentScope;
  selectable?: boolean;
  shareTargetId?: string;
  shareTargetType?: SharedRootTargetType;
  title: string;
}

interface FolderTreeNode {
  children?: FolderTreeNode[];
  file?: DocumentFileInfo;
  key: string;
  selectable?: boolean;
  title: string;
}

interface InlineEditorState {
  extension?: string;
  fileName: string;
  mode: 'create' | 'rename';
  parentId?: string;
  record?: DocumentFileInfo;
}

type DocumentBatchAction = 'copy' | 'cut' | 'delete' | 'download';
type DocumentViewMode = 'grid' | 'list';

interface DocumentNavigationLocation {
  parentStack: DocumentFileInfo[];
  rootKey: string;
}

const activeRootKey = ref('my');
const loading = ref(false);
const treeLoading = ref(false);
const uploading = ref(false);
const moving = ref(false);
const savingName = ref(false);
const keyword = ref('');
const dataSource = ref<DocumentFileInfo[]>([]);
const folderTree = ref<FolderTreeNode[]>([]);
const folderTreeCache = ref<Record<string, FolderTreeNode[]>>({});
const inlineEditor = ref<InlineEditorState>();
const expandedTreeKeys = ref<string[]>([getScopeRootKey('my')]);
const selectedTreeKeys = ref<string[]>([getScopeRootKey('my')]);
const parentStack = ref<DocumentFileInfo[]>([]);
const documentViewMode = ref<DocumentViewMode>(readDocumentViewMode());
const treeShortcutActive = ref(false);
const currentDeparts = ref<CurrentUserOrganization['departs']>([]);
const currentTenant = ref<CurrentUserTenant>();
const fileInputRef = ref<HTMLInputElement>();
const shareDrawerRef = ref<InstanceType<typeof DocumentShareDrawer>>();
const imagePreviewModalRef = ref<InstanceType<typeof DocumentImagePreviewModal>>();
const previewModalRef = ref<InstanceType<typeof DocumentOnlyOfficePreviewModal>>();
const documentClipboard = ref<{
  ids: string[];
  mode: Extract<DocumentBatchAction, 'copy' | 'cut'>;
}>();
const navigationHistory = ref<DocumentNavigationLocation[]>([]);
let treeRenameTimer: number | undefined;

const currentParentId = computed(() => parentStack.value.at(-1)?.id);
const currentFolder = computed(() => parentStack.value.at(-1));
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
const canGoParent = computed(() => !isGlobalSearch.value && parentStack.value.length > 0);
const canGoBack = computed(() => !isGlobalSearch.value && navigationHistory.value.length > 0);
const documentViewModeModel = computed({
  get: () => documentViewMode.value,
  set: (mode: DocumentViewMode) => handleChangeDocumentViewMode(mode),
});
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
const treeData = computed<FolderTreeNode[]>(() =>
  scopeOptions.value.map((item) => buildScopeTreeNode(item)),
);

function buildScopeTreeNode(option: ScopeOption): FolderTreeNode {
  const optionChildren = option.children?.map((item) => buildScopeTreeNode(item)) || [];
  const activeChildren = shouldRenderFolderTree(option) ? folderTreeCache.value[option.key] || [] : [];
  return {
    children: [...optionChildren, ...activeChildren],
    key: getScopeRootKey(option.key),
    selectable: option.selectable,
    title: option.title,
  };
}

function buildDepartScopeOptions(
  departs: CurrentUserOrganization['departs'],
): ScopeOption[] {
  const optionMap = new Map<string, ScopeOption>();
  const parentIdMap = new Map<string, string>();
  const roots: ScopeOption[] = [];

  for (const depart of departs) {
    if (!depart.id) {
      continue;
    }
    optionMap.set(depart.id, {
      icon: 'lucide:building-2',
      key: `depart:${depart.id}`,
      scope: 'shared',
      shareTargetId: depart.id,
      shareTargetType: 'depart',
      title: depart.departName || depart.orgCode || '部门共享',
    });
    if (depart.parentId) {
      parentIdMap.set(depart.id, depart.parentId);
    }
  }

  for (const [departId, option] of optionMap) {
    const parentId = parentIdMap.get(departId);
    const parent = parentId ? optionMap.get(parentId) : undefined;
    if (parent) {
      parent.children = [...(parent.children || []), option];
    } else {
      roots.push(option);
    }
  }

  return roots;
}

function getScopeRootKey(scopeValue: string) {
  return `${SCOPE_ROOT_PREFIX}${scopeValue}`;
}

function shouldRenderFolderTree(option?: ScopeOption) {
  return option?.shareTargetType !== 'depart';
}

function getFolderNodeKey(rootKey: string, fileId?: string) {
  return fileId ? `${rootKey}${TREE_NODE_KEY_SEPARATOR}${fileId}` : getScopeRootKey(rootKey);
}

function getSelectedTreeKey(rootKey: string, fileId?: string) {
  return shouldRenderFolderTree(findScopeOption(rootKey))
    ? getFolderNodeKey(rootKey, fileId)
    : getScopeRootKey(rootKey);
}

function getActiveSelectedTreeKey() {
  return getSelectedTreeKey(activeRootKey.value, currentParentId.value);
}

function readDocumentViewMode(): DocumentViewMode {
  if (typeof window === 'undefined') {
    return 'grid';
  }
  try {
    const cached = window.localStorage.getItem(DOCUMENT_VIEW_MODE_STORAGE_KEY);
    return cached === 'list' || cached === 'grid' ? cached : 'grid';
  } catch {
    return 'grid';
  }
}

function handleChangeDocumentViewMode(mode: DocumentViewMode) {
  if (mode !== 'list' && mode !== 'grid') {
    return;
  }
  documentViewMode.value = mode;
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(DOCUMENT_VIEW_MODE_STORAGE_KEY, mode);
  } catch {
    // 本地缓存失败不影响文档浏览。
  }
}

function isScopeRootKey(key: string) {
  return key.startsWith(SCOPE_ROOT_PREFIX);
}

function getScopeFromRootKey(key: string) {
  return key.slice(SCOPE_ROOT_PREFIX.length);
}

function findScopeOption(key: string, options = scopeOptions.value): ScopeOption | undefined {
  for (const option of options) {
    if (option.key === key) {
      return option;
    }
    const found = option.children ? findScopeOption(key, option.children) : undefined;
    if (found) {
      return found;
    }
  }
  return undefined;
}

function collectScopeRootKeys(options = scopeOptions.value): string[] {
  const keys: string[] = [];
  for (const option of options) {
    if (option.scope) {
      keys.push(option.key);
    }
    if (option.children?.length) {
      keys.push(...collectScopeRootKeys(option.children));
    }
  }
  return keys;
}

function getTreeNodeIcon(key: string) {
  if (!isScopeRootKey(key)) {
    const record = findFolderByKey(key);
    if (record?.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE) {
      return 'lucide:briefcase-business';
    }
    if (record?.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE) {
      return 'lucide:database';
    }
    return 'lucide:folder';
  }
  const scopeKey = getScopeFromRootKey(key);
  return findScopeOption(scopeKey)?.icon || 'lucide:folder';
}

function findFolderByKey(key: string) {
  return findCachedPath(key)?.path.at(-1);
}

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

async function buildFolderTree(
  parentId?: string,
  option: ScopeOption | undefined = activeScopeOption.value,
  rootKey = activeRootKey.value,
): Promise<FolderTreeNode[]> {
  const children = await fetchDocuments(parentId, undefined, option);
  const records = children.filter((item) => item.izFolder === '1' && item.id);
  const nodes = await Promise.all(
    records.map(async (record) => ({
      children: record.izFolder === '1' ? await buildFolderTree(record.id, option, rootKey) : undefined,
      file: record,
      key: getFolderNodeKey(rootKey, record.id),
      title: record.fileName || (record.izFolder === '1' ? '未命名文件夹' : '未命名文件'),
    })),
  );
  return nodes;
}

async function loadFolderTree(rootKey = activeRootKey.value, updateSelection = true) {
  const option = findScopeOption(rootKey);
  if (!option?.scope) {
    return;
  }
  if (!shouldRenderFolderTree(option)) {
    folderTreeCache.value = {
      ...folderTreeCache.value,
      [rootKey]: [],
    };
    if (rootKey === activeRootKey.value) {
      folderTree.value = [];
    }
    if (updateSelection) {
      selectedTreeKeys.value = [getScopeRootKey(rootKey)];
    }
    return;
  }
  if (option.scope === 'trash') {
    folderTreeCache.value = {
      ...folderTreeCache.value,
      [rootKey]: [],
    };
    if (rootKey === activeRootKey.value) {
      folderTree.value = [];
    }
    if (updateSelection) {
      selectedTreeKeys.value = [getScopeRootKey(rootKey)];
    }
    return;
  }
  treeLoading.value = true;
  try {
    const nextTree = await buildFolderTree(undefined, option, rootKey);
    folderTreeCache.value = {
      ...folderTreeCache.value,
      [rootKey]: nextTree,
    };
    if (rootKey === activeRootKey.value) {
      folderTree.value = nextTree;
    }
    if (updateSelection) {
      selectedTreeKeys.value = [getSelectedTreeKey(rootKey, currentParentId.value)];
    }
  } finally {
    treeLoading.value = false;
  }
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

async function reloadCachedFolderTrees() {
  const rootKeys = Array.from(
    new Set([activeRootKey.value, ...Object.keys(folderTreeCache.value)]),
  );
  await Promise.all(rootKeys.map((rootKey) => loadFolderTree(rootKey, false)));
  selectedTreeKeys.value = [getActiveSelectedTreeKey()];
}

async function loadInitialFolderTrees() {
  const rootKeys = Array.from(new Set(collectScopeRootKeys()));
  await Promise.all(rootKeys.map((rootKey) => loadFolderTree(rootKey, false)));
  selectedTreeKeys.value = [getActiveSelectedTreeKey()];
}

function resetAndLoad() {
  void loadData();
}

function handleSearch(value: string) {
  keyword.value = value;
  resetAndLoad();
}

function findPath(
  nodes: FolderTreeNode[],
  key: string,
  parents: DocumentFileInfo[] = [],
): DocumentFileInfo[] | undefined {
  for (const node of nodes) {
    const nextParents = node.file ? [...parents, node.file] : parents;
    if (node.key === key) {
      return nextParents;
    }
    const found = node.children ? findPath(node.children, key, nextParents) : undefined;
    if (found) {
      return found;
    }
  }
  return undefined;
}

function findCachedPath(key: string) {
  for (const [rootKey, nodes] of Object.entries(folderTreeCache.value)) {
    const path = findPath(nodes, key);
    if (path) {
      return { path, rootKey };
    }
  }
  return undefined;
}

function expandPathKeys(path: DocumentFileInfo[]) {
  const keys = path
    .map((item) => getFolderNodeKey(activeRootKey.value, item.id))
    .filter(Boolean) as string[];
  expandedTreeKeys.value = Array.from(
    new Set([getScopeRootKey(activeRootKey.value), ...expandedTreeKeys.value, ...keys]),
  );
}

function getCurrentNavigationLocation(): DocumentNavigationLocation {
  return {
    parentStack: [...parentStack.value],
    rootKey: activeRootKey.value,
  };
}

function isSameNavigationLocation(
  first: DocumentNavigationLocation,
  second: DocumentNavigationLocation,
) {
  return (
    first.rootKey === second.rootKey &&
    (first.parentStack.at(-1)?.id || '') === (second.parentStack.at(-1)?.id || '')
  );
}

function pushNavigationHistory() {
  const current = getCurrentNavigationLocation();
  const last = navigationHistory.value.at(-1);
  if (!last || !isSameNavigationLocation(last, current)) {
    navigationHistory.value = [...navigationHistory.value.slice(-49), current];
  }
}

async function applyNavigationLocation(location: DocumentNavigationLocation) {
  cancelInlineEditor();
  activeRootKey.value = location.rootKey;
  folderTree.value = folderTreeCache.value[location.rootKey] || [];
  parentStack.value = [...location.parentStack];
  selectedTreeKeys.value = [getSelectedTreeKey(location.rootKey, currentParentId.value)];
  expandedTreeKeys.value = Array.from(
    new Set([...expandedTreeKeys.value, getScopeRootKey(location.rootKey)]),
  );
  expandPathKeys(parentStack.value);
  await Promise.all([loadData(), loadFolderTree(location.rootKey, false)]);
}

async function handleGoBack() {
  const location = navigationHistory.value.at(-1);
  if (!location) {
    return;
  }
  navigationHistory.value = navigationHistory.value.slice(0, -1);
  await applyNavigationLocation(location);
}

function handleGoParent() {
  if (!canGoParent.value) {
    return;
  }
  pushNavigationHistory();
  parentStack.value = parentStack.value.slice(0, -1);
  selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  cancelInlineEditor();
  resetAndLoad();
}

function clearTreeRenameTimer() {
  if (treeRenameTimer) {
    window.clearTimeout(treeRenameTimer);
    treeRenameTimer = undefined;
  }
}

async function handleSelectTree(keys: unknown[], info?: { node?: { key?: string | number } }) {
  treeShortcutActive.value = true;
  const key = String(
    info?.node?.key || keys[0] || selectedTreeKeys.value[0] || getScopeRootKey(activeRootKey.value),
  );
  const alreadySelected = selectedTreeKeys.value[0] === key;
  clearTreeRenameTimer();
  selectedTreeKeys.value = [key];
  if (alreadySelected && canManageTreeFolder(key) && !isEditingTreeNode(key)) {
    const folder = findFolderByKey(key);
    if (folder) {
      treeRenameTimer = window.setTimeout(() => {
        handleRenameFolder(folder);
        treeRenameTimer = undefined;
      }, 220);
      return;
    }
  }
  cancelInlineEditor();
  if (isScopeRootKey(key)) {
    const nextRootKey = getScopeFromRootKey(key);
    const nextRoot = findScopeOption(nextRootKey);
    if (!nextRoot?.scope) {
      return;
    }
    const nextLocation = { parentStack: [], rootKey: nextRootKey };
    const currentLocation = getCurrentNavigationLocation();
    if (!isSameNavigationLocation(currentLocation, nextLocation)) {
      pushNavigationHistory();
    }
    parentStack.value = [];
    expandedTreeKeys.value = Array.from(new Set([...expandedTreeKeys.value, key]));
    if (nextRootKey !== activeRootKey.value) {
      activeRootKey.value = nextRootKey;
      await Promise.all([loadData(), loadFolderTree()]);
      return;
    }
    resetAndLoad();
    return;
  }

  const cachedPath = findCachedPath(key);
  const selectedRecord = cachedPath?.path.at(-1);
  const parentPath = selectedRecord?.izFolder === '1'
    ? cachedPath?.path || []
    : cachedPath?.path.slice(0, -1) || [];
  const nextLocation = {
    parentStack: parentPath,
    rootKey: cachedPath?.rootKey || activeRootKey.value,
  };
  const currentLocation = getCurrentNavigationLocation();
  if (!isSameNavigationLocation(currentLocation, nextLocation)) {
    pushNavigationHistory();
  }
  if (cachedPath) {
    activeRootKey.value = cachedPath.rootKey;
    folderTree.value = folderTreeCache.value[cachedPath.rootKey] || [];
  }
  parentStack.value = nextLocation.parentStack;
  expandPathKeys(parentStack.value);
  resetAndLoad();
}

function handleOpenFolder(record: DocumentFileInfo) {
  if (!record.id || record.izFolder !== '1') {
    return;
  }
  cancelInlineEditor();
  pushNavigationHistory();
  parentStack.value = [...parentStack.value, record];
  selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  expandPathKeys(parentStack.value);
  resetAndLoad();
}

function handleGoRoot() {
  if (parentStack.value.length === 0) {
    return;
  }
  pushNavigationHistory();
  parentStack.value = [];
  selectedTreeKeys.value = [getScopeRootKey(activeRootKey.value)];
  cancelInlineEditor();
  resetAndLoad();
}

function handleGoBreadcrumb(index: number) {
  if (index === parentStack.value.length - 1) {
    return;
  }
  pushNavigationHistory();
  parentStack.value = parentStack.value.slice(0, index + 1);
  selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  cancelInlineEditor();
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
  await loadData();
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

function handleShareTreeFolder(record?: DocumentFileInfo) {
  if (!record?.id || !canManageFolder(record) || scope.value === 'trash') {
    return;
  }
  handleShare(record);
}

function handleCancelShareTreeFolder(record?: DocumentFileInfo) {
  if (!record?.id || !canManageFolder(record) || !record.sharedFlag) {
    return;
  }
  handleCancelShare(record);
}

function handleStarTreeFolder(record?: DocumentFileInfo) {
  if (!record?.id || !canManageFolder(record) || scope.value === 'trash') {
    return;
  }
  void handleStar(record);
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
      for (const id of ids) {
        await deleteDocument(id);
      }
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
    for (const id of ids) {
      await moveDocument({
        id,
        parentId: targetParentId,
        scope: scope.value,
        shareTargetType: activeScopeOption.value?.shareTargetType,
      });
    }
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
    handleOpenFolder(record);
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
                  <Menu>
                    <Menu.Item
                      v-if="canCreateInTreeFolder(String(key))"
                      @click="handleCreateFolderIn(findFolderByKey(String(key)))"
                    >
                      <IconifyIcon class="document-menu-icon" icon="lucide:folder-plus" />
                      新建文件夹
                    </Menu.Item>
                    <Menu.Item
                      v-if="canManageTreeFolder(String(key))"
                      @click="handleShareTreeFolder(findFolderByKey(String(key)))"
                    >
                      <IconifyIcon class="document-menu-icon" icon="lucide:share-2" />
                      {{ findFolderByKey(String(key))?.sharedFlag ? '查看共享' : '共享' }}
                    </Menu.Item>
                    <Menu.Item
                      v-if="canManageTreeFolder(String(key)) && findFolderByKey(String(key))?.sharedFlag"
                      danger
                      @click="handleCancelShareTreeFolder(findFolderByKey(String(key)))"
                    >
                      <IconifyIcon class="document-menu-icon" icon="lucide:share-x" />
                      取消共享
                    </Menu.Item>
                    <Menu.Item
                      v-if="canManageTreeFolder(String(key))"
                      @click="handleStarTreeFolder(findFolderByKey(String(key)))"
                    >
                      <IconifyIcon class="document-menu-icon" icon="lucide:star" />
                      {{ findFolderByKey(String(key))?.izStar === '1' ? '取消收藏' : '收藏' }}
                    </Menu.Item>
                    <Menu.Item
                      v-if="canManageTreeFolder(String(key))"
                      @click="handleRenameFolder(findFolderByKey(String(key)))"
                    >
                      <IconifyIcon class="document-menu-icon" icon="lucide:pencil" />
                      修改名称
                    </Menu.Item>
                    <Menu.Item
                      v-if="canManageTreeFolder(String(key)) && !isBusinessScope"
                      danger
                      @click="handleDeleteFolder(findFolderByKey(String(key)))"
                    >
                      <IconifyIcon class="document-menu-icon" icon="lucide:trash-2" />
                      删除文件夹
                    </Menu.Item>
                  </Menu>
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
  min-height: 0;
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
  min-height: 100%;
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
