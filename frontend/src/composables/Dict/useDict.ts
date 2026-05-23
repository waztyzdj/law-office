import { ref, shallowRef } from 'vue';

import { listDictOptionsByCode, type DictOption } from '#/api/system/dict';

const dictOptionsCache = new Map<string, DictOption[]>();

export function useDictOptions(dictCode: string) {
  const loading = ref(false);
  const options = shallowRef<DictOption[]>([]);

  async function loadOptions(force = false) {
    if (!dictCode) {
      options.value = [];
      return options.value;
    }

    if (!force) {
      const cachedOptions = dictOptionsCache.get(dictCode);
      if (cachedOptions) {
        options.value = cachedOptions;
        return cachedOptions;
      }
    }

    loading.value = true;
    try {
      const nextOptions = (await listDictOptionsByCode(dictCode)) || [];
      dictOptionsCache.set(dictCode, nextOptions);
      options.value = nextOptions;
      return nextOptions;
    } finally {
      loading.value = false;
    }
  }

  return {
    loading,
    options,
    loadOptions,
    refreshOptions: () => loadOptions(true),
  };
}

export function clearDictOptionsCache(dictCode?: string) {
  if (dictCode) {
    dictOptionsCache.delete(dictCode);
    return;
  }
  dictOptionsCache.clear();
}

export function getDictOptionLabel(
  options: DictOption[] | null | undefined,
  value?: string | number | null,
) {
  const normalizedValue = value === undefined || value === null ? '' : String(value);
  return (
    options?.find((option) => String(option.value) === normalizedValue)?.label || ''
  );
}
