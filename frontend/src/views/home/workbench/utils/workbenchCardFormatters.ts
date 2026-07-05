import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';

const DEFAULT_LIST_PAGE_SIZE = 8;
const MAX_LIST_PAGE_SIZE = 99;

function parseRecordJson(value?: string) {
  if (!value) {
    return {};
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function readPositiveInteger(value: unknown) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return undefined;
  }
  return Math.min(MAX_LIST_PAGE_SIZE, Math.floor(numericValue));
}

export function formatWorkbenchCardTime(value?: unknown) {
  if (!value || typeof value !== 'string') {
    return '';
  }
  return value.replace('T', ' ').slice(0, 16);
}

export function getWorkbenchItemConfig(item: WorkbenchCardItem) {
  const config = item.config;
  if (config && typeof config === 'object' && !Array.isArray(config)) {
    return config as Record<string, unknown>;
  }
  return parseRecordJson(
    typeof item.configJson === 'string' ? item.configJson : undefined,
  );
}

export function readWorkbenchCardLimit(card: WorkbenchLayoutCard) {
  const config = card.config;
  const rawLimit =
    config && typeof config === 'object' && !Array.isArray(config)
      ? config.limit
      : undefined;
  return (
    readPositiveInteger(rawLimit) ??
    readPositiveInteger(parseRecordJson(card.configJson).limit)
  );
}

export function getWorkbenchListPageSize(card: WorkbenchLayoutCard) {
  const limit = readWorkbenchCardLimit(card);
  if (limit) {
    return limit;
  }
  return Math.max(1, Math.min(DEFAULT_LIST_PAGE_SIZE, (card.gridH ?? 3) + 1));
}
