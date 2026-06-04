import type { ComputedRef, Ref } from 'vue';
import type { DocumentFileInfo } from '#/api/system/document';

interface UseDocumentDragDropOptions {
  canDropOnFolder: (record: DocumentFileInfo) => boolean;
  canMove: (record: DocumentFileInfo) => boolean;
  emitBatchMove: (sourceIds: string[], targetParentId?: string) => void;
  emitMove: (sourceId: string, targetParentId?: string) => void;
  isSelected: (record: DocumentFileInfo) => boolean;
  moving: Readonly<Ref<boolean>>;
  selectOnly: (record: DocumentFileInfo) => void;
  selectedMovableIds: ComputedRef<string[]>;
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
    const sourceIds = options.isSelected(record) && options.selectedMovableIds.value.length > 0
      ? options.selectedMovableIds.value
      : [record.id];
    event.dataTransfer?.setData('application/x-document-id', sourceIds[0] || record.id);
    event.dataTransfer?.setData('application/x-document-ids', JSON.stringify(sourceIds));
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
    if (sourceIds.length === 0 || !options.canDropOnFolder(target) || options.moving.value) {
      return;
    }
    if (sourceIds.length === 1) {
      const sourceId = sourceIds[0];
      if (!sourceId) {
        return;
      }
      options.emitMove(sourceId, target.id);
      return;
    }
    options.emitBatchMove(sourceIds, target.id);
  }

  return {
    handleDragStart,
    handleDropOnFolder,
    handleFolderDragOver,
  };
}
