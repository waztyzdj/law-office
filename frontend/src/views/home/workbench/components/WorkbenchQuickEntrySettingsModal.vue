<script setup lang="ts">
import type { CSSProperties } from 'vue';
import type { RouteRecordRaw } from 'vue-router';
import type {
  WorkbenchQuickEntryInfo,
  WorkbenchQuickEntryType,
} from '#/api/home/workbench';

import { computed, reactive, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import {
  Button,
  Input,
  message,
  Modal,
  Radio,
  TreeSelect,
} from 'ant-design-vue';

import {
  listWorkbenchQuickEntries,
  saveCurrentWorkbenchQuickEntry,
} from '#/api/home/workbench';
import MenuIconPicker from '#/views/system/menu/components/MenuIconPicker.vue';

interface QuickEntryFormState {
  color: string;
  entryName: string;
  entryType: Extract<WorkbenchQuickEntryType, 'link' | 'menu'>;
  icon: string;
  id?: string;
  menuId: string;
  path: string;
  sortNo: number;
}

type RouteMetaWithMenu = NonNullable<RouteRecordRaw['meta']> & {
  id?: string;
  perms?: string;
};

interface AccessRouteItem extends Omit<RouteRecordRaw, 'children' | 'meta'> {
  children?: AccessRouteItem[];
  meta?: RouteMetaWithMenu;
}

interface AccessMenuItem {
  children?: AccessMenuItem[];
  icon?: unknown;
  meta?: {
    hideInMenu?: boolean;
    id?: string;
    icon?: string;
    perms?: string;
    title?: unknown;
  };
  name?: string;
  path?: string;
  show?: boolean;
}

interface MenuTreeNode {
  children?: MenuTreeNode[];
  disabled?: boolean;
  icon?: string;
  id?: string;
  label: string;
  path?: string;
  permissionCode?: string;
  title: string;
  value: string;
}

interface BuiltinMenuOption {
  icon: string;
  id: string;
  path: string;
  title: string;
}

const excludedInternalMenuPaths = new Set(['/home', '/home/workbench']);
const builtinCommonMenus: BuiltinMenuOption[] = [
  {
    icon: 'lucide:user',
    id: 'builtin:profile',
    path: '/profile',
    title: '个人中心',
  },
  {
    icon: 'lucide:message-square',
    id: 'builtin:message-center',
    path: '/message-center',
    title: '消息中心',
  },
  {
    icon: 'lucide:folder-open',
    id: 'builtin:document-center',
    path: '/document-center',
    title: '文档中心',
  },
];

const props = defineProps<{
  open: boolean;
  record?: WorkbenchQuickEntryInfo;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  success: [];
}>();

const accessStore = useAccessStore();
const saving = ref(false);
const entries = ref<WorkbenchQuickEntryInfo[]>([]);
const formState = reactive<QuickEntryFormState>({
  color: '#2563eb',
  entryName: '',
  entryType: 'menu',
  icon: 'lucide:blocks',
  menuId: '',
  path: '',
  sortNo: 100,
});

const colorOptions = [
  { label: '蓝色', value: '#2563eb' },
  { label: '青色', value: '#0891b2' },
  { label: '绿色', value: '#16a34a' },
  { label: '翠绿', value: '#059669' },
  { label: '黄绿', value: '#65a30d' },
  { label: '黄色', value: '#eab308' },
  { label: '橙色', value: '#f97316' },
  { label: '红色', value: '#dc2626' },
  { label: '玫红', value: '#e11d48' },
  { label: '粉色', value: '#db2777' },
  { label: '紫色', value: '#9333ea' },
  { label: '靛蓝', value: '#4f46e5' },
  { label: '深蓝', value: '#1d4ed8' },
  { label: '棕色', value: '#92400e' },
  { label: '灰色', value: '#64748b' },
  { label: '黑灰', value: '#334155' },
];

const entryTypeOptions = [
  { label: '内部菜单', value: 'menu' },
  { label: '外部链接', value: 'link' },
];

const selectedMenuIds = computed(() => {
  const currentEntryId = formState.id;
  return new Set(
    entries.value
      .filter(
        (entry) =>
          entry.entryType === 'menu' &&
          entry.menuId &&
          (!currentEntryId || entry.id !== currentEntryId),
      )
      .map((entry) => entry.menuId!),
  );
});
const menuTreeData = computed(() => {
  const routeTree = buildMenuTree(accessStore.accessRoutes as AccessRouteItem[]);
  const sourceTree = routeTree.length > 0
    ? routeTree
    : buildMenuTreeFromMenus(accessStore.accessMenus);
  return mergeBuiltinMenus(sourceTree);
});
const menuOptions = computed(() => flattenMenuTree(menuTreeData.value));
const menuOptionMap = computed(() => {
  const map = new Map<string, MenuTreeNode>();
  menuOptions.value.forEach((item) => {
    if (item.id) {
      map.set(item.id, item);
    }
  });
  return map;
});

watch(
  () => props.open,
  (open) => {
    if (open) {
      void prepareOpen();
    }
  },
);

function close() {
  emit('update:open', false);
}

async function prepareOpen() {
  await loadEntries();
  if (!props.open) {
    return;
  }
  if (props.record?.id) {
    fillForm(props.record);
    return;
  }
  resetForm();
}

function buildMenuTree(routes: AccessRouteItem[], parents: string[] = []) {
  return routes
    .filter(
      (route) =>
        !route.meta?.hideInMenu &&
        !isExcludedInternalMenuPath(typeof route.path === 'string' ? route.path : ''),
    )
    .map((route) => {
      const title = getRouteTitle(route);
      const children = Array.isArray(route.children)
        ? buildMenuTree(route.children, [...parents, title])
        : [];
      const path = typeof route.path === 'string' ? route.path : '';
      const id = route.meta?.id;
      const isLeaf = children.length === 0;
      const selectable = Boolean(isLeaf && id && path);
      const selected = Boolean(id && selectedMenuIds.value.has(id));
      const node: MenuTreeNode = {
        children: children.length > 0 ? children : undefined,
        disabled: !selectable || selected,
        icon: typeof route.meta?.icon === 'string' ? route.meta.icon : undefined,
        id,
        label: selected ? `${title}（已添加）` : title,
        path,
        permissionCode: route.meta?.perms,
        title,
        value: id || path || [...parents, title].join('/'),
      };
      return node;
    })
    .filter((node) => !node.disabled || Boolean(node.children?.length));
}

function buildMenuTreeFromMenus(menus: AccessMenuItem[], parents: string[] = []) {
  return menus
    .filter(
      (menu) =>
        menu.show !== false &&
        !menu.meta?.hideInMenu &&
        !isExcludedInternalMenuPath(typeof menu.path === 'string' ? menu.path : ''),
    )
    .map((menu) => {
      const title = getMenuTitle(menu);
      const children = Array.isArray(menu.children)
        ? buildMenuTreeFromMenus(menu.children, [...parents, title])
        : [];
      const path = typeof menu.path === 'string' ? menu.path : '';
      const id = menu.meta?.id;
      const isLeaf = children.length === 0;
      const selectable = Boolean(isLeaf && id && path);
      const selected = Boolean(id && selectedMenuIds.value.has(id));
      const node: MenuTreeNode = {
        children: children.length > 0 ? children : undefined,
        disabled: !selectable || selected,
        icon: menu.meta?.icon || (typeof menu.icon === 'string' ? menu.icon : undefined),
        id,
        label: selected ? `${title}（已添加）` : title,
        path,
        permissionCode: menu.meta?.perms,
        title,
        value: id || path || [...parents, title].join('/'),
      };
      return node;
    })
    .filter((node) => !node.disabled || Boolean(node.children?.length));
}

function isExcludedInternalMenuPath(path: string) {
  const normalizedPath = (path.split('?')[0] || path).replace(/\/+$/, '') || '/';
  return excludedInternalMenuPaths.has(normalizedPath);
}

function mergeBuiltinMenus(nodes: MenuTreeNode[]) {
  const existingPaths = collectMenuPaths(nodes);
  const children = builtinCommonMenus
    .filter((menu) => !existingPaths.has(menu.path))
    .map(toBuiltinMenuNode);
  if (children.length === 0) {
    return nodes;
  }
  return [
    {
      children,
      disabled: true,
      label: '常用入口',
      title: '常用入口',
      value: 'builtin:common',
    },
    ...nodes,
  ];
}

function collectMenuPaths(nodes: MenuTreeNode[], result = new Set<string>()) {
  nodes.forEach((node) => {
    if (node.path) {
      result.add(node.path.split('?')[0] || node.path);
    }
    if (node.children?.length) {
      collectMenuPaths(node.children, result);
    }
  });
  return result;
}

function toBuiltinMenuNode(menu: BuiltinMenuOption): MenuTreeNode {
  const selected = selectedMenuIds.value.has(menu.id);
  return {
    disabled: selected,
    icon: menu.icon,
    id: menu.id,
    label: selected ? `${menu.title}（已添加）` : menu.title,
    path: menu.path,
    title: menu.title,
    value: menu.id,
  };
}

function getRouteTitle(route: AccessRouteItem) {
  const title = normalizeTitle(route.meta?.title);
  if (title && !isPathLikeTitle(title)) {
    return title;
  }
  const name = typeof route.name === 'string' ? route.name : '';
  return name || title || route.path || '未命名菜单';
}

function getMenuTitle(menu: AccessMenuItem) {
  const title = normalizeTitle(menu.meta?.title);
  if (title && !isPathLikeTitle(title)) {
    return title;
  }
  const name = typeof menu.name === 'string' ? menu.name : '';
  return name || title || menu.path || '未命名菜单';
}

function isPathLikeTitle(title: string) {
  return title.startsWith('/');
}

function flattenMenuTree(nodes: MenuTreeNode[]) {
  const result: MenuTreeNode[] = [];
  nodes.forEach((node) => {
    if (!node.disabled) {
      result.push(node);
    }
    if (node.children?.length) {
      result.push(...flattenMenuTree(node.children));
    }
  });
  return result;
}

function normalizeTitle(title: unknown) {
  return typeof title === 'string' ? title : '';
}

function getConfig(entry: WorkbenchQuickEntryInfo): Record<string, unknown> {
  if (entry.config && typeof entry.config === 'object') {
    return entry.config;
  }
  if (!entry.configJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(entry.configJson) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function getEntryColor(entry: WorkbenchQuickEntryInfo) {
  const color = getConfig(entry).color;
  return typeof color === 'string' && color ? color : colorOptions[0]!.value;
}

function colorSwatchStyle(color: string): CSSProperties {
  return {
    '--quick-entry-color': color,
  } as CSSProperties;
}

function getDefaultSortNo() {
  const userSortNos = entries.value
    .filter((entry) => entry.ownerType === 'user')
    .map((entry) => entry.sortNo ?? 0);
  return userSortNos.length > 0 ? Math.max(...userSortNos) + 10 : 100;
}

function resetForm() {
  formState.id = undefined;
  formState.entryName = '';
  formState.entryType = 'menu';
  formState.menuId = '';
  formState.path = '';
  formState.icon = 'lucide:blocks';
  formState.color = colorOptions[0]!.value;
  formState.sortNo = getDefaultSortNo();
}

function fillForm(entry: WorkbenchQuickEntryInfo) {
  const entryType = entry.entryType === 'link' ? 'link' : 'menu';
  const menuOption = entryType === 'menu' ? findMenuOption(entry) : undefined;
  formState.id = entry.id;
  formState.entryName = entry.entryName || '';
  formState.entryType = entryType;
  formState.menuId = menuOption?.id || entry.menuId || '';
  formState.path = entry.path || '';
  formState.icon = entry.icon || 'lucide:blocks';
  formState.color = getEntryColor(entry);
  formState.sortNo = entry.sortNo ?? getDefaultSortNo();
}

async function loadEntries() {
  const result = await listWorkbenchQuickEntries({ includeSystem: true });
  entries.value = result.entries || [];
}

function handleCancel() {
  resetForm();
  close();
}

function handleTypeChange() {
  formState.menuId = '';
  formState.path = '';
  if (formState.entryType === 'menu') {
    formState.icon = 'lucide:blocks';
  }
}

function handleMenuChange(value: unknown) {
  const menuId = typeof value === 'string' ? value : '';
  const option = menuOptionMap.value.get(menuId);
  formState.menuId = menuId;
  formState.path = option?.path || '';
  if (option) {
    formState.entryName = option.title;
    formState.icon = option.icon || formState.icon || 'lucide:blocks';
  }
}

function findMenuOption(entry: WorkbenchQuickEntryInfo) {
  if (entry.menuId && menuOptionMap.value.has(entry.menuId)) {
    return menuOptionMap.value.get(entry.menuId);
  }
  return menuOptions.value.find((item) => item.path === entry.path);
}

function isValidExternalUrl(url: string) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

function validateForm() {
  const entryName = formState.entryName.trim();
  const path = formState.path.trim();
  if (!entryName) {
    message.warning('请输入快捷菜单名称');
    return;
  }
  if (formState.entryType === 'menu' && !formState.menuId) {
    message.warning('请选择内部菜单');
    return;
  }
  const menuOption =
    formState.entryType === 'menu'
      ? menuOptionMap.value.get(formState.menuId)
      : undefined;
  if (formState.entryType === 'menu' && !menuOption?.id) {
    message.warning('菜单数据不完整，请刷新页面后重新选择');
    return;
  }
  if (formState.entryType === 'link' && !isValidExternalUrl(path)) {
    message.warning('请输入以 http:// 或 https:// 开头的外部链接');
    return;
  }
  return {
    entryName,
    menuOption,
    path,
  };
}

async function handleSave() {
  const validValues = validateForm();
  if (!validValues) {
    return;
  }

  saving.value = true;
  try {
    await saveCurrentWorkbenchQuickEntry({
      config: { color: formState.color },
      entryName: validValues.entryName,
      entryType: formState.entryType,
      icon: formState.icon,
      id: formState.id,
      menuId:
        formState.entryType === 'menu' ? validValues.menuOption?.id : undefined,
      path:
        formState.entryType === 'menu'
          ? validValues.menuOption?.path
          : validValues.path,
      permissionCode:
        formState.entryType === 'menu'
          ? validValues.menuOption?.permissionCode
          : undefined,
      sortNo: formState.sortNo,
      status: 'enabled',
    });
    message.success(formState.id ? '快捷菜单已更新' : '快捷菜单已添加');
    resetForm();
    close();
    emit('success');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal
    :footer="null"
    :open="open"
    :title="formState.id ? '编辑快捷菜单' : '添加快捷菜单'"
    width="860px"
    @cancel="close"
  >
    <div class="quick-entry-settings">
      <section class="quick-entry-settings__edit-view">
        <div class="quick-entry-settings__form">
          <div class="quick-entry-settings__field">
            <span class="quick-entry-settings__label">快捷菜单类型</span>
            <Radio.Group
              v-model:value="formState.entryType"
              button-style="solid"
              option-type="button"
              :options="entryTypeOptions"
              @change="handleTypeChange"
            />
          </div>

          <div class="quick-entry-settings__field">
            <span class="quick-entry-settings__label">
              {{ formState.entryType === 'menu' ? '选择内部菜单' : '外部链接地址' }}
            </span>
            <TreeSelect
              v-if="formState.entryType === 'menu'"
              v-model:value="formState.menuId"
              allow-clear
              class="quick-entry-settings__target"
              :field-names="{ children: 'children', label: 'label', value: 'value' }"
              placeholder="请选择菜单名称"
              show-search
              tree-default-expand-all
              tree-node-filter-prop="label"
              :tree-data="menuTreeData"
              @change="handleMenuChange"
            />
            <Input
              v-else
              v-model:value="formState.path"
              class="quick-entry-settings__target"
              :maxlength="512"
              placeholder="请输入外部链接，例如：https://example.com"
            />
          </div>

          <div class="quick-entry-settings__row">
            <div class="quick-entry-settings__field quick-entry-settings__field--name">
              <span class="quick-entry-settings__label">快捷菜单名称</span>
              <Input
                v-model:value="formState.entryName"
                :maxlength="100"
                placeholder="请输入快捷菜单名称"
              />
            </div>
            <div class="quick-entry-settings__field quick-entry-settings__field--icon">
              <span class="quick-entry-settings__label">快捷菜单图标</span>
              <MenuIconPicker
                v-model:value="formState.icon"
                placeholder="请选择快捷菜单图标"
              />
            </div>
          </div>

          <div class="quick-entry-settings__field">
            <span class="quick-entry-settings__label">色块颜色</span>
            <div class="quick-entry-settings__colors">
              <button
                v-for="color in colorOptions"
                :key="color.value"
                :aria-label="`选择${color.label}`"
                :class="[
                  'quick-entry-settings__color',
                  { 'quick-entry-settings__color--active': formState.color === color.value },
                ]"
                :style="colorSwatchStyle(color.value)"
                type="button"
                @click="formState.color = color.value"
              >
                <IconifyIcon v-if="formState.color === color.value" icon="lucide:check" />
              </button>
            </div>
          </div>

          <div class="quick-entry-settings__footer">
            <Button @click="handleCancel">取消</Button>
            <Button :loading="saving" type="primary" @click="handleSave">
              {{ formState.id ? '保存修改' : '添加快捷菜单' }}
            </Button>
          </div>
        </div>
      </section>
    </div>
  </Modal>
</template>

<style scoped>
.quick-entry-settings {
  min-height: 320px;
}

.quick-entry-settings__edit-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quick-entry-settings__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.quick-entry-settings__row {
  display: flex;
  gap: 10px;
}

.quick-entry-settings__field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.quick-entry-settings__field--name {
  width: 220px;
}

.quick-entry-settings__field--icon {
  flex: 1;
}

.quick-entry-settings__label {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

.quick-entry-settings__target {
  width: 100%;
}

.quick-entry-settings__colors,
.quick-entry-settings__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-entry-settings__footer {
  justify-content: flex-end;
}

.quick-entry-settings__color {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid color-mix(in srgb, var(--quick-entry-color) 72%, black);
  border-radius: 999px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--quick-entry-color) 88%, white),
    var(--quick-entry-color)
  );
  box-shadow:
    inset 0 1px 2px rgb(255 255 255 / 28%),
    0 5px 12px color-mix(in srgb, var(--quick-entry-color) 20%, transparent);
  color: white;
  cursor: pointer;
  opacity: 1;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    opacity 0.16s ease,
    transform 0.16s ease;
}

.quick-entry-settings__color:hover,
.quick-entry-settings__color:focus-visible {
  transform: translateY(-1px);
}

.quick-entry-settings__color--active {
  border-color: hsl(var(--foreground));
  box-shadow:
    0 0 0 2px rgb(255 255 255 / 92%),
    0 0 0 4px color-mix(in srgb, var(--quick-entry-color) 72%, transparent),
    0 8px 18px color-mix(in srgb, var(--quick-entry-color) 24%, transparent);
  opacity: 1;
}

@media (max-width: 768px) {
  .quick-entry-settings__row {
    align-items: stretch;
    flex-direction: column;
  }

  .quick-entry-settings__field--name {
    width: 100%;
  }
}
</style>
