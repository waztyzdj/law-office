import type {
  DocumentFileInfo,
  DocumentScope,
  DocumentShareTargetType,
} from '#/api/document';
import type { DocumentSortField, DocumentSortState } from '#/constants/document';

type SharedRootTargetType = Extract<DocumentShareTargetType, 'depart' | 'tenant'>;

export interface ScopeOption {
  children?: ScopeOption[];
  icon: string;
  key: string;
  scope?: DocumentScope;
  selectable?: boolean;
  shareTargetId?: string;
  shareTargetType?: SharedRootTargetType;
  title: string;
}

export interface FolderTreeNode {
  children?: FolderTreeNode[];
  file?: DocumentFileInfo;
  isLeaf?: boolean;
  key: string;
  selectable?: boolean;
  title: string;
}

export interface InlineEditorState {
  extension?: string;
  fileName: string;
  mode: 'create' | 'rename';
  parentId?: string;
  record?: DocumentFileInfo;
}

export interface DocumentNavigationLocation {
  parentStack: DocumentFileInfo[];
  rootKey: string;
}

export type DocumentBatchAction = 'copy' | 'cut' | 'delete' | 'download' | 'restore';
export type DocumentViewMode = 'grid' | 'list';

export interface DocumentContentViewProps {
  canEditContentItem: (record: DocumentFileInfo) => boolean;
  canEditItem: (record: DocumentFileInfo) => boolean;
  canMove: (record: DocumentFileInfo) => boolean;
  canShowItemActionMenu: (record: DocumentFileInfo) => boolean;
  canPreviewItem: (record: DocumentFileInfo) => boolean;
  canViewHistoryItem: (record: DocumentFileInfo) => boolean;
  creatingHere?: boolean;
  getContextCopyableRecords: (record: DocumentFileInfo) => DocumentFileInfo[];
  getContextCuttableRecords: (record: DocumentFileInfo) => DocumentFileInfo[];
  getContextDeletableRecords: (record: DocumentFileInfo) => DocumentFileInfo[];
  getContextDownloadRecords: (record: DocumentFileInfo) => DocumentFileInfo[];
  getContextRestorableRecords: (record: DocumentFileInfo) => DocumentFileInfo[];
  imageThumbnailUrl: (record: DocumentFileInfo) => string | undefined;
  inlineEditor?: InlineEditorState;
  isGlobalSearch?: boolean;
  isCutting: (record: DocumentFileInfo) => boolean;
  isRenaming: (record: DocumentFileInfo) => boolean;
  isSelected: (record: DocumentFileInfo) => boolean;
  isSingleContext: (record: DocumentFileInfo) => boolean;
  itemKey: (record: DocumentFileInfo) => string;
  items: DocumentFileInfo[];
  savingName?: boolean;
  readonlyContext?: boolean;
  scope: DocumentScope;
}

export interface DocumentContentViewEmits {
  action: [event: string, record: DocumentFileInfo];
  contextBatchAction: [event: DocumentBatchAction, record: DocumentFileInfo];
  contextSelect: [record: DocumentFileInfo];
  dropOnFolder: [event: DragEvent, record: DocumentFileInfo];
  folderDragOver: [event: DragEvent, record: DocumentFileInfo];
  inlineCancel: [];
  inlineChange: [value: string];
  inlineSubmit: [];
  itemClick: [event: MouseEvent, record: DocumentFileInfo];
  itemDragStart: [event: DragEvent, record: DocumentFileInfo];
  itemActivate: [record: DocumentFileInfo];
  itemOpen: [record: DocumentFileInfo];
}

export type DocumentContentViewListeners = {
  [Key in keyof DocumentContentViewEmits]: (...args: DocumentContentViewEmits[Key]) => void;
};

export interface DocumentContentViewExpose {
  focusCreateNameInput: () => Promise<void>;
  focusRenameNameInput: () => Promise<void>;
}

export interface DocumentListViewProps extends DocumentContentViewProps {
  sortState: DocumentSortState;
}

export interface DocumentListViewEmits extends DocumentContentViewEmits {
  sort: [field: DocumentSortField];
}
