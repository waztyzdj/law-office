import type { ComputedRef, Ref } from 'vue';
import type {
  DocumentFileInfo,
  DocumentScope,
  DocumentShareTargetType,
  OnlyOfficeHistoryVersion,
  OnlyOfficePreviewMode,
} from '#/api/system/document';
import type {
  DocumentBatchAction,
  InlineEditorState,
  ScopeOption,
} from '../types';

import { computed, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import {
  batchDeleteDocuments,
  batchMoveDocuments,
  clearDocumentTrash,
  copyDocuments,
  createDocumentFolder,
  deleteDocument,
  downloadDocument,
  moveDocument,
  purgeDocument,
  renameDocument,
  restoreDocument,
  shareDocument,
  toggleDocumentStar,
  uploadDocument,
} from '#/api/system/document';

import { IMAGE_PREVIEW_EXTENSIONS } from '../constants';

type SharedRootTargetType = Extract<DocumentShareTargetType, 'depart' | 'tenant'>;

interface SharedRootTarget {
  targetId: string;
  targetType: SharedRootTargetType;
}

interface DocumentShareDrawerExpose {
  open: (payload: { record: DocumentFileInfo }) => void;
}

interface DocumentHistoryModalExpose {
  open: (record: DocumentFileInfo) => void;
}

interface DocumentImagePreviewModalExpose {
  open: (record: DocumentFileInfo) => void;
}

interface DocumentOnlyOfficePreviewModalExpose {
  open: (record: DocumentFileInfo, mode?: OnlyOfficePreviewMode) => void;
  openHistoryVersion: (version: OnlyOfficeHistoryVersion) => void;
}

interface UseDocumentActionsOptions {
  activeScopeOption: ComputedRef<ScopeOption | undefined>;
  activeSharedTarget: ComputedRef<SharedRootTarget | undefined>;
  canCreateCurrentScope: ComputedRef<boolean>;
  canDropToTreeTarget: (key: string) => boolean;
  canManageFolder: (record?: DocumentFileInfo) => boolean;
  canUploadCurrentScope: ComputedRef<boolean>;
  currentParentId: ComputedRef<string | undefined>;
  dataSource: Ref<DocumentFileInfo[]>;
  handleOpenFolder: (record: DocumentFileInfo) => Promise<void>;
  historyModalRef: Ref<DocumentHistoryModalExpose | undefined>;
  imagePreviewModalRef: Ref<DocumentImagePreviewModalExpose | undefined>;
  loadData: () => Promise<void>;
  previewModalRef: Ref<DocumentOnlyOfficePreviewModalExpose | undefined>;
  reloadAll: () => Promise<void>;
  reloadTrashData: () => Promise<void>;
  refreshFolderTreeChildren: (parentId?: string) => Promise<void>;
  selectFolderTreeParent: (parentId?: string) => void;
  isGlobalSearch: ComputedRef<boolean>;
  scope: ComputedRef<DocumentScope>;
  shareDrawerRef: Ref<DocumentShareDrawerExpose | undefined>;
  updateCachedFolderTreeRecord: (record: DocumentFileInfo) => void;
  updateNavigationFolderRecord: (record: DocumentFileInfo) => void;
}

export function useDocumentActions(options: UseDocumentActionsOptions) {
  const uploading = ref(false);
  const moving = ref(false);
  const savingName = ref(false);
  const inlineEditor = ref<InlineEditorState>();
  const fileInputRef = ref<HTMLInputElement>();
  const documentClipboard = ref<{
    ids: string[];
    mode: Extract<DocumentBatchAction, 'copy' | 'cut'>;
    sourceParentIds?: Array<string | undefined>;
  }>();

  const canPasteCurrentScope = computed(() => {
    const clipboard = documentClipboard.value;
    if (
      !clipboard ||
      clipboard.ids.length === 0 ||
      options.isGlobalSearch.value ||
      options.scope.value === 'trash'
    ) {
      return false;
    }
    if (clipboard.mode === 'copy') {
      return options.canUploadCurrentScope.value;
    }
    return true;
  });

  const cuttingDocumentIds = computed(() =>
    documentClipboard.value?.mode === 'cut' ? documentClipboard.value.ids : [],
  );

  async function refreshDocumentArea(parentIds: Array<string | undefined> = [options.currentParentId.value]) {
    const uniqueParentIds = Array.from(new Set(parentIds));
    await Promise.all([
      options.loadData(),
      ...uniqueParentIds.map((parentId) => options.refreshFolderTreeChildren(parentId)),
    ]);
  }

  function handleCreateFolder(parentId = options.currentParentId.value) {
    if (options.scope.value === 'trash' || !options.canCreateCurrentScope.value) {
      return;
    }
    inlineEditor.value = {
      fileName: '新建文件夹',
      mode: 'create',
      parentId,
    };
  }

  async function shareRootFolderIfNeeded(record: DocumentFileInfo, parentId?: string) {
    const target = options.activeSharedTarget.value;
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

  function handleUploadClick() {
    if (!options.canUploadCurrentScope.value || uploading.value) {
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
        const uploaded = await uploadDocument(file, options.currentParentId.value, {
          scope: options.scope.value,
          shareTargetType: options.activeScopeOption.value?.shareTargetType,
        });
        await shareRootFolderIfNeeded(uploaded, options.currentParentId.value);
      }
      message.success(files.length > 1 ? '文件已上传' : '文件上传成功');
      await options.loadData();
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
    if (!record?.id || !options.canManageFolder(record)) {
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
          scope: options.scope.value,
          shareTargetType: options.activeScopeOption.value?.shareTargetType,
        });
        await shareRootFolderIfNeeded(createdFolder, editor.parentId);
        cancelInlineEditor();
        await Promise.all([
          options.loadData(),
          options.refreshFolderTreeChildren(editor.parentId),
        ]);
        message.success('文件夹已创建');
      } else if (editor.record?.id) {
        if (fileName === (editor.record.fileName || '').trim()) {
          cancelInlineEditor();
          return;
        }
        const renamedRecord = await renameDocument({
          fileName,
          id: editor.record.id,
        });
        const nextRecord = {
          ...editor.record,
          ...renamedRecord,
          fileName,
        };
        if (nextRecord.izFolder === '1') {
          options.updateCachedFolderTreeRecord(nextRecord);
          options.updateNavigationFolderRecord(nextRecord);
        }
        cancelInlineEditor();
        await options.loadData();
        message.success('名称已更新');
      }
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
    options.shareDrawerRef.value?.open({ record });
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
        await options.reloadAll();
      },
    });
  }

  function handleDelete(record: DocumentFileInfo) {
    if (!record.id) {
      return;
    }
    const deletedParentId = record.parentId || options.currentParentId.value;
    Modal.confirm({
      cancelText: '取消',
      content: `确认删除“${record.fileName || ''}”吗？删除后可在回收站恢复。`,
      okText: '确定',
      title: '确认删除',
      async onOk() {
        await deleteDocument(record.id || '');
        if (record.izFolder === '1') {
          cancelInlineEditor();
          message.success('文件夹已移入回收站');
          options.selectFolderTreeParent(deletedParentId);
          await Promise.all([
            options.loadData(),
            options.refreshFolderTreeChildren(deletedParentId),
          ]);
          return;
        }
        message.success('已移入回收站');
        await options.reloadAll();
      },
    });
  }

  function handleDeleteFolder(record?: DocumentFileInfo) {
    if (!record?.id || !options.canManageFolder(record)) {
      return;
    }
    handleDelete(record);
  }

  function handleBatchDelete(records: DocumentFileInfo[]) {
    const ids = Array.from(new Set(records.map((record) => record.id || '').filter(Boolean)));
    if (ids.length === 0) {
      return;
    }
    const folders = records.filter((record) => record.id && record.izFolder === '1');
    const folderParentIds = Array.from(
      new Set(folders.map((record) => record.parentId || options.currentParentId.value)),
    );
    Modal.confirm({
      cancelText: '取消',
      content: `确认删除选中的 ${ids.length} 个文档吗？删除后可在回收站恢复。`,
      okText: '确定',
      title: '确认删除',
      async onOk() {
        await batchDeleteDocuments(ids);
        if (folders.length > 0) {
          cancelInlineEditor();
          message.success('文件夹已移入回收站');
          const selectedParentId =
            folderParentIds.length === 1 ? folderParentIds[0] : options.currentParentId.value;
          options.selectFolderTreeParent(selectedParentId);
          await Promise.all([
            options.loadData(),
            ...folderParentIds.map((parentId) => options.refreshFolderTreeChildren(parentId)),
          ]);
          return;
        }
        message.success('已移入回收站');
        await options.reloadAll();
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
      sourceParentIds: mode === 'cut'
        ? uniqueRecords.map((record) => record.parentId || options.currentParentId.value)
        : undefined,
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
      handleBatchDelete(records);
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
    await options.reloadAll();
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
        await options.reloadTrashData();
      },
    });
  }

  function handleClearTrash() {
    if (options.scope.value !== 'trash' || options.dataSource.value.length === 0) {
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
        await options.reloadAll();
      },
    });
  }

  async function handleStar(record: DocumentFileInfo) {
    if (!record.id) {
      return;
    }
    await toggleDocumentStar(record.id);
    message.success(record.izStar === '1' ? '已取消收藏' : '已收藏');
    await options.reloadAll();
  }

  async function handleMove(sourceId: string, targetParentId?: string) {
    if (!sourceId || moving.value || options.scope.value === 'trash') {
      return;
    }
    moving.value = true;
    try {
      await moveDocument({
        id: sourceId,
        parentId: targetParentId,
        scope: options.scope.value,
        shareTargetType: options.activeScopeOption.value?.shareTargetType,
      });
      message.success('已移动');
      await options.reloadAll();
    } finally {
      moving.value = false;
    }
  }

  async function handleBatchMove(sourceIds: string[], targetParentId?: string) {
    const ids = Array.from(new Set(sourceIds.filter(Boolean)));
    if (ids.length === 0 || moving.value || options.scope.value === 'trash') {
      return;
    }
    moving.value = true;
    try {
      await batchMoveDocuments({
        ids,
        parentId: targetParentId,
        scope: options.scope.value,
        shareTargetType: options.activeScopeOption.value?.shareTargetType,
      });
      message.success(`已移动 ${ids.length} 项`);
      await options.reloadAll();
    } finally {
      moving.value = false;
    }
  }

  async function handleMoveWithLocalRefresh(
    sourceIds: string[],
    sourceParentIds: Array<string | undefined>,
    targetParentId?: string,
  ) {
    const ids = Array.from(new Set(sourceIds.filter(Boolean)));
    if (ids.length === 0 || moving.value || options.scope.value === 'trash') {
      return;
    }
    moving.value = true;
    try {
      await batchMoveDocuments({
        ids,
        parentId: targetParentId,
        scope: options.scope.value,
        shareTargetType: options.activeScopeOption.value?.shareTargetType,
      });
      message.success(`已移动 ${ids.length} 项`);
      await refreshDocumentArea([...sourceParentIds, targetParentId]);
    } finally {
      moving.value = false;
    }
  }

  async function handlePasteMove(sourceIds: string[], targetParentId?: string) {
    await handleMoveWithLocalRefresh(
      sourceIds,
      documentClipboard.value?.sourceParentIds || [],
      targetParentId,
    );
  }

  async function handleTreeMove(
    sourceIds: string[],
    sourceParentIds: Array<string | undefined>,
    targetParentId?: string,
  ) {
    await handleMoveWithLocalRefresh(sourceIds, sourceParentIds, targetParentId);
  }

  async function handlePaste() {
    const clipboard = documentClipboard.value;
    if (!clipboard || clipboard.ids.length === 0 || moving.value || !canPasteCurrentScope.value) {
      return;
    }
    const targetParentId = options.currentParentId.value;
    if (clipboard.mode === 'cut') {
      await handlePasteMove(clipboard.ids, targetParentId);
      documentClipboard.value = undefined;
      return;
    }
    moving.value = true;
    try {
      const copiedFiles = await copyDocuments({
        ids: clipboard.ids,
        parentId: targetParentId,
        scope: options.scope.value,
        shareTargetType: options.activeScopeOption.value?.shareTargetType,
      });
      for (const copiedFile of copiedFiles) {
        await shareRootFolderIfNeeded(copiedFile, targetParentId);
      }
      message.success(`已粘贴 ${copiedFiles.length} 项`);
      await refreshDocumentArea([targetParentId]);
    } finally {
      moving.value = false;
    }
  }

  async function handlePasteToTreeFolder(record?: DocumentFileInfo) {
    const clipboard = documentClipboard.value;
    if (!record?.id || !clipboard || clipboard.ids.length === 0 || moving.value) {
      return;
    }
    if (!options.canDropToTreeTarget(record.id)) {
      return;
    }
    if (clipboard.ids.includes(record.id)) {
      message.warning('不能粘贴到自身');
      return;
    }
    if (clipboard.mode === 'cut') {
      await handlePasteMove(clipboard.ids, record.id);
      documentClipboard.value = undefined;
      return;
    }
    moving.value = true;
    try {
      const copiedFiles = await copyDocuments({
        ids: clipboard.ids,
        parentId: record.id,
        scope: options.scope.value,
        shareTargetType: options.activeScopeOption.value?.shareTargetType,
      });
      for (const copiedFile of copiedFiles) {
        await shareRootFolderIfNeeded(copiedFile, record.id);
      }
      message.success(`已粘贴 ${copiedFiles.length} 项`);
      await refreshDocumentArea([record.id]);
    } finally {
      moving.value = false;
    }
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

  function handleAction(event: string, record: DocumentFileInfo) {
    if (event === 'open') {
      void options.handleOpenFolder(record);
      return;
    }
    if (event === 'preview') {
      if (isImagePreviewFile(record)) {
        options.imagePreviewModalRef.value?.open(record);
        return;
      }
      options.previewModalRef.value?.open(record);
      return;
    }
    if (event === 'edit') {
      options.previewModalRef.value?.open(record, 'edit');
      return;
    }
    if (event === 'history') {
      options.historyModalRef.value?.open(record);
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

  return {
    cancelInlineEditor,
    canPasteCurrentScope,
    cuttingDocumentIds,
    documentClipboard,
    fileInputRef,
    handleAction,
    handleBatchAction,
    handleBatchMove,
    handleClearTrash,
    handleCreateFolder,
    handleDeleteFolder,
    handleFilesSelected,
    handleInlineNameChange,
    handleMove,
    handlePaste,
    handlePasteToTreeFolder,
    handleRenameFolder,
    handleTreeMove,
    handleUploadClick,
    inlineEditor,
    moving,
    rememberDocumentClipboard,
    savingName,
    submitInlineName,
    uploading,
  };
}
