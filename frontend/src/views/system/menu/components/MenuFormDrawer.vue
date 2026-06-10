<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { PermissionInfo as MenuInfo } from '#/api/system/permission';

import { computed, markRaw, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  cleanFormPayload,
  noAutofillInputProps,
  optionalString,
  useVbenForm,
  z,
} from '#/adapter/form';
import {
  getPermissionById as getMenuById,
  savePermission as saveMenu,
} from '#/api/system/permission';
import {
  filterTreeSelectOptions,
  type StringTreeSelectOption,
} from '#/composables/Tree/useTree';
import { menuIconOptions } from '#/constants/menu-icons';
import { menuTypeOptions, menuTypeValues } from '#/constants/menu-types';
import MenuIconPicker from './MenuIconPicker.vue';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  parentId?: string;
  record?: MenuInfo;
}

const props = defineProps<{
  treeOptions: StringTreeSelectOption[];
}>();

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增菜单权限' : '编辑菜单权限'));
const availableTreeOptions = computed(() =>
  filterTreeSelectOptions(props.treeOptions, currentId.value),
);

const emptyMenuValues = {
  parentId: undefined,
  name: '',
  url: '',
  component: '',
  componentName: '',
  icon: '',
  sortNo: 0,
  menuType: menuTypeValues.subMenu,
  perms: '',
  hidden: 0,
  hideTab: 0,
  keepAlive: false,
  redirect: '',
  status: '1',
};

type MenuIcon = (typeof menuIconOptions)[number];
const supportedMenuIcons = menuIconOptions as readonly string[];

const legacyIconMap: Record<string, MenuIcon> = {
  category: 'lucide:tags',
  database: 'lucide:database',
  depart: 'lucide:building-2',
  department: 'lucide:building-2',
  dict: 'lucide:book-open-text',
  log: 'lucide:scroll-text',
  menu: 'lucide:menu',
  role: 'lucide:shield-check',
  setting: 'lucide:settings',
  settings: 'lucide:settings',
  shield: 'lucide:shield-check',
  tenant: 'lucide:landmark',
  user: 'lucide:user',
  users: 'lucide:users',
};

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const isFirstLevelMenu = (menuType?: number) =>
  menuType === menuTypeValues.firstLevelMenu;
const isSubMenu = (menuType?: number) => menuType === menuTypeValues.subMenu;
const isButtonPermission = (menuType?: number) =>
  menuType === menuTypeValues.buttonPermission;
const isMenu = (menuType?: number) =>
  isFirstLevelMenu(menuType) || isSubMenu(menuType);

const menuRequiredRule = (message: string, maxMessage: string, max = 200) =>
  z.string({ required_error: message }).min(1, message).max(max, maxMessage);

const noopRule = z.any().optional();
const isMenuIcon = (value: string): value is MenuIcon =>
  supportedMenuIcons.includes(value);

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'menuType',
    component: 'RadioGroup',
    label: '类型',
    defaultValue: getDefaultValue('menuType', menuTypeValues.subMenu),
    rules: 'selectRequired',
    componentProps: {
      buttonStyle: 'solid',
      optionType: 'button',
      options: menuTypeOptions.map(({ label, value }) => ({ label, value })),
    },
  },
  {
    fieldName: 'parentId',
    component: 'TreeSelect',
    label: '父级',
    defaultValue: getDefaultValue('parentId', undefined),
    dependencies: {
      required: (values) => !isFirstLevelMenu(values.menuType),
      rules: (values) =>
        !isFirstLevelMenu(values.menuType)
          ? z
              .string({ required_error: '请选择父级菜单' })
              .min(1, '请选择父级菜单')
          : noopRule,
      show: (values) => !isFirstLevelMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: {
      allowClear: true,
      treeData: availableTreeOptions.value,
      treeDefaultExpandAll: true,
      placeholder: '请选择父级菜单',
    },
  },
  {
    fieldName: 'name',
    component: 'Input',
    label: '名称',
    defaultValue: getDefaultValue('name', ''),
    rules: z
      .string({ required_error: '请输入名称' })
      .min(1, '请输入名称')
      .max(100, '名称不能超过100个字符'),
    componentProps: noAutofillInputProps('menuName', {
      maxlength: 100,
      placeholder: '请输入名称',
    }),
  },
  {
    fieldName: 'url',
    component: 'Input',
    label: '路径',
    defaultValue: getDefaultValue('url', ''),
    dependencies: {
      rules: (values) =>
        isMenu(values.menuType)
          ? menuRequiredRule('请输入路径', '路径不能超过200个字符')
          : noopRule,
      show: (values) => isMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: noAutofillInputProps('menuUrl', {
      maxlength: 200,
      placeholder: '例如 /system/role',
    }),
  },
  {
    fieldName: 'component',
    component: 'Input',
    label: '组件',
    defaultValue: getDefaultValue('component', ''),
    dependencies: {
      rules: (values) =>
        isMenu(values.menuType)
          ? menuRequiredRule('请输入组件路径', '组件路径不能超过200个字符')
          : noopRule,
      show: (values) => isMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: noAutofillInputProps('menuComponent', {
      maxlength: 200,
      placeholder: '例如 /views/system/role/index.vue',
    }),
  },
  {
    fieldName: 'componentName',
    component: 'Input',
    label: '组件名',
    defaultValue: getDefaultValue('componentName', ''),
    rules: optionalString(z.string().max(100, '组件名不能超过100个字符')),
    dependencies: {
      show: (values) => isMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: noAutofillInputProps('menuComponentName', {
      maxlength: 100,
      placeholder: '例如 SystemRole',
    }),
  },
  {
    fieldName: 'perms',
    component: 'Input',
    label: '权限码',
    defaultValue: getDefaultValue('perms', ''),
    dependencies: {
      rules: (values) =>
        isButtonPermission(values.menuType)
          ? z
              .string({ required_error: '请输入权限码' })
              .min(1, '请输入权限码')
              .max(100, '权限码不能超过100个字符')
              .regex(
                /^[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z][A-Za-z0-9_-]*){1,2}$/,
                '权限码格式为 module:action 或 domain:module:action',
              )
          : noopRule,
      show: (values) => isButtonPermission(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: noAutofillInputProps('menuPerms', {
      maxlength: 100,
      placeholder: '例如 role:edit',
    }),
  },
  {
    fieldName: 'icon',
    component: markRaw(MenuIconPicker),
    label: '图标',
    modelPropName: 'value',
    defaultValue: getDefaultValue('icon', ''),
    dependencies: {
      show: (values) => isMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    rules: optionalString(
      z
        .string()
        .max(100, '图标不能超过100个字符')
        .refine(isMenuIcon, '请选择系统支持的图标'),
    ),
    componentProps: {
      icons: menuIconOptions,
      pageSize: 60,
      placeholder: '请选择菜单图标',
    },
  },
  {
    fieldName: 'sortNo',
    component: 'InputNumber',
    label: '排序',
    defaultValue: getDefaultValue('sortNo', 0),
    componentProps: {
      min: 0,
      precision: 0,
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'status',
    component: 'Select',
    label: '状态',
    defaultValue: getDefaultValue('status', '1'),
    rules: 'selectRequired',
    componentProps: {
      options: [
        { label: '正常', value: '1' },
        { label: '停用', value: '0' },
      ],
    },
  },
  {
    fieldName: 'hidden',
    component: 'Select',
    label: '菜单显示',
    defaultValue: getDefaultValue('hidden', 0),
    rules: 'selectRequired',
    dependencies: {
      show: (values) => isMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: {
      options: [
        { label: '显示', value: 0 },
        { label: '隐藏', value: 1 },
      ],
    },
  },
  {
    fieldName: 'keepAlive',
    component: 'Switch',
    label: '页面缓存',
    defaultValue: getDefaultValue('keepAlive', false),
    dependencies: {
      show: (values) => isSubMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: {
      class: 'menu-keep-alive-switch',
      style: { width: 'auto' },
    },
  },
  {
    fieldName: 'redirect',
    component: 'Input',
    label: '重定向',
    defaultValue: getDefaultValue('redirect', ''),
    rules: optionalString(z.string().max(200, '重定向不能超过200个字符')),
    dependencies: {
      show: (values) => isFirstLevelMenu(values.menuType),
      triggerFields: ['menuType'],
    },
    componentProps: noAutofillInputProps('menuRedirect', {
      maxlength: 200,
      placeholder: '请输入重定向路径',
    }),
  },
];

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 92,
  },
  layout: 'horizontal',
  schema: buildFormSchema(),
  scrollToFirstError: true,
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-1/2! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return {
      ...emptyMenuValues,
      parentId: payload.parentId,
    };
  }
  return {
    ...emptyMenuValues,
    ...payload.record,
    icon: normalizeIcon(payload.record?.icon),
    status: String(payload.record?.status ?? '1'),
  };
}

function normalizeIcon(icon?: string) {
  if (!icon) {
    return '';
  }
  const normalizedIcon = icon.trim();
  if (isMenuIcon(normalizedIcon)) {
    return normalizedIcon;
  }
  return legacyIconMap[normalizedIcon] || '';
}

function prepareFormState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentId.value = payload.record?.id;
  initialValues.value = buildInitialValues(payload);
  hasSyncedMountedValues.value = false;
  formApi.setState({ schema: buildFormSchema() });
  drawerApi.setState({ loading: false, title: drawerTitle.value });
}

async function syncMountedFormValues() {
  if (!formApi.isMounted || hasSyncedMountedValues.value) {
    return;
  }

  await formApi.resetForm();
  await formApi.setValues(initialValues.value);
  hasSyncedMountedValues.value = true;
}

async function refreshDetailSilently() {
  if (!currentId.value) {
    return;
  }
  try {
    const detail = await getMenuById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层会统一提示错误，这里保留已有行数据。
  }
}

function cleanPayload(values: Record<string, any>): MenuInfo {
  const payload = cleanFormPayload<MenuInfo>(values, {
    id: currentId.value,
  });

  if (isFirstLevelMenu(payload.menuType)) {
    delete payload.parentId;
    delete payload.perms;
    delete payload.keepAlive;
    payload.hidden ??= 0;
    payload.hideTab = 0;
  } else if (isSubMenu(payload.menuType)) {
    delete payload.perms;
    delete payload.redirect;
    payload.hidden ??= 0;
    payload.hideTab = 0;
  } else if (isButtonPermission(payload.menuType)) {
    delete payload.url;
    delete payload.component;
    delete payload.componentName;
    delete payload.icon;
    delete payload.hidden;
    delete payload.hideTab;
    delete payload.keepAlive;
    delete payload.redirect;
  }

  return payload;
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  try {
    drawerApi.lock();
    const values = await formApi.getValues();
    await saveMenu(cleanPayload(values));
    message.success(isCreate.value ? '新增菜单权限成功' : '修改菜单权限成功');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(payload: DrawerPayload) {
  prepareFormState(payload);

  if (formApi.isMounted) {
    await syncMountedFormValues();
  }

  drawerApi.setData(payload).open();

  await nextTick();
  if (formApi.isMounted) {
    void syncMountedFormValues();
  }
  void refreshDetailSilently();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Form />
  </Drawer>
</template>

<style scoped>
:deep(.menu-keep-alive-switch) {
  width: auto !important;
  min-width: 44px;
}
</style>
