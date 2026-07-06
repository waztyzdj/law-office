export const FALLBACK_HOME_PATH = '/home/workbench';
export const LEGACY_TEMPLATE_HOME_PATHS = new Set(['/analytics']);

export function normalizeRoutePathname(path?: string) {
  return (path?.split('?')[0] || path || '').replace(/\/+$/, '') || '/';
}

export function isLegacyTemplateHomePath(path?: string) {
  return LEGACY_TEMPLATE_HOME_PATHS.has(normalizeRoutePathname(path));
}
