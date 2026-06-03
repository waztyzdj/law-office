export const BUSINESS_VIEW_STORE_TYPE = 'business_view';
export const BUSINESS_MODULE_VIEW_STORE_TYPE = 'business_module_view';
export const BUSINESS_RECORD_VIEW_STORE_TYPE = 'business_record_view';

export const DOCUMENT_SORT_FIELDS = ['fileName', 'fileSize', 'fileType', 'modifiedTime'] as const;
export const DOCUMENT_SORT_ORDERS = ['asc', 'desc'] as const;

export type DocumentSortField = (typeof DOCUMENT_SORT_FIELDS)[number];
export type DocumentSortOrder = (typeof DOCUMENT_SORT_ORDERS)[number];

export interface DocumentSortState {
  field: DocumentSortField;
  order: DocumentSortOrder;
}

export interface DocumentSortOption {
  field: DocumentSortField;
  label: string;
}

export const DOCUMENT_SORT_OPTIONS: DocumentSortOption[] = [
  { field: 'fileName', label: '名称' },
  { field: 'modifiedTime', label: '修改时间' },
  { field: 'fileType', label: '类型' },
  { field: 'fileSize', label: '大小' },
];

export const ONLYOFFICE_PREVIEW_EXTENSIONS = new Set([
  'doc',
  'docx',
  'pdf',
  'ppt',
  'pptx',
  'xls',
  'xlsx',
]);
export const ONLYOFFICE_EDIT_EXTENSIONS = new Set(['doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx']);
export const IMAGE_PREVIEW_EXTENSIONS = new Set(['bmp', 'gif', 'jpeg', 'jpg', 'png', 'webp']);

export const DOCUMENT_UPLOAD_ACCEPT = [
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
