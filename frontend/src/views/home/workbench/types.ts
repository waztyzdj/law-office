import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';

export type WorkbenchTodoTabKey = 'done' | 'todo';
export type WorkbenchCcTabKey = 'read-cc' | 'unread-cc';
export type WorkbenchMessageTabKey =
  | 'read-message'
  | 'timeout-message'
  | 'unread-message'
  | 'urge-message';
export type WorkbenchTitleTabKey =
  | WorkbenchCcTabKey
  | WorkbenchMessageTabKey
  | WorkbenchTodoTabKey;

export interface WorkbenchCardAction {
  disabled?: boolean;
  icon: string;
  key: string;
  loading?: boolean;
  onClick: () => void;
  title: string;
}

export interface WorkbenchCardOpenPayload {
  card: WorkbenchLayoutCard;
  item: WorkbenchCardItem;
}

export interface WorkbenchQuickEntryActionPayload {
  draft: boolean;
  item?: WorkbenchCardItem;
  items: WorkbenchCardItem[];
}

export interface WorkbenchQuickEntryDraftChange {
  item: WorkbenchCardItem;
  seq: number;
}

export interface WorkbenchQuickEntrySortSavePayload {
  items: WorkbenchCardItem[];
  onSaved: () => void;
}

export interface WorkbenchQuickEntryExpose {
  getCurrentItems?: () => WorkbenchCardItem[];
  reset?: () => void;
}

export interface WorkbenchTitleTab {
  key: WorkbenchTitleTabKey;
  label: string;
  total: number;
}
