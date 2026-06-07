import type { ComputedRef } from 'vue';
import type { DocumentFileInfo } from '#/api/document';

import { ref, watch } from 'vue';

import { downloadDocumentThumbnail } from '#/api/document';

import { isImageFile } from '../components/documentExplorerUtils';

interface UseDocumentThumbnailsOptions {
  itemKey: (record: DocumentFileInfo) => string;
  sortedItems: ComputedRef<DocumentFileInfo[]>;
}

export function useDocumentThumbnails(options: UseDocumentThumbnailsOptions) {
  const imageThumbnailUrls = ref<Record<string, string>>({});
  let imageThumbnailLoadVersion = 0;

  function imageThumbnailUrl(record: DocumentFileInfo) {
    const key = options.itemKey(record);
    return key ? imageThumbnailUrls.value[key] : undefined;
  }

  function revokeImageThumbnailUrl(key: string) {
    const url = imageThumbnailUrls.value[key];
    if (url) {
      URL.revokeObjectURL(url);
    }
  }

  function revokeAllImageThumbnailUrls() {
    for (const key of Object.keys(imageThumbnailUrls.value)) {
      revokeImageThumbnailUrl(key);
    }
    imageThumbnailUrls.value = {};
  }

  async function loadImageThumbnails() {
    const version = ++imageThumbnailLoadVersion;
    const imageItems = options.sortedItems.value.filter((item) => item.id && isImageFile(item));
    const activeKeys = new Set(imageItems.map((item) => options.itemKey(item)).filter(Boolean));

    for (const key of Object.keys(imageThumbnailUrls.value)) {
      if (!activeKeys.has(key)) {
        revokeImageThumbnailUrl(key);
        delete imageThumbnailUrls.value[key];
      }
    }

    for (const item of imageItems) {
      const key = options.itemKey(item);
      if (!item.id || !key || imageThumbnailUrls.value[key]) {
        continue;
      }
      try {
        const blob = await downloadDocumentThumbnail(item.id);
        if (version !== imageThumbnailLoadVersion) {
          continue;
        }
        imageThumbnailUrls.value = {
          ...imageThumbnailUrls.value,
          [key]: URL.createObjectURL(blob),
        };
      } catch {
        // 缩略图加载失败时保留文件类型图标，避免影响文件列表使用。
      }
    }
  }

  const stopThumbnailWatch = watch(
    () =>
      options.sortedItems.value
        .filter((item) => item.id && isImageFile(item))
        .map((item) => options.itemKey(item))
        .join(','),
    () => {
      void loadImageThumbnails();
    },
    { immediate: true },
  );

  function cleanupImageThumbnails() {
    imageThumbnailLoadVersion += 1;
    stopThumbnailWatch();
    revokeAllImageThumbnailUrls();
  }

  return {
    cleanupImageThumbnails,
    imageThumbnailUrl,
  };
}
