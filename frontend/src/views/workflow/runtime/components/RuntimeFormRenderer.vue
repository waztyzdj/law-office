<script setup lang="ts">
import type { Api, FormRule, Options } from '@form-create/ant-design-vue';

import type { RuntimeFieldPermissionInfo } from '#/api/workflow';

import { computed, ref, shallowRef, watch } from 'vue';

import formCreate from '@form-create/ant-design-vue';

import { Alert, Empty, Spin } from 'ant-design-vue';

type FieldPermission = 'editable' | 'hidden' | 'readonly';

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
    fieldPermissions?: RuntimeFieldPermissionInfo[];
    formDataJson?: string;
    loading?: boolean;
    optionJson?: string;
    readonly?: boolean;
    schemaJson?: string;
  }>(),
  {
    disabled: false,
    fieldPermissions: () => [],
    formDataJson: '{}',
    loading: false,
    optionJson: '{}',
    readonly: false,
    schemaJson: '[]',
  },
);

const FormCreate = formCreate.$form();
const formApi = ref<Api>();
const formData = ref<Record<string, unknown>>({});
const cachedRuleKey = ref('');
const cachedRules = shallowRef<FormRule[]>([]);

const parseError = ref('');

function parseJson<T>(json: string | undefined, fallback: T): T {
  if (!json) {
    return fallback;
  }

  try {
    return JSON.parse(json) as T;
  } catch {
    parseError.value = '表单配置解析失败，请检查表单设计内容';
    return fallback;
  }
}

function cloneRules(rules: FormRule[]): FormRule[] {
  return JSON.parse(JSON.stringify(rules)) as FormRule[];
}

const permissionMap = computed(() => {
  const map = new Map<string, RuntimeFieldPermissionInfo>();
  for (const item of props.fieldPermissions) {
    if (item.fieldKey) {
      map.set(item.fieldKey, item);
    }
  }
  return map;
});

function normalizePermission(
  item: RuntimeFieldPermissionInfo | undefined,
): FieldPermission {
  if (props.readonly) {
    return 'readonly';
  }
  if (!item?.permission) {
    return 'editable';
  }
  if (item.permission === 'hidden' || item.permission === 'readonly') {
    return item.permission;
  }
  return 'editable';
}

function isEnabledFlag(value: unknown) {
  return value === true || value === 1 || value === '1' || value === 'true';
}

function hasRequiredValidate(rule: FormRule) {
  const currentValidate = Array.isArray(rule.validate) ? rule.validate : [];
  return currentValidate.some((item: object) => {
    const validateRule = item as Record<string, unknown>;
    return validateRule.required === true;
  });
}

function resolveDesignerRequired(rule: FormRule) {
  const formRule = rule as FormRule & Record<string, unknown>;
  const effect = (rule.effect ?? {}) as Record<string, unknown>;
  const props = (rule.props ?? {}) as Record<string, unknown>;
  return (
    hasRequiredValidate(rule) ||
    isEnabledFlag(formRule.$required) ||
    isEnabledFlag(effect.required) ||
    isEnabledFlag(props.required)
  );
}

function ensureRequiredValidate(rule: FormRule, required: boolean) {
  const formRule = rule as FormRule & Record<string, unknown>;
  const currentValidate = Array.isArray(rule.validate) ? rule.validate : [];
  const withoutRequired = currentValidate.filter(
    (item: object) => !('required' in item) || item.required !== true,
  );
  formRule.$required = false;

  if (!required) {
    rule.validate = withoutRequired;
    rule.effect = {
      ...(rule.effect ?? {}),
      required: false,
    };
    return;
  }

  rule.validate = [
    ...withoutRequired,
    {
      message: `${rule.title ?? rule.field ?? '字段'}不能为空`,
      required: true,
      trigger: 'change',
    },
  ];
  rule.effect = {
    ...(rule.effect ?? {}),
    required: false,
  };
}

function applyRulePermission(rule: FormRule) {
  if (!rule || typeof rule !== 'object') {
    return;
  }

  if (rule.field) {
    const field = String(rule.field);
    const item = permissionMap.value.get(field);
    const hasExplicitPermission = Boolean(item);
    const permission = normalizePermission(item);
    const required = hasExplicitPermission
      ? Number(item?.requiredFlag ?? 0) === 1
      : resolveDesignerRequired(rule);

    rule.hidden = permission === 'hidden';
    rule.display = permission !== 'hidden';
    rule.props = {
      ...(rule.props ?? {}),
      disabled: props.disabled || permission === 'readonly',
      required: false,
    };
    ensureRequiredValidate(rule, permission === 'editable' && required);
  }

  if (Array.isArray(rule.children)) {
    for (const child of rule.children) {
      if (typeof child === 'object') {
        applyRulePermission(child as FormRule);
      }
    }
  }
}

const rules = computed<FormRule[]>(() => {
  const permissionKey = JSON.stringify(props.fieldPermissions ?? []);
  const cacheKey = [
    props.schemaJson,
    permissionKey,
    props.readonly ? 'readonly' : 'editable',
    props.disabled ? 'disabled' : 'enabled',
  ].join('|');
  if (cacheKey === cachedRuleKey.value) {
    return cachedRules.value;
  }
  parseError.value = '';
  const parsed = parseJson<FormRule[]>(props.schemaJson, []);
  const cloned = cloneRules(Array.isArray(parsed) ? parsed : []);
  for (const rule of cloned) {
    applyRulePermission(rule);
  }
  cachedRuleKey.value = cacheKey;
  cachedRules.value = cloned;
  return cloned;
});

const options = computed<Options>(() => {
  const parsed = parseJson<Options>(props.optionJson, {});
  return {
    ...parsed,
    resetBtn: false,
    submitBtn: false,
  };
});

watch(
  () => props.formDataJson,
  () => {
    formData.value = parseJson<Record<string, unknown>>(props.formDataJson, {});
  },
  { immediate: true },
);

async function validate() {
  await formApi.value?.validate();
}

function getFormData() {
  return formApi.value?.formData() ?? formData.value ?? {};
}

async function getValidatedFormData() {
  await validate();
  return getFormData();
}

defineExpose({
  getFormData,
  getValidatedFormData,
  validate,
});
</script>

<template>
  <Spin :spinning="loading">
    <Alert
      v-if="parseError"
      banner
      show-icon
      type="error"
      :message="parseError"
    />
    <Empty
      v-else-if="rules.length === 0"
      description="暂无表单内容"
    />
    <FormCreate
      v-else
      v-model="formData"
      v-model:api="formApi"
      :disabled="disabled"
      :option="options"
      :rule="rules"
    />
  </Spin>
</template>
