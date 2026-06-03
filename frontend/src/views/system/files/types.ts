import type {
  DocumentFileInfo,
  DocumentScope,
  DocumentShareTargetType,
} from '#/api/system/document';

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

export type DocumentBatchAction = 'copy' | 'cut' | 'delete' | 'download';
export type DocumentViewMode = 'grid' | 'list';
