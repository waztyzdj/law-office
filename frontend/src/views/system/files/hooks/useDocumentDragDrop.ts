import type { ComputedRef, Ref } from 'vue';
import type { DocumentFileInfo } from '#/api/system/document';

interface UseDocumentDragDropOptions {
  canDropOnFolder: (record: DocumentFileInfo) => boolean;
  canMove: (record: DocumentFileInfo) => boolean;
  emitBatchMove: (
    sourceIds: string[],
    targetParentId?: string,
    sourceParentIds?: Array<string | undefined>,
  ) => void;
  emitMove: (sourceId: string, targetParentId?: string, sourceParentId?: string) => void;
  isSelected: (record: DocumentFileInfo) => boolean;
  moving: Readonly<Ref<boolean>>;
  selectOnly: (record: DocumentFileInfo) => void;
  selectedMovableIds: ComputedRef<string[]>;
  selectedMovableRecords: ComputedRef<DocumentFileInfo[]>;
}

export function useDocumentDragDrop(options: UseDocumentDragDropOptions) {
  function handleDragStart(event: DragEvent, record: DocumentFileInfo) {
    if (!record.id || !options.canMove(record) || options.moving.value) {
      event.preventDefault();
      return;
    }
    if (!options.isSelected(record)) {
      options.selectOnly(record);
    }
    const sourceRecords =
      options.isSelected(record) && options.selectedMovableRecords.value.length > 0
        ? options.selectedMovableRecords.value
        : [record];
    const sourceIds = sourceRecords.map((item) => item.id || '').filter(Boolean);
    const sourceParentIds = sourceRecords.map((item) => item.parentId || undefined);
    event.dataTransfer?.setData('application/x-document-id', sourceIds[0] || record.id);
    event.dataTransfer?.setData('application/x-document-ids', JSON.stringify(sourceIds));
    event.dataTransfer?.setData(
      'application/x-document-source-parent-ids',
      JSON.stringify(sourceParentIds.map((parentId) => parentId || null)),
    );
    event.dataTransfer?.setData('text/plain', record.fileName || '');
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  function isDocumentDrag(event: DragEvent) {
    const types = Array.from(event.dataTransfer?.types || []);
    return types.includes('application/x-document-id') || types.includes('application/x-document-ids');
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

  function getDragSourceParentIds(event: DragEvent) {
    const rawParentIds = event.dataTransfer?.getData('application/x-document-source-parent-ids');
    if (!rawParentIds) {
      return [];
    }
    try {
      const parsed = JSON.parse(rawParentIds);
      if (Array.isArray(parsed)) {
        return parsed
          .filter((parentId): parentId is string | null => typeof parentId === 'string' || parentId === null)
          .map((parentId) => parentId ?? undefined);
      }
    } catch {
      return [];
    }
    return [];
  }

  function handleFolderDragOver(event: DragEvent, target: DocumentFileInfo) {
    if (!options.canDropOnFolder(target)) {
      return;
    }
    if (!isDocumentDrag(event)) {
      return;
    }
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
  }

  function handleDropOnFolder(event: DragEvent, target: DocumentFileInfo) {
    event.preventDefault();
    const sourceIds = getDragSourceIds(event).filter((sourceId) => sourceId !== target.id);
    const sourceParentIds = getDragSourceParentIds(event);
    if (sourceIds.length === 0 || !options.canDropOnFolder(target) || options.moving.value) {
      return;
    }
    if (sourceIds.length === 1) {
      const sourceId = sourceIds[0];
      if (!sourceId) {
        return;
      }
      options.emitMove(sourceId, target.id, sourceParentIds[0]);
      return;
    }
    options.emitBatchMove(sourceIds, target.id, sourceParentIds);
  }

  return {
    handleDragStart,
    handleDropOnFolder,
    handleFolderDragOver,
  };
}
